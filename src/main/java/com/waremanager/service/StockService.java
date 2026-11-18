package com.waremanager.service;

import com.waremanager.model.*;
import com.waremanager.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

/** Physical stock and availability shared by recall, cold-chain, reservation and scarcity workflows. */
@Service
public class StockService {
    private final ProductRepository products;
    private final BatchRepository batches;
    private final ReservationRepository reservations;

    public StockService(ProductRepository products, BatchRepository batches, ReservationRepository reservations) {
        this.products = products;
        this.batches = batches;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public List<Batch> batches() { return batches.findAllByOrderByExpiryDateAsc(); }

    @Transactional(readOnly = true)
    public Batch batch(Long id) { return batches.findById(id).orElseThrow(() -> new IllegalArgumentException("Batch not found")); }

    /** All inventory includes unbatched stock inherited from the original WareManager. */
    @Transactional(readOnly = true)
    public int available(Long productId) {
        Product p = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        int held = batches.findByProductIdOrderByExpiryDateAsc(productId).stream()
            .filter(Batch::isHeld).mapToInt(Batch::getQuantity).sum();
        long reserved = reservations.unitsForProduct(productId, Reservation.Status.ACTIVE);
        return (int) Math.max(0, (long) p.getQuantity() - held - reserved);
    }

    /** Checks actual pickable stock after expiry, hold, existing reservations and shelf-life rules. */
    @Transactional(readOnly = true)
    public int allocatable(Long productId, int minShelfLifeDays) {
        List<Batch> lots = batches.findByProductIdOrderByExpiryDateAsc(productId);
        int tracked = lots.stream().mapToInt(Batch::getQuantity).sum();
        Product p = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        long loose = Math.max(0, (long) p.getQuantity() - tracked
            - reservations.unitsWithoutBatch(productId, Reservation.Status.ACTIVE));
        if (minShelfLifeDays > 0) loose = 0; // No expiry record: cannot prove the shelf-life requirement.
        LocalDate latestAcceptable = LocalDate.now().plusDays(Math.max(0, minShelfLifeDays));
        long eligible = lots.stream().filter(b -> !b.isHeld() && !b.getExpiryDate().isBefore(latestAcceptable))
            .mapToLong(b -> Math.max(0, b.getQuantity() - reservations.unitsForBatch(b.getId(), Reservation.Status.ACTIVE)))
            .sum();
        return (int) Math.min(Integer.MAX_VALUE, Math.min(available(productId), loose + eligible));
    }

    /** Receiving a new tracked batch increases the original product's physical quantity too. */
    @Transactional
    public Batch receiveBatch(Long productId, String number, String bin, LocalDate expiry, int quantity) {
        if (number == null || number.isBlank() || bin == null || bin.isBlank()
                || number.length() > 80 || bin.length() > 80 || quantity <= 0
                || expiry == null || expiry.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Enter batch number, bin, current/future expiry and positive quantity");
        }
        if (batches.existsByBatchNumberIgnoreCase(number.trim())) {
            throw new IllegalArgumentException("Batch number already exists");
        }
        Product p = lock(productId);
        if (p.getQuantity() > Integer.MAX_VALUE - quantity) throw new IllegalArgumentException("Stock overflow");
        p.setQuantity(p.getQuantity() + quantity);
        return batches.save(new Batch(p, number.trim(), bin.trim(), expiry, quantity));
    }

    /** A hold blocks affected active reservations immediately, but retains them as evidence. */
    @Transactional
    public void holdBatch(Batch batch, String reason) {
        lock(batch.getProduct().getId());
        batch.setHeld(true);
        batch.setHoldReason(reason);
        for (Reservation r : reservations.findByBatchIdOrderByIdAsc(batch.getId())) {
            if (r.getStatus() != Reservation.Status.ACTIVE) continue;
            r.setStatus(Reservation.Status.BLOCKED);
            SalesOrderLine line = r.getOrderLine();
            line.setReservedQuantity(Math.max(0, line.getReservedQuantity() - r.getQuantity()));
            line.setBackorderedQuantity(line.getQuantity() - line.getReservedQuantity());
            if (line.getSalesOrder().getStatus() == SalesOrder.Status.CONFIRMED) {
                line.getSalesOrder().setStatus(SalesOrder.Status.PARTIAL);
            }
        }
    }

    @Transactional
    public void discard(Batch batch) {
        Product p = lock(batch.getProduct().getId());
        p.setQuantity(Math.max(0, p.getQuantity() - batch.getQuantity()));
        batch.setQuantity(0);
        batch.setHeld(true);
        batch.setHoldReason("Discarded after quality review");
    }

    public Product lock(Long id) {
        return products.lockById(id).orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    public List<Batch> lots(Long id) { return batches.findByProductIdOrderByExpiryDateAsc(id); }
    public long reservedIn(Batch batch) { return reservations.unitsForBatch(batch.getId(), Reservation.Status.ACTIVE); }
    public long unbatchedReserved(Long id) { return reservations.unitsWithoutBatch(id, Reservation.Status.ACTIVE); }
}

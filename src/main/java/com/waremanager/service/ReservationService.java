package com.waremanager.service;

import com.waremanager.model.*;
import com.waremanager.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

/** F17: atomic stock reservations; F18 reuses the same allocation engine. */
@Service
public class ReservationService {
    private final SalesOrderRepository orders;
    private final SalesOrderLineRepository lines;
    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final ShipmentRepository shipments;
    private final StockService stock;

    public ReservationService(SalesOrderRepository orders, SalesOrderLineRepository lines,
            ProductRepository products, ReservationRepository reservations,
            ShipmentRepository shipments, StockService stock) {
        this.orders = orders;
        this.lines = lines;
        this.products = products;
        this.reservations = reservations;
        this.shipments = shipments;
        this.stock = stock;
    }

    @Transactional(readOnly = true)
    public List<SalesOrder> orders() { return orders.findAllByOrderByCreatedAtAscIdAsc(); }
    @Transactional(readOnly = true)
    public List<SalesOrderLine> lines(Long orderId) { return lines.findBySalesOrderIdOrderByIdAsc(orderId); }
    @Transactional(readOnly = true)
    public List<SalesOrderLine> allLines() { return lines.findAllByOrderByIdAsc(); }
    @Transactional(readOnly = true)
    public List<Reservation> allocations(Long orderId) { return reservations.findByOrderLineSalesOrderIdOrderByIdAsc(orderId); }

    @Transactional
    public SalesOrder newOrder(String customer, int priority) {
        if (customer == null || customer.isBlank() || customer.length() > 120 || priority < 1 || priority > 3)
            throw new IllegalArgumentException("Enter customer name and priority from 1 to 3");
        return orders.save(new SalesOrder(customer.trim(), priority));
    }

    @Transactional
    public void addLine(Long orderId, Long productId, int qty, int minShelfLifeDays) {
        SalesOrder order = findOrder(orderId);
        if (order.getStatus() != SalesOrder.Status.DRAFT) throw new IllegalArgumentException("Only draft orders can be edited");
        if (qty <= 0 || minShelfLifeDays < 0 || minShelfLifeDays > 3650)
            throw new IllegalArgumentException("Enter positive quantity and valid shelf-life days");
        Product product = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        lines.save(new SalesOrderLine(order, product, qty, minShelfLifeDays));
    }

    /** All-or-nothing. A database lock prevents simultaneous orders claiming the same units. */
    @Transactional
    public void confirm(Long orderId) {
        SalesOrder order = findOrder(orderId);
        if (order.getStatus() != SalesOrder.Status.DRAFT && order.getStatus() != SalesOrder.Status.PARTIAL)
            throw new IllegalArgumentException("Only draft or partially allocated orders can be confirmed");
        List<SalesOrderLine> items = lines(orderId);
        if (items.isEmpty()) throw new IllegalArgumentException("Add at least one order line");
        items.stream().map(x -> x.getProduct().getId()).distinct().sorted().forEach(stock::lock);
        // Locks stay held through the transaction. Any failure rolls back all reservations.
        for (SalesOrderLine item : items) {
            int needed = item.getQuantity() - item.getReservedQuantity();
            if (stock.allocatable(item.getProduct().getId(), item.getMinShelfLifeDays()) < needed)
                throw new IllegalArgumentException("Insufficient eligible stock for " + item.getProduct().getName()
                    + "; use F18 scarcity allocation or choose a substitute");
            allocateExact(item, needed);
        }
        order.setStatus(SalesOrder.Status.CONFIRMED);
    }

    /** A partial allocation with the same locking and batch rules used by F17. */
    @Transactional
    public int reservePart(Long lineId, int desired) {
        SalesOrderLine line = findLine(lineId);
        SalesOrder.Status state = line.getSalesOrder().getStatus();
        if (state != SalesOrder.Status.DRAFT && state != SalesOrder.Status.PARTIAL)
            throw new IllegalArgumentException("Order is not open for allocation");
        if (desired < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        stock.lock(line.getProduct().getId());
        int needed = line.getQuantity() - line.getReservedQuantity();
        int qty = Math.min(Math.min(desired, needed), stock.allocatable(line.getProduct().getId(), line.getMinShelfLifeDays()));
        allocateExact(line, qty);
        line.setBackorderedQuantity(line.getQuantity() - line.getReservedQuantity());
        refreshStatus(line.getSalesOrder());
        return qty;
    }

    private void allocateExact(SalesOrderLine line, int quantity) {
        if (quantity == 0) return;
        Long productId = line.getProduct().getId();
        int remaining = quantity;
        int tracked = 0;
        LocalDate acceptable = LocalDate.now().plusDays(line.getMinShelfLifeDays());
        for (Batch batch : stock.lots(productId)) {
            tracked += batch.getQuantity();
            if (batch.isHeld() || batch.getExpiryDate().isBefore(acceptable)) continue;
            int free = (int) Math.max(0, batch.getQuantity() - stock.reservedIn(batch));
            int take = Math.min(free, remaining);
            if (take > 0) reservations.save(new Reservation(line, batch, take));
            remaining -= take;
            if (remaining == 0) break;
        }
        if (remaining > 0) {
            // Unbatched inventory from the team's original F1-F7 product records.
            if (line.getMinShelfLifeDays() > 0)
                throw new IllegalArgumentException("Unbatched stock cannot guarantee a minimum shelf life");
            Product p = stock.lock(productId);
            long looseFree = Math.max(0, (long) p.getQuantity() - tracked - stock.unbatchedReserved(productId));
            if (remaining > looseFree) throw new IllegalArgumentException("Not enough eligible stock to reserve");
            reservations.save(new Reservation(line, null, remaining));
        }
        line.setReservedQuantity(line.getReservedQuantity() + quantity);
        line.setBackorderedQuantity(line.getQuantity() - line.getReservedQuantity());
    }

    /** Physical dispatch records shipment history for F14 recall impact. */
    @Transactional
    public void ship(Long orderId) {
        SalesOrder order = findOrder(orderId);
        if (order.getStatus() != SalesOrder.Status.CONFIRMED)
            throw new IllegalArgumentException("Only fully confirmed orders can be shipped");
        List<SalesOrderLine> items = lines(orderId);
        items.stream().map(x -> x.getProduct().getId()).distinct().sorted().forEach(stock::lock);
        for (SalesOrderLine line : items) {
            List<Reservation> active = reservations.findByOrderLineIdAndStatus(line.getId(), Reservation.Status.ACTIVE);
            int count = active.stream().mapToInt(Reservation::getQuantity).sum();
            if (count != line.getQuantity()) throw new IllegalArgumentException("Reservation changed: cannot ship");
            for (Reservation reservation : active) {
                Batch batch = reservation.getBatch();
                if (batch != null && (batch.isHeld() || batch.getExpiryDate().isBefore(LocalDate.now())))
                    throw new IllegalArgumentException("Held or expired batch cannot be shipped");
            }
        }
        for (SalesOrderLine line : items) {
            Product p = stock.lock(line.getProduct().getId());
            for (Reservation reservation : reservations.findByOrderLineIdAndStatus(line.getId(), Reservation.Status.ACTIVE)) {
                Batch batch = reservation.getBatch();
                int qty = reservation.getQuantity();
                if (batch != null) batch.setQuantity(batch.getQuantity() - qty);
                p.setQuantity(p.getQuantity() - qty);
                reservation.setStatus(Reservation.Status.SHIPPED);
                shipments.save(new Shipment(line, batch, qty));
            }
            line.setReservedQuantity(0);
            line.setBackorderedQuantity(0);
        }
        order.setStatus(SalesOrder.Status.SHIPPED);
    }

    @Transactional
    public void cancel(Long orderId) {
        SalesOrder order = findOrder(orderId);
        if (order.getStatus() == SalesOrder.Status.SHIPPED || order.getStatus() == SalesOrder.Status.CANCELLED)
            throw new IllegalArgumentException("A shipped or cancelled order cannot be cancelled again");
        for (Reservation r : reservations.findByOrderLineSalesOrderIdOrderByIdAsc(orderId)) {
            if (r.getStatus() == Reservation.Status.ACTIVE || r.getStatus() == Reservation.Status.BLOCKED)
                r.setStatus(Reservation.Status.RELEASED);
        }
        for (SalesOrderLine line : lines(orderId)) {
            line.setReservedQuantity(0);
            line.setBackorderedQuantity(0);
        }
        order.setStatus(SalesOrder.Status.CANCELLED);
    }

    @Transactional(readOnly = true)
    public SalesOrder findOrder(Long id) { return orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Order not found")); }
    @Transactional(readOnly = true)
    public SalesOrderLine findLine(Long id) { return lines.findById(id).orElseThrow(() -> new IllegalArgumentException("Order line not found")); }

    private void refreshStatus(SalesOrder order) {
        List<SalesOrderLine> items = lines(order.getId());
        boolean complete = !items.isEmpty() && items.stream().allMatch(x -> x.getReservedQuantity() == x.getQuantity());
        order.setStatus(complete ? SalesOrder.Status.CONFIRMED : SalesOrder.Status.PARTIAL);
    }
}

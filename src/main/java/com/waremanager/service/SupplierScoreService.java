// F5
package com.waremanager.service;

import com.waremanager.model.PurchaseOrder;
import com.waremanager.model.Supplier;
import com.waremanager.repository.PurchaseOrderRepository;
import com.waremanager.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class SupplierScoreService {

    public record Row(Supplier supplier, SupplierScoring.Result result) {}

    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;

    public SupplierScoreService(PurchaseOrderRepository orders, SupplierRepository suppliers) {
        this.orders = orders;
        this.suppliers = suppliers;
    }

    /** All suppliers ranked by score, best first. */
    @Transactional(readOnly = true)
    public List<Row> ranking() {
        List<Row> rows = new ArrayList<>();
        for (Supplier s : suppliers.findAll()) {
            rows.add(new Row(s, SupplierScoring.score(toReceipts(receiptsOf(s.getId())))));
        }
        rows.sort(Comparator.comparingDouble((Row r) -> r.result().score()).reversed());
        return rows;
    }

    /** Average score of suppliers that have at least one receipt (dashboard card). */
    @Transactional(readOnly = true)
    public double averageScore() {
        return ranking().stream().filter(r -> r.result().receipts() > 0)
                .mapToDouble(r -> r.result().score()).average().orElse(0);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> receiptsOf(Long supplierId) {
        return orders.findByReceivedDateIsNotNull().stream()
                .filter(o -> o.getSupplier() != null && o.getSupplier().getId().equals(supplierId))
                .sorted(Comparator.comparing(PurchaseOrder::getReceivedDate).reversed())
                .toList();
    }

    public Supplier supplier(Long id) {
        return suppliers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown supplier"));
    }

    /** Records how many units arrived and how many were defective (validated). */
    @Transactional
    public void recordReceipt(Long orderId, int receivedQty, int defectiveQty) {
        PurchaseOrder o = orders.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order"));
        if (o.isOpen()) throw new IllegalArgumentException("The order has not been received yet");
        if (receivedQty < 0 || receivedQty > o.getQuantity())
            throw new IllegalArgumentException("Received quantity must be between 0 and " + o.getQuantity());
        if (defectiveQty < 0 || defectiveQty > receivedQty)
            throw new IllegalArgumentException("Defective quantity must be between 0 and the received quantity");
        o.setReceivedQuantity(receivedQty);
        o.setDefectiveQuantity(defectiveQty);
        orders.save(o);
    }

    private List<SupplierScoring.Receipt> toReceipts(List<PurchaseOrder> list) {
        return list.stream().map(o -> new SupplierScoring.Receipt(
                !o.getReceivedDate().isAfter(o.getExpectedDate()),
                o.getQuantity(),
                o.getReceivedQuantity() != null ? o.getReceivedQuantity() : o.getQuantity(),
                o.getDefectiveQuantity() != null ? o.getDefectiveQuantity() : 0)).toList();
    }
}
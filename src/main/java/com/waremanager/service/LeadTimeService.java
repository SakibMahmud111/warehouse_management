package com.waremanager.service;

import com.waremanager.model.Product;
import com.waremanager.model.PurchaseOrder;
import com.waremanager.model.ReorderSuggestion;
import com.waremanager.model.Supplier;
import com.waremanager.repository.ProductRepository;
import com.waremanager.repository.PurchaseOrderRepository;
import com.waremanager.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * F6 Supplier Lead-Time Prediction: learns from received purchase orders how many days each
 * product's supplier takes (see {@link LeadTimePredictor}), records orders and receipts, and
 * compares every receipt with what was predicted when the order was placed.
 * The prediction is used by F2 (safety stock), F4 (winter order-by date) and F7 (stockout risk).
 */
@Service
public class LeadTimeService {

    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final ModelTrainer models;

    public LeadTimeService(ProductRepository products, SupplierRepository suppliers, PurchaseOrderRepository orders,
                           ModelTrainer models) {
        this.products = products;
        this.suppliers = suppliers;
        this.orders = orders;
        this.models = models;
    }

    /** The lead time planning should use now. */
    @Transactional(readOnly = true)
    public LeadTimeEstimate estimate(Product product) {
        return estimateAsOf(product, null);
    }

    /** Prediction using only deliveries received on or before {@code date} (null = all deliveries). */
    @Transactional(readOnly = true)
    public LeadTimeEstimate estimateAsOf(Product product, LocalDate date) {
        List<Integer> leadDays = new ArrayList<>();
        for (PurchaseOrder order : orders.findByProductAndReceivedDateIsNotNullOrderByOrderDateAscIdAsc(product)) {
            if (date == null || !order.getReceivedDate().isAfter(date)) {
                leadDays.add(order.getLeadDays());
            }
        }
        return LeadTimePredictor.estimate(leadDays, product.getLeadTimeDays(), product.getLeadTimeStdDays(), models);
    }

    /** Records a new order (from F3 "Mark ordered") with the lead time predicted on that day. */
    @Transactional
    public PurchaseOrder placeOrder(Product product, int quantity, LocalDate orderDate, ReorderSuggestion suggestion) {
        LeadTimeEstimate estimate = estimateAsOf(product, orderDate);
        return orders.save(new PurchaseOrder(product, product.getSupplier(), quantity, orderDate,
                estimate.getPredictedDays(), suggestion));
    }

    /** Marks an order as received; its real lead time becomes training data. */
    @Transactional
    public void receive(PurchaseOrder order, LocalDate receivedOn) {
        checkReceiptDate(order.getOrderDate(), receivedOn);
        order.setReceivedDate(receivedOn);
        orders.save(order);
    }

    /** Records a delivery that happened before the system was used (history for F6). */
    @Transactional
    public void recordPastDelivery(Long productId, LocalDate orderedOn, LocalDate receivedOn, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (orderedOn == null) {
            throw new IllegalArgumentException("Order date is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        checkReceiptDate(orderedOn, receivedOn);
        PurchaseOrder order = placeOrder(product, quantity, orderedOn, null);
        order.setReceivedDate(receivedOn);
    }

    /** A receipt must be after the order (at least 1 day) and not in the future. */
    static void checkReceiptDate(LocalDate orderedOn, LocalDate receivedOn) {
        if (receivedOn == null) {
            throw new IllegalArgumentException("Received date is required");
        }
        if (receivedOn.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Received date cannot be in the future");
        }
        if (!receivedOn.isAfter(orderedOn)) {
            throw new IllegalArgumentException("Received date must be at least 1 day after the order date (" + orderedOn + ")");
        }
    }

    // ---------------------------------------------------------------- suppliers

    @Transactional
    public Supplier addSupplier(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Supplier name is required");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("Supplier name must be at most 100 characters");
        }
        if (suppliers.findByNameIgnoreCase(name.trim()).isPresent()) {
            throw new IllegalArgumentException("A supplier with this name already exists");
        }
        return suppliers.save(new Supplier(name.trim()));
    }

    @Transactional(readOnly = true)
    public List<Supplier> allSuppliers() {
        return suppliers.findAllByOrderByNameAsc();
    }

    /** The supplier with this id, or null when no supplier was chosen. */
    @Transactional(readOnly = true)
    public Supplier findSupplier(Long id) {
        if (id == null) {
            return null;
        }
        return suppliers.findById(id).orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
    }

    // ---------------------------------------------------------------- page data

    /** One row per product for the F6 table. */
    @Transactional(readOnly = true)
    public List<LeadTimeRow> rows() {
        List<LeadTimeRow> rows = new ArrayList<>();
        for (Product product : products.findAllByOrderByNameAsc()) {
            List<PurchaseOrder> received = orders.findByProductAndReceivedDateIsNotNullOrderByOrderDateAscIdAsc(product);
            List<Integer> recent = new ArrayList<>();
            double errorSum = 0;
            for (int i = received.size() - 1; i >= 0; i--) {
                if (recent.size() < 5) {
                    recent.add(received.get(i).getLeadDays());
                }
                errorSum += Math.abs(received.get(i).getPredictionError());
            }
            Double meanError = received.isEmpty() ? null : errorSum / received.size();
            int open = orders.findByProductAndReceivedDateIsNullOrderByOrderDateAscIdAsc(product).size();
            rows.add(new LeadTimeRow(product, estimate(product), recent, meanError, open));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> openOrders() {
        return orders.findByReceivedDateIsNullOrderByOrderDateAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> recentReceipts() {
        return orders.findTop20ByReceivedDateIsNotNullOrderByReceivedDateDescIdDesc();
    }

    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> orderFor(ReorderSuggestion suggestion) {
        return orders.findBySuggestion(suggestion);
    }
}

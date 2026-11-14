package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * An order placed with a supplier (F6). Created when a reorder suggestion is marked as ordered (F3),
 * or recorded by hand for past deliveries. When it is received, its real lead time becomes
 * training data for the lead-time prediction, and is compared with what was predicted.
 */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** The product's supplier when the order was placed (may be empty if none was set). */
    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    /** What F6 predicted when the order was placed. */
    @Column(name = "predicted_lead_days", nullable = false)
    private double predictedLeadDays;

    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    /** Empty while the order is still on its way. */
    @Column(name = "received_date")
    private LocalDate receivedDate;

    /** The F3 suggestion this order came from, if any. */
    @OneToOne
    @JoinColumn(name = "suggestion_id", unique = true)
    private ReorderSuggestion suggestion;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(Product product, Supplier supplier, int quantity, LocalDate orderDate,
                         double predictedLeadDays, ReorderSuggestion suggestion) {
        this.product = product;
        this.supplier = supplier;
        this.quantity = quantity;
        this.orderDate = orderDate;
        this.predictedLeadDays = predictedLeadDays;
        this.expectedDate = orderDate.plusDays(Math.round(predictedLeadDays));
        this.suggestion = suggestion;
    }

    /** True while the goods have not arrived. */
    public boolean isOpen() {
        return receivedDate == null;
    }

    /** Real days from order to receipt, or null while open. */
    public Integer getLeadDays() {
        return receivedDate == null ? null : (int) ChronoUnit.DAYS.between(orderDate, receivedDate);
    }

    /** Real minus predicted lead time (positive = arrived later than predicted), or null while open. */
    public Double getPredictionError() {
        return receivedDate == null ? null : getLeadDays() - predictedLeadDays;
    }

    /** Days past the expected date for an open order (0 if not late). */
    public long daysLate(LocalDate today) {
        if (!isOpen() || !today.isAfter(expectedDate)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(expectedDate, today);
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public Supplier getSupplier() { return supplier; }
    public int getQuantity() { return quantity; }
    public LocalDate getOrderDate() { return orderDate; }
    public double getPredictedLeadDays() { return predictedLeadDays; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public ReorderSuggestion getSuggestion() { return suggestion; }
}

package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * F3 output: "order N units of this product". The planner marks it as ordered (adds the units to
 * "on order"), then received (moves them into stock), or dismisses it.
 * F19 (supplier order consolidation) will later group open suggestions into purchase orders.
 */
@Entity
@Table(name = "reorder_suggestions")
public class ReorderSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 400)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionStatus status = SuggestionStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ReorderSuggestion() {
    }

    public ReorderSuggestion(Product product, int quantity, String reason) {
        this.product = product;
        this.quantity = quantity;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public SuggestionStatus getStatus() { return status; }
    public void setStatus(SuggestionStatus status) { this.status = status; }

    // Simple yes/no checks used by the page to decide which buttons to show.
    public boolean isOpen() { return status == SuggestionStatus.OPEN; }
    public boolean isOrdered() { return status == SuggestionStatus.ORDERED; }
    public boolean isReceived() { return status == SuggestionStatus.RECEIVED; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** F3: a saved reorder point, written whenever a product's reorder point or safety stock changes. */
@Entity
@Table(name = "reorder_point_history")
public class ReorderPointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint;

    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    @Column(name = "lead_time_demand", nullable = false)
    private double leadTimeDemand;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    protected ReorderPointHistory() {
    }

    public ReorderPointHistory(Product product, int reorderPoint, int safetyStock, double leadTimeDemand) {
        this.product = product;
        this.reorderPoint = reorderPoint;
        this.safetyStock = safetyStock;
        this.leadTimeDemand = leadTimeDemand;
        this.recordedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public int getReorderPoint() { return reorderPoint; }
    public int getSafetyStock() { return safetyStock; }
    public double getLeadTimeDemand() { return leadTimeDemand; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}

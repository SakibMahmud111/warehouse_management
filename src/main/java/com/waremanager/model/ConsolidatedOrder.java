package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "consolidated_orders")
public class ConsolidatedOrder {
    public enum Status { DRAFT, CONFIRMED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private Supplier supplier;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    protected ConsolidatedOrder() {}
    public ConsolidatedOrder(Supplier supplier) { this.supplier = supplier; }
    public Long getId() { return id; }
    public Supplier getSupplier() { return supplier; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

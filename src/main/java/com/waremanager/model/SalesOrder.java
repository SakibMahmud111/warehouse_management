package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_orders")
public class SalesOrder {
    public enum Status { DRAFT, CONFIRMED, PARTIAL, SHIPPED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String customerName;
    /** 1 = highest priority; 3 = lowest priority. */
    @Column(nullable = false)
    private int priority = 2;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    protected SalesOrder() {}
    public SalesOrder(String customerName, int priority) {
        this.customerName = customerName;
        this.priority = priority;
    }
    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public int getPriority() { return priority; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}

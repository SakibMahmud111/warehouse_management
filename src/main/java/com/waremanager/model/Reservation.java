package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_reservations")
public class Reservation {
    public enum Status { ACTIVE, BLOCKED, RELEASED, SHIPPED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private SalesOrderLine orderLine;
    /** Null means stock that predates batch tracking. */
    @ManyToOne
    private Batch batch;
    @Column(nullable = false)
    private int quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Reservation() {}
    public Reservation(SalesOrderLine orderLine, Batch batch, int quantity) {
        this.orderLine = orderLine;
        this.batch = batch;
        this.quantity = quantity;
    }
    public Long getId() { return id; }
    public SalesOrderLine getOrderLine() { return orderLine; }
    public Batch getBatch() { return batch; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

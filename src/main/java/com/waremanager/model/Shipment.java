package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse_shipments")
public class Shipment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private SalesOrderLine orderLine;
    @ManyToOne private Batch batch;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private LocalDateTime shippedAt = LocalDateTime.now();
    protected Shipment() {}
    public Shipment(SalesOrderLine orderLine, Batch batch, int quantity) {
        this.orderLine = orderLine;
        this.batch = batch;
        this.quantity = quantity;
    }
    public Long getId() { return id; }
    public SalesOrderLine getOrderLine() { return orderLine; }
    public Batch getBatch() { return batch; }
    public int getQuantity() { return quantity; }
    public LocalDateTime getShippedAt() { return shippedAt; }
}

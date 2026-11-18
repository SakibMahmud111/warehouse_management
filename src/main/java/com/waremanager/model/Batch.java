package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** A physical lot in one warehouse bin. Quantities are physical, not available-to-promise. */
@Entity
@Table(name = "warehouse_batches", uniqueConstraints = @UniqueConstraint(columnNames = "batch_number"))
public class Batch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private Product product;
    @Column(name = "batch_number", nullable = false, length = 80)
    private String batchNumber;
    @Column(nullable = false, length = 80)
    private String binCode;
    @Column(nullable = false)
    private LocalDate expiryDate;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private boolean held;
    @Column(length = 300)
    private String holdReason;

    protected Batch() {}
    public Batch(Product product, String batchNumber, String binCode, LocalDate expiryDate, int quantity) {
        this.product = product;
        this.batchNumber = batchNumber;
        this.binCode = binCode;
        this.expiryDate = expiryDate;
        this.quantity = quantity;
    }
    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getBatchNumber() { return batchNumber; }
    public String getBinCode() { return binCode; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
    public boolean isHeld() { return held; }
    public void setHeld(boolean value) { held = value; }
    public String getHoldReason() { return holdReason; }
    public void setHoldReason(String value) { holdReason = value; }
}

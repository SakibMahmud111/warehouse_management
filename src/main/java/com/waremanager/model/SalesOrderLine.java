package com.waremanager.model;

import jakarta.persistence.*;

@Entity
@Table(name = "sales_order_lines")
public class SalesOrderLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private SalesOrder salesOrder;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private Product product;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private int minShelfLifeDays;
    @Column(nullable = false)
    private int reservedQuantity;
    @Column(nullable = false)
    private int backorderedQuantity;

    protected SalesOrderLine() {}
    public SalesOrderLine(SalesOrder order, Product product, int quantity, int minShelfLifeDays) {
        this.salesOrder = order;
        this.product = product;
        this.quantity = quantity;
        this.minShelfLifeDays = minShelfLifeDays;
    }
    public Long getId() { return id; }
    public SalesOrder getSalesOrder() { return salesOrder; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public int getQuantity() { return quantity; }
    public int getMinShelfLifeDays() { return minShelfLifeDays; }
    public int getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(int value) { reservedQuantity = value; }
    public int getBackorderedQuantity() { return backorderedQuantity; }
    public void setBackorderedQuantity(int value) { backorderedQuantity = value; }
}

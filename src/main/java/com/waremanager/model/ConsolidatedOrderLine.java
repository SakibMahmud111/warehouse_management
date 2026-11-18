package com.waremanager.model;

import jakarta.persistence.*;

@Entity
@Table(name = "consolidated_order_lines", uniqueConstraints = @UniqueConstraint(columnNames = "suggestion_id"))
public class ConsolidatedOrderLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private ConsolidatedOrder consolidatedOrder;
    @OneToOne(optional = false) @JoinColumn(nullable = false, unique = true)
    private ReorderSuggestion suggestion;
    @Column(nullable = false)
    private int quantity;
    protected ConsolidatedOrderLine() {}
    public ConsolidatedOrderLine(ConsolidatedOrder order, ReorderSuggestion suggestion, int quantity) {
        this.consolidatedOrder = order;
        this.suggestion = suggestion;
        this.quantity = quantity;
    }
    public Long getId() { return id; }
    public ConsolidatedOrder getConsolidatedOrder() { return consolidatedOrder; }
    public ReorderSuggestion getSuggestion() { return suggestion; }
    public Product getProduct() { return suggestion.getProduct(); }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
    public double getLineValue() { return quantity * getProduct().getUnitCost(); }
}

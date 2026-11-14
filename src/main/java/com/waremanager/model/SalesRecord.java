package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Units of one product sold in one week (Monday to Sunday). F1 learns from this history.
 * Later, when sales orders exist (F17), these totals can be filled from them.
 */
@Entity
@Table(name = "sales_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "week_start"}))
public class SalesRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Monday of the week. */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false)
    private int quantity;

    /** True if a holiday or promotion (see SpecialEvent) fell in this week. */
    @Column(name = "event_week", nullable = false)
    private boolean eventWeek;

    protected SalesRecord() {
    }

    public SalesRecord(Product product, LocalDate weekStart, int quantity, boolean eventWeek) {
        this.product = product;
        this.weekStart = weekStart;
        this.quantity = quantity;
        this.eventWeek = eventWeek;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public LocalDate getWeekStart() { return weekStart; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isEventWeek() { return eventWeek; }
    public void setEventWeek(boolean eventWeek) { this.eventWeek = eventWeek; }
}

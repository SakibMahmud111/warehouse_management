package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** F1 output: forecast units for one product in one future week. Replaced on every planning run. */
@Entity
@Table(name = "demand_forecasts")
public class DemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Monday of the forecast week. */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false)
    private double quantity;

    @Column(name = "expected_cold_days", nullable = false)
    private int expectedColdDays;

    @Column(name = "event_week", nullable = false)
    private boolean eventWeek;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    protected DemandForecast() {
    }

    public DemandForecast(Product product, LocalDate weekStart, double quantity, int expectedColdDays,
                          boolean eventWeek, LocalDateTime generatedAt) {
        this.product = product;
        this.weekStart = weekStart;
        this.quantity = quantity;
        this.expectedColdDays = expectedColdDays;
        this.eventWeek = eventWeek;
        this.generatedAt = generatedAt;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public LocalDate getWeekStart() { return weekStart; }
    public double getQuantity() { return quantity; }
    public int getExpectedColdDays() { return expectedColdDays; }
    public boolean isEventWeek() { return eventWeek; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
}

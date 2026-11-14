package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A warning shown to the planner: F7 stockout risk or F20 demand anomaly. */
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private AlertType alertType;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** F20 only: the Monday of the unusual week. */
    @Column(name = "week_start")
    private LocalDate weekStart;

    /** "High" or "Medium". */
    @Column(nullable = false, length = 10)
    private String severity;

    @Column(nullable = false, length = 300)
    private String message;

    /** F7: stockout probability (0 to 1). F20: deviation from the forecast in percent (+ above, - below). */
    @Column(nullable = false)
    private double metric;

    /** F20: units actually sold and units the model expected. */
    @Column(name = "actual_units")
    private Double actualUnits;

    @Column(name = "expected_units")
    private Double expectedUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.OPEN;

    /** The planner's explanation or investigation note. */
    @Column(length = 300)
    private String note;

    /** F7: the reorder suggestion that fixes the risk, if there is one (F3). */
    @ManyToOne
    @JoinColumn(name = "suggestion_id")
    private ReorderSuggestion suggestion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Alert() {
    }

    public Alert(AlertType alertType, Product product, LocalDate weekStart) {
        this.alertType = alertType;
        this.product = product;
        this.weekStart = weekStart;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** True while the alert still needs attention (open or being investigated). */
    public boolean isActive() {
        return status == AlertStatus.OPEN || status == AlertStatus.INVESTIGATING;
    }

    public boolean isAbove() {
        return metric > 0;
    }

    public Long getId() { return id; }
    public AlertType getAlertType() { return alertType; }
    public Product getProduct() { return product; }
    public LocalDate getWeekStart() { return weekStart; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public double getMetric() { return metric; }
    public void setMetric(double metric) { this.metric = metric; }

    public Double getActualUnits() { return actualUnits; }
    public void setActualUnits(Double actualUnits) { this.actualUnits = actualUnits; }

    public Double getExpectedUnits() { return expectedUnits; }
    public void setExpectedUnits(Double expectedUnits) { this.expectedUnits = expectedUnits; }

    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public ReorderSuggestion getSuggestion() { return suggestion; }
    public void setSuggestion(ReorderSuggestion suggestion) { this.suggestion = suggestion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

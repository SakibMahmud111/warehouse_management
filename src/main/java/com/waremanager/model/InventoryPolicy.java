package com.waremanager.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * The planning numbers for one product, recalculated on every planning run.
 * F1 fills the demand fields, F2 the safety stock.
 */
@Entity
@Table(name = "inventory_policies")
public class InventoryPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    // ----- F1: demand -----

    @Column(name = "forecast_method", length = 300)
    private String forecastMethod;

    /** Average forecast units per week over the next 12 weeks. */
    @Column(name = "avg_weekly_demand", nullable = false)
    private double avgWeeklyDemand;

    /** Forecast error: standard deviation of (actual - model) per week. */
    @Column(name = "forecast_error_std", nullable = false)
    private double forecastErrorStd;

    // ----- Lead time used by F2 (copied from the product on each planning run) -----

    @Column(name = "lead_time_days", nullable = false)
    @ColumnDefault("7")
    private double leadTimeDays = 7;

    @Column(name = "lead_time_std_days", nullable = false)
    @ColumnDefault("2")
    private double leadTimeStdDays = 2;

    // ----- F2: safety stock -----

    /** Forecast units sold during one lead time, starting today. */
    @Column(name = "lead_time_demand", nullable = false)
    private double leadTimeDemand;

    /** Target chance of not running out during a lead time (0 to 1). */
    @Column(name = "service_level", nullable = false)
    private double serviceLevel;

    /** z, the safety factor for the service level (for example 1.645 for 95%). */
    @Column(name = "safety_factor", nullable = false)
    private double safetyFactor;

    @Column(name = "safety_stock_calculated", nullable = false)
    private int safetyStockCalculated;

    /** Planner's manual value; when set it replaces the calculated safety stock. */
    @Column(name = "safety_stock_override")
    private Integer safetyStockOverride;

    @Column(name = "override_reason", length = 300)
    private String overrideReason;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected InventoryPolicy() {
    }

    public InventoryPolicy(Product product) {
        this.product = product;
    }

    /** The safety stock actually used: the planner's override if there is one, otherwise the calculated value. */
    public int getEffectiveSafetyStock() {
        return safetyStockOverride != null ? safetyStockOverride : safetyStockCalculated;
    }

    public boolean isOverridden() {
        return safetyStockOverride != null;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }

    public String getForecastMethod() { return forecastMethod; }
    public void setForecastMethod(String forecastMethod) { this.forecastMethod = forecastMethod; }

    public double getAvgWeeklyDemand() { return avgWeeklyDemand; }
    public void setAvgWeeklyDemand(double avgWeeklyDemand) { this.avgWeeklyDemand = avgWeeklyDemand; }

    public double getForecastErrorStd() { return forecastErrorStd; }
    public void setForecastErrorStd(double forecastErrorStd) { this.forecastErrorStd = forecastErrorStd; }

    public double getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(double leadTimeDays) { this.leadTimeDays = leadTimeDays; }

    public double getLeadTimeStdDays() { return leadTimeStdDays; }
    public void setLeadTimeStdDays(double leadTimeStdDays) { this.leadTimeStdDays = leadTimeStdDays; }

    public double getLeadTimeDemand() { return leadTimeDemand; }
    public void setLeadTimeDemand(double leadTimeDemand) { this.leadTimeDemand = leadTimeDemand; }

    public double getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(double serviceLevel) { this.serviceLevel = serviceLevel; }

    public double getSafetyFactor() { return safetyFactor; }
    public void setSafetyFactor(double safetyFactor) { this.safetyFactor = safetyFactor; }

    public int getSafetyStockCalculated() { return safetyStockCalculated; }
    public void setSafetyStockCalculated(int safetyStockCalculated) { this.safetyStockCalculated = safetyStockCalculated; }

    public Integer getSafetyStockOverride() { return safetyStockOverride; }
    public void setSafetyStockOverride(Integer safetyStockOverride) { this.safetyStockOverride = safetyStockOverride; }

    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String overrideReason) { this.overrideReason = overrideReason; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

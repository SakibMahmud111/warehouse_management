package com.waremanager.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The planning numbers for one product, recalculated on every planning run.
 * F1 fills the demand fields, F2 the safety stock, F4 the order quantity, F3 the reorder point.
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

    // ----- F6: lead time used by F2, F4 and F7 (new columns have defaults so old rows upgrade) -----
    @Column(name = "lead_time_days", nullable = false)
    @ColumnDefault("7")
    private double leadTimeDays = 7;

    @Column(name = "lead_time_std_days", nullable = false)
    @ColumnDefault("2")
    private double leadTimeStdDays = 2;

    /** "F6 prediction (8 deliveries)" or "Manual (product)". */
    @Column(name = "lead_time_source", length = 100)
    private String leadTimeSource;

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

    // ----- F4: order quantity -----
    @Column(name = "annual_demand", nullable = false)
    private double annualDemand;

    /** Economic Order Quantity before rounding. */
    @Column(nullable = false)
    private double eoq;

    /** Recommended units per order: EOQ rounded to the minimum order (steady products) or the winter plan (winter products). */
    @Column(name = "order_quantity", nullable = false)
    private int orderQuantity;

    /** Winter products only: most profitable total stock for the rest of the winter. */
    @Column(name = "season_target")
    private Integer seasonTarget;

    /** Winter products only: latest order date so stock arrives before winter. */
    @Column(name = "order_by_date")
    private LocalDate orderByDate;

    // ----- F3: reorder point -----
    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint;

    @Column(name = "reorder_status", length = 300)
    private String reorderStatus;

    /** True when stock on hand + on order is at or below the reorder point. */
    @Column(name = "below_reorder_point", nullable = false)
    private boolean belowReorderPoint;

    // ----- F7: stockout risk -----
    /** Chance (0 to 1) of running out before the next delivery arrives. */
    @Column(name = "stockout_probability", nullable = false)
    @ColumnDefault("0")
    private double stockoutProbability;

    /** Days until the next delivery (or one lead time if nothing is on order). */
    @Column(name = "risk_horizon_days", nullable = false)
    @ColumnDefault("0")
    private int riskHorizonDays;

    /** Forecast units sold during those days. */
    @Column(name = "risk_demand", nullable = false)
    @ColumnDefault("0")
    private double riskDemand;

    /** Plain-language basis, e.g. "until the open order arrives on 20 Nov". */
    @Column(name = "risk_note", length = 300)
    private String riskNote;

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

    public double getAnnualDemand() { return annualDemand; }
    public void setAnnualDemand(double annualDemand) { this.annualDemand = annualDemand; }

    public double getEoq() { return eoq; }
    public void setEoq(double eoq) { this.eoq = eoq; }

    public int getOrderQuantity() { return orderQuantity; }
    public void setOrderQuantity(int orderQuantity) { this.orderQuantity = orderQuantity; }

    public Integer getSeasonTarget() { return seasonTarget; }
    public void setSeasonTarget(Integer seasonTarget) { this.seasonTarget = seasonTarget; }

    public LocalDate getOrderByDate() { return orderByDate; }
    public void setOrderByDate(LocalDate orderByDate) { this.orderByDate = orderByDate; }

    public int getReorderPoint() { return reorderPoint; }
    public void setReorderPoint(int reorderPoint) { this.reorderPoint = reorderPoint; }

    public String getReorderStatus() { return reorderStatus; }
    public void setReorderStatus(String reorderStatus) { this.reorderStatus = reorderStatus; }

    public boolean isBelowReorderPoint() { return belowReorderPoint; }
    public void setBelowReorderPoint(boolean belowReorderPoint) { this.belowReorderPoint = belowReorderPoint; }

    public double getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(double leadTimeDays) { this.leadTimeDays = leadTimeDays; }

    public double getLeadTimeStdDays() { return leadTimeStdDays; }
    public void setLeadTimeStdDays(double leadTimeStdDays) { this.leadTimeStdDays = leadTimeStdDays; }

    public String getLeadTimeSource() { return leadTimeSource; }
    public void setLeadTimeSource(String leadTimeSource) { this.leadTimeSource = leadTimeSource; }

    public double getStockoutProbability() { return stockoutProbability; }
    /** The probability as a whole percentage (0-100), used for the bar width on the F7 page. */
    public int getStockoutPercent() { return (int) Math.round(stockoutProbability * 100); }
    public void setStockoutProbability(double stockoutProbability) { this.stockoutProbability = stockoutProbability; }

    public int getRiskHorizonDays() { return riskHorizonDays; }
    public void setRiskHorizonDays(int riskHorizonDays) { this.riskHorizonDays = riskHorizonDays; }

    public double getRiskDemand() { return riskDemand; }
    public void setRiskDemand(double riskDemand) { this.riskDemand = riskDemand; }

    public String getRiskNote() { return riskNote; }
    public void setRiskNote(String riskNote) { this.riskNote = riskNote; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

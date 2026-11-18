package com.waremanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.hibernate.annotations.ColumnDefault;

/**
 * Business settings used by F1-F4 (the SRS calls this SystemSetting).
 * There is only ever one row, with id 1, edited on the Settings page.
 */
@Entity
@Table(name = "planning_settings")
public class PlanningSettings {

    public static final long SINGLE_ROW_ID = 1L;

    @Id
    private Long id = SINGLE_ROW_ID;

    /** F4: cost of placing one purchase order (paperwork, transport booking...), BDT. */
    @DecimalMin(value = "0.0", message = "Ordering cost must not be negative")
    @Column(name = "ordering_cost", nullable = false)
    private double orderingCost = 1500;

    /** F4: yearly cost of storing stock, as % of its value (rent, capital, damage...). */
    @DecimalMin(value = "1.0", message = "Holding rate must be at least 1%")
    @DecimalMax(value = "100.0", message = "Holding rate must be at most 100%")
    @Column(name = "holding_rate_percent", nullable = false)
    private double holdingRatePercent = 20;

    /** F2: target chance of not running out, for steady (non-winter) products. */
    @DecimalMin(value = "50.0", message = "Service level must be at least 50%")
    @DecimalMax(value = "99.9", message = "Service level must be at most 99.9%")
    @Column(name = "service_level_percent", nullable = false)
    private double serviceLevelPercent = 95;

    /** F1: a day at or below this average temperature counts as a cold (winter) day. */
    @DecimalMin(value = "5.0", message = "Cold-day threshold must be at least 5 °C")
    @DecimalMax(value = "30.0", message = "Cold-day threshold must be at most 30 °C")
    @Column(name = "cold_day_threshold", nullable = false)
    private double coldDayThreshold = 20;

    /** F4: the usual habit to compare the winter plan with ("we always stock for 60 days"). */
    @Min(value = 1, message = "Usual winter stock days must be at least 1")
    @Max(value = 151, message = "Usual winter stock days must be at most 151")
    @Column(name = "default_stock_days", nullable = false)
    private int defaultStockDays = 60;

    /** F7: raise a stockout alert when the chance of running out is at least this (%). */
    @DecimalMin(value = "1.0", message = "Stockout alert level must be at least 1%")
    @DecimalMax(value = "99.0", message = "Stockout alert level must be at most 99%")
    @Column(name = "stockout_alert_percent", nullable = false)
    @ColumnDefault("30")
    private double stockoutAlertPercent = 30;

    /** F20: flag a week whose sales differ from the forecast by at least this (%). */
    @DecimalMin(value = "10.0", message = "Anomaly threshold must be at least 10%")
    @DecimalMax(value = "500.0", message = "Anomaly threshold must be at most 500%")
    @Column(name = "anomaly_threshold_percent", nullable = false)
    @ColumnDefault("50")
    private double anomalyThresholdPercent = 50;

    public Long getId() { return id; }

    public double getOrderingCost() { return orderingCost; }
    public void setOrderingCost(double orderingCost) { this.orderingCost = orderingCost; }

    public double getHoldingRatePercent() { return holdingRatePercent; }
    public void setHoldingRatePercent(double holdingRatePercent) { this.holdingRatePercent = holdingRatePercent; }

    public double getServiceLevelPercent() { return serviceLevelPercent; }
    public void setServiceLevelPercent(double serviceLevelPercent) { this.serviceLevelPercent = serviceLevelPercent; }

    public double getColdDayThreshold() { return coldDayThreshold; }
    public void setColdDayThreshold(double coldDayThreshold) { this.coldDayThreshold = coldDayThreshold; }

    public int getDefaultStockDays() { return defaultStockDays; }
    public void setDefaultStockDays(int defaultStockDays) { this.defaultStockDays = defaultStockDays; }

    public double getStockoutAlertPercent() { return stockoutAlertPercent; }
    public void setStockoutAlertPercent(double stockoutAlertPercent) { this.stockoutAlertPercent = stockoutAlertPercent; }

    public double getAnomalyThresholdPercent() { return anomalyThresholdPercent; }
    public void setAnomalyThresholdPercent(double anomalyThresholdPercent) { this.anomalyThresholdPercent = anomalyThresholdPercent; }
}

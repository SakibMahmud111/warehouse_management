package com.waremanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Business settings used by F1 and F2 (the SRS calls this SystemSetting).
 * There is only ever one row, with id 1, edited on the Settings page.
 */
@Entity
@Table(name = "planning_settings")
public class PlanningSettings {

    public static final long SINGLE_ROW_ID = 1L;

    @Id
    private Long id = SINGLE_ROW_ID;

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

    public Long getId() { return id; }

    public double getServiceLevelPercent() { return serviceLevelPercent; }
    public void setServiceLevelPercent(double serviceLevelPercent) { this.serviceLevelPercent = serviceLevelPercent; }

    public double getColdDayThreshold() { return coldDayThreshold; }
    public void setColdDayThreshold(double coldDayThreshold) { this.coldDayThreshold = coldDayThreshold; }
}

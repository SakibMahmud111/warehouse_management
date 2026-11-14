package com.waremanager.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * F1: what the forecasting model learned about one product, made by {@link DemandForecaster}.
 *
 * Weekly sales ≈ (base + perColdDay × cold days in the week) × eventFactor (only in holiday/promotion weeks).
 * For products without a winter effect, perColdDay is 0 and base is a 12-week moving average.
 */
public class DemandModel {

    private final String method;
    private final boolean winterModel;
    private final double baseWeekly;
    private final double perColdDay;
    private final double eventFactor;
    private final double rSquared;
    private final double errorStd;
    private final double meanAbsoluteError;
    private final int weeksUsed;
    private final Map<LocalDate, Double> fitted;

    public DemandModel(String method, boolean winterModel, double baseWeekly, double perColdDay, double eventFactor,
                       double rSquared, double errorStd, double meanAbsoluteError, int weeksUsed,
                       Map<LocalDate, Double> fitted) {
        this.method = method;
        this.winterModel = winterModel;
        this.baseWeekly = baseWeekly;
        this.perColdDay = perColdDay;
        this.eventFactor = eventFactor;
        this.rSquared = rSquared;
        this.errorStd = errorStd;
        this.meanAbsoluteError = meanAbsoluteError;
        this.weeksUsed = weeksUsed;
        this.fitted = fitted;
    }

    /** The model's estimate of sales for a week with this many cold days. */
    public double predict(int coldDays, boolean eventWeek) {
        double quantity = baseWeekly + perColdDay * coldDays;
        if (eventWeek) {
            quantity *= eventFactor;
        }
        return Math.max(0, quantity);
    }

    /** Plain-language description of how the forecast was made. */
    public String getMethod() { return method; }

    /** True if sales were learned as a function of cold days (winter products). */
    public boolean isWinterModel() { return winterModel; }

    /** Units sold in a normal week with no cold days. */
    public double getBaseWeekly() { return baseWeekly; }

    /** Extra units sold for each cold day. */
    public double getPerColdDay() { return perColdDay; }

    /** Holiday/promotion uplift, e.g. 1.35 means 35% more in event weeks. */
    public double getEventFactor() { return eventFactor; }

    /** How well the winter regression fits (0 to 1); 0 for the moving average. */
    public double getRSquared() { return rSquared; }

    /** Forecast error: standard deviation of (actual - model) per week over the last year. Used by F2. */
    public double getErrorStd() { return errorStd; }

    /** Average size of the weekly error, in units. */
    public double getMeanAbsoluteError() { return meanAbsoluteError; }

    public int getWeeksUsed() { return weeksUsed; }

    /** What the model says each past week should have sold (for the forecast-vs-actual chart). */
    public Map<LocalDate, Double> getFitted() { return fitted; }
}

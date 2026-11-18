package com.waremanager.service;

/**
 * The prediction models: F1 (linear regression), F6 (lead-time prediction), F7 (stockout probability)
 * and F20 (demand anomaly detection).
 *
 * Two implementations give the same numbers:
 * <ul>
 *   <li>{@link PythonModelTrainer}: runs {@code ml/model_worker.py} (Python + scikit-learn). Used by the app.</li>
 *   <li>{@link JavaModelTrainer}: the same formulas in Java. The backup when Python is not available,
 *       and what the plain-Java unit tests use.</li>
 * </ul>
 */
public interface ModelTrainer {

    /** F1: least-squares straight line y = intercept + slope × x (all values rounded to 6 decimals). */
    Statistics.LineFit fitLine(double[] x, double[] y);

    /** F6: prediction from a supplier's past lead times in days, oldest delivery first (at least 3). */
    LeadTimeFit leadTime(double[] leadDaysOldestFirst);

    /**
     * F7: chance that demand during the next {@code horizonDays} days is larger than {@code stock}.
     * Demand is treated as normally distributed with mean {@code demand} (the F1 forecast for those days) and
     * σ = √(days × σd² + d² × σLT²), where σd = {@code dailyErrorStd} and σLT = {@code leadTimeStdDays}.
     */
    StockoutRisk stockoutRisk(double stock, double demand, int horizonDays, double dailyErrorStd, double leadTimeStdDays);

    /**
     * F20: compares each week's actual sales with what the F1 model expected. A week is unusual when it is
     * at least {@code thresholdPercent} and at least {@code minUnits} away from the forecast.
     */
    AnomalyCheck anomalies(double[] actual, double[] expected, double thresholdPercent, double minUnits);

    /**
     * Result of {@link #leadTime}: predicted lead time and its spread (standard deviation), both rounded
     * to 2 decimals, and the trend in days per delivery (positive = the supplier is getting slower).
     */
    record LeadTimeFit(double predictedDays, double spreadDays, double trendPerDelivery) {
    }

    /** Result of {@link #stockoutRisk}: the chance (0 to 1) and the σ of demand that was used. */
    record StockoutRisk(double probability, double demandStd) {
    }

    /** Result of {@link #anomalies}: per week, unusual or not, and the deviation in percent (+ above, − below). */
    record AnomalyCheck(boolean[] unusual, double[] deviationPercent) {
    }
}

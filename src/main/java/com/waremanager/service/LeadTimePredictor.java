package com.waremanager.service;

import java.util.List;

/**
 * F6: predicts a supplier's lead time from past deliveries (no database access).
 *
 *  - Fewer than 3 deliveries: use the lead time typed on the product (manual value).
 *  - Otherwise the model is trained by {@link ModelTrainer} (Python, or the Java backup):
 *    exponentially weighted average. Deliveries are taken oldest first and each new one counts 30%:
 *    estimate = 0.3 × newest + 0.7 × previous estimate. Recent behaviour matters most,
 *    so a supplier that is getting slower is noticed quickly.
 *  - Spread: standard deviation of all past lead times (at least half a day).
 *  - Trend: slope of a straight line through the lead times (days per delivery), shown for information.
 */
public final class LeadTimePredictor {

    public static final int MIN_DELIVERIES = 3;

    private LeadTimePredictor() {
        // only static methods
    }

    /** Same as below, using the Java version of the model (for unit tests). */
    public static LeadTimeEstimate estimate(List<Integer> leadDaysOldestFirst, double manualDays, double manualStdDays) {
        return estimate(leadDaysOldestFirst, manualDays, manualStdDays, JavaModelTrainer.INSTANCE);
    }

    public static LeadTimeEstimate estimate(List<Integer> leadDaysOldestFirst, double manualDays, double manualStdDays,
                                            ModelTrainer models) {
        int count = leadDaysOldestFirst.size();
        if (count < MIN_DELIVERIES) {
            return new LeadTimeEstimate(manualDays, manualStdDays, false, count,
                    "Manual value from the product (" + count + " of " + MIN_DELIVERIES + " deliveries needed for a prediction)", 0);
        }
        double[] days = new double[count];
        for (int i = 0; i < count; i++) {
            days[i] = leadDaysOldestFirst.get(i);
        }
        ModelTrainer.LeadTimeFit fit = models.leadTime(days);
        return new LeadTimeEstimate(fit.predictedDays(), fit.spreadDays(), true, count,
                "Weighted average of " + count + " deliveries (each new delivery counts 30%)", fit.trendPerDelivery());
    }
}

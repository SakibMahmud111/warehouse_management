package com.waremanager.service;

/** F6: how many days a product's supplier is expected to take, made by {@link LeadTimePredictor}. */
public class LeadTimeEstimate {

    /** About 80% of deliveries should fall within ±1.28 standard deviations. */
    private static final double Z_80_PERCENT = 1.28;

    private final double predictedDays;
    private final double stdDays;
    private final boolean fromDeliveries;
    private final int deliveries;
    private final String method;
    private final double trendPerDelivery;

    public LeadTimeEstimate(double predictedDays, double stdDays, boolean fromDeliveries, int deliveries,
                            String method, double trendPerDelivery) {
        this.predictedDays = predictedDays;
        this.stdDays = stdDays;
        this.fromDeliveries = fromDeliveries;
        this.deliveries = deliveries;
        this.method = method;
        this.trendPerDelivery = trendPerDelivery;
    }

    /** Expected days from placing an order to receiving it. */
    public double getPredictedDays() { return predictedDays; }

    /** How much the lead time varies (standard deviation, days). Used by F2 and F7. */
    public double getStdDays() { return stdDays; }

    /** Shortest likely lead time (never below 1 day). */
    public double getLowDays() { return Math.max(1, predictedDays - Z_80_PERCENT * stdDays); }

    /** Longest likely lead time. */
    public double getHighDays() { return predictedDays + Z_80_PERCENT * stdDays; }

    /** True when the value was learned from deliveries; false when the product's manual value is used. */
    public boolean isFromDeliveries() { return fromDeliveries; }

    /** Number of received deliveries the estimate is based on. */
    public int getDeliveries() { return deliveries; }

    /** Plain-language description of how the estimate was made. */
    public String getMethod() { return method; }

    /** Change in lead time per delivery (positive = the supplier is getting slower). */
    public double getTrendPerDelivery() { return trendPerDelivery; }

    /** Short label for tables, e.g. "Predicted (8 deliveries)" or "Manual (product)". */
    public String getSourceLabel() {
        return fromDeliveries ? "Predicted (" + deliveries + " deliveries)" : "Manual (product)";
    }
}

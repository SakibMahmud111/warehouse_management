package com.waremanager.model;

/** What raised an alert. */
public enum AlertType {
    /** F7: a product is likely to run out before its next delivery. */
    STOCKOUT_RISK("Stockout risk"),
    /** F20: a week's sales were far from the forecast. */
    DEMAND_ANOMALY("Demand anomaly");

    private final String label;

    AlertType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

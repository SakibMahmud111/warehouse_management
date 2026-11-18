package com.waremanager.service;

import com.waremanager.model.Product;

import java.util.List;

/** One row of the F6 page: a product, its lead-time prediction and how accurate past predictions were. */
public class LeadTimeRow {

    private final Product product;
    private final LeadTimeEstimate estimate;
    private final List<Integer> recentLeadDays;
    private final Double meanAbsoluteError;
    private final int openOrders;

    public LeadTimeRow(Product product, LeadTimeEstimate estimate, List<Integer> recentLeadDays,
                       Double meanAbsoluteError, int openOrders) {
        this.product = product;
        this.estimate = estimate;
        this.recentLeadDays = recentLeadDays;
        this.meanAbsoluteError = meanAbsoluteError;
        this.openOrders = openOrders;
    }

    public Product getProduct() { return product; }
    public LeadTimeEstimate getEstimate() { return estimate; }
    /** Up to 5 most recent real lead times, newest first. */
    public List<Integer> getRecentLeadDays() { return recentLeadDays; }
    /** Average size of (real - predicted) over received orders, or null if none had a prediction. */
    public Double getMeanAbsoluteError() { return meanAbsoluteError; }
    public int getOpenOrders() { return openOrders; }
}

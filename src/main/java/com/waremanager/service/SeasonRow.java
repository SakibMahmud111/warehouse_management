package com.waremanager.service;

import com.waremanager.model.Product;

/** One row of the F4 winter stock plan. */
public class SeasonRow {

    private final Product product;
    private final DemandModel model;
    private final SeasonPlan plan;

    public SeasonRow(Product product, DemandModel model, SeasonPlan plan) {
        this.product = product;
        this.model = model;
        this.plan = plan;
    }

    public Product getProduct() { return product; }
    public DemandModel getModel() { return model; }
    public SeasonPlan getPlan() { return plan; }
}

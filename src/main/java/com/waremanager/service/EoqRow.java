package com.waremanager.service;

import com.waremanager.model.Product;

/** One row of the F4 EOQ table (steady products). */
public class EoqRow {

    private final Product product;
    private final double annualDemand;
    private final double orderingCost;
    private final double holdingCost;
    private final double eoq;
    private final int recommendedQuantity;
    private final double recommendedYearlyCost;
    private final double currentYearlyCost;

    public EoqRow(Product product, double annualDemand, double orderingCost, double holdingCost, double eoq,
                  int recommendedQuantity) {
        this.product = product;
        this.annualDemand = annualDemand;
        this.orderingCost = orderingCost;
        this.holdingCost = holdingCost;
        this.eoq = eoq;
        this.recommendedQuantity = recommendedQuantity;
        this.recommendedYearlyCost = InventoryMath.annualCost(annualDemand, orderingCost, holdingCost, recommendedQuantity);
        this.currentYearlyCost = InventoryMath.annualCost(annualDemand, orderingCost, holdingCost, product.getCurrentOrderQty());
    }

    public Product getProduct() { return product; }
    /** D: forecast units per year. */
    public double getAnnualDemand() { return annualDemand; }
    /** S: cost of one order. */
    public double getOrderingCost() { return orderingCost; }
    /** H: cost of holding one unit for a year. */
    public double getHoldingCost() { return holdingCost; }
    public double getEoq() { return eoq; }
    /** EOQ rounded up to a whole unit and to the supplier's minimum order. */
    public int getRecommendedQuantity() { return recommendedQuantity; }
    public double getRecommendedYearlyCost() { return recommendedYearlyCost; }

    /** False when the current order size is not set (0). */
    public boolean isCurrentQuantitySet() { return product.getCurrentOrderQty() > 0; }
    public double getCurrentYearlyCost() { return currentYearlyCost; }
    /** Yearly saving from switching to the recommended order size. */
    public double getYearlySaving() { return isCurrentQuantitySet() ? currentYearlyCost - recommendedYearlyCost : 0; }

    public double getOrdersPerYear() {
        return recommendedQuantity > 0 ? annualDemand / recommendedQuantity : 0;
    }

    public double getDaysBetweenOrders() {
        double orders = getOrdersPerYear();
        return orders > 0 ? 365 / orders : 0;
    }
}

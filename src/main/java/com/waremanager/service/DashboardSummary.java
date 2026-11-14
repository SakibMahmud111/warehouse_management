package com.waremanager.service;

/** Numbers shown on the dashboard. */
public class DashboardSummary {

    private final long productCount;
    private final WinterOutlook outlook;
    private final long openSuggestions;
    private final long belowReorderPoint;
    private final double winterPlanExtraProfit;
    private final double winterOrderCost;
    private final long stockoutAlerts;
    private final long demandAnomalies;
    private final long openPurchaseOrders;

    public DashboardSummary(long productCount, WinterOutlook outlook, long openSuggestions, long belowReorderPoint,
                            double winterPlanExtraProfit, double winterOrderCost, long stockoutAlerts,
                            long demandAnomalies, long openPurchaseOrders) {
        this.productCount = productCount;
        this.outlook = outlook;
        this.openSuggestions = openSuggestions;
        this.belowReorderPoint = belowReorderPoint;
        this.winterPlanExtraProfit = winterPlanExtraProfit;
        this.winterOrderCost = winterOrderCost;
        this.stockoutAlerts = stockoutAlerts;
        this.demandAnomalies = demandAnomalies;
        this.openPurchaseOrders = openPurchaseOrders;
    }

    public long getProductCount() { return productCount; }
    public WinterOutlook getOutlook() { return outlook; }
    public long getOpenSuggestions() { return openSuggestions; }
    public long getBelowReorderPoint() { return belowReorderPoint; }
    /** Expected extra profit of the winter plan over the usual fixed-days habit, all winter products. */
    public double getWinterPlanExtraProfit() { return winterPlanExtraProfit; }
    /** Purchase cost of the winter orders recommended now. */
    public double getWinterOrderCost() { return winterOrderCost; }
    /** F7: products with an open stockout alert. */
    public long getStockoutAlerts() { return stockoutAlerts; }
    /** F20: unusual weeks not yet explained. */
    public long getDemandAnomalies() { return demandAnomalies; }
    /** F6: orders still on their way. */
    public long getOpenPurchaseOrders() { return openPurchaseOrders; }
}

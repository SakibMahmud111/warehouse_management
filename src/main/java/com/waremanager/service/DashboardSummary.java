package com.waremanager.service;

/** Numbers shown on the dashboard. */
public class DashboardSummary {

    private final long productCount;
    private final WinterOutlook outlook;
    private final long totalSafetyStock;
    private final long belowSafetyStock;
    private final long overrides;

    public DashboardSummary(long productCount, WinterOutlook outlook, long totalSafetyStock,
                            long belowSafetyStock, long overrides) {
        this.productCount = productCount;
        this.outlook = outlook;
        this.totalSafetyStock = totalSafetyStock;
        this.belowSafetyStock = belowSafetyStock;
        this.overrides = overrides;
    }

    public long getProductCount() { return productCount; }
    public WinterOutlook getOutlook() { return outlook; }
    /** F2: safety stock of all products added together (units). */
    public long getTotalSafetyStock() { return totalSafetyStock; }
    /** F2: products whose stock on hand is below their safety stock. */
    public long getBelowSafetyStock() { return belowSafetyStock; }
    /** F2: products whose safety stock the planner set by hand. */
    public long getOverrides() { return overrides; }
}

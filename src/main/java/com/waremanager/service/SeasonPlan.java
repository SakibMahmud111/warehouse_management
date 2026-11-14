package com.waremanager.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * F4 for winter products: how much stock to hold for the rest of this winter so the business
 * makes the most profit, compared with the usual habit of stocking for a fixed number of days
 * (60 by default).
 *
 * Why not EOQ? EOQ assumes demand is steady all year. Winter stock is different: anything unsold
 * when winter ends must be cleared at a lower price, so ordering too much loses money.
 * This uses the "newsvendor" rule: stock = expected demand + z × uncertainty, where z comes
 * from the critical ratio (see {@link InventoryMath#criticalRatio}).
 */
public class SeasonPlan {

    /** The winter stock also covers sales in the month before the first cold day. */
    static final int PRE_WINTER_DAYS = 30;

    private final double meanDemand;
    private final double demandStd;
    private final int horizonDays;
    private final double criticalRatio;
    private final int targetStock;
    private final int inventoryPosition;
    private final int orderQuantity;
    private final LocalDate orderByDate;
    private final String note;
    private final int defaultStockDays;
    private final int defaultStock;
    private final InventoryMath.SeasonOutcome recommended;
    private final InventoryMath.SeasonOutcome usualPlan;
    private final double unitCost;

    private SeasonPlan(double meanDemand, double demandStd, int horizonDays, double criticalRatio, int targetStock,
                       int inventoryPosition, int orderQuantity, LocalDate orderByDate, String note,
                       int defaultStockDays, int defaultStock, InventoryMath.SeasonOutcome recommended,
                       InventoryMath.SeasonOutcome usualPlan, double unitCost) {
        this.meanDemand = meanDemand;
        this.demandStd = demandStd;
        this.horizonDays = horizonDays;
        this.criticalRatio = criticalRatio;
        this.targetStock = targetStock;
        this.inventoryPosition = inventoryPosition;
        this.orderQuantity = orderQuantity;
        this.orderByDate = orderByDate;
        this.note = note;
        this.defaultStockDays = defaultStockDays;
        this.defaultStock = defaultStock;
        this.recommended = recommended;
        this.usualPlan = usualPlan;
        this.unitCost = unitCost;
    }

    /**
     * @param inventoryPosition stock on hand + stock already on order
     * @param defaultStockDays  the usual habit to compare with, e.g. "stock for a 60-day winter"
     */
    public static SeasonPlan create(double sellingPrice, double unitCost, double leftoverValue,
                                    int inventoryPosition, int minOrderQuantity, double leadTimeDays,
                                    DemandModel model, WinterOutlook outlook, LocalDate today, int defaultStockDays) {
        int remaining = outlook.getRemainingDays();

        // 1. Expected demand from (at most 30 days before winter starts) until the predicted last cold day.
        //    Sales before that are covered by normal reordering (F3), not by the winter stock.
        int horizonDays = 0;
        if (remaining > 0 && outlook.getExpectedEnd() != null) {
            LocalDate planStart = outlook.getExpectedStart().minusDays(PRE_WINTER_DAYS);
            if (planStart.isBefore(today)) {
                planStart = today;
            }
            horizonDays = (int) ChronoUnit.DAYS.between(planStart, outlook.getExpectedEnd()) + 1;
        }
        double dailyBase = model.getBaseWeekly() / 7.0;
        double meanDemand = dailyBase * horizonDays + model.getPerColdDay() * remaining;

        // 2. Uncertainty: the winter may be longer/shorter, and weekly sales vary.
        double winterUncertainty = model.getPerColdDay() * outlook.getPredictionStd();
        double salesUncertainty = model.getErrorStd() * Math.sqrt(horizonDays / 7.0);
        double demandStd = horizonDays == 0 ? 0
                : Math.sqrt(winterUncertainty * winterUncertainty + salesUncertainty * salesUncertainty);

        // 3. Most profitable stock level (newsvendor rule).
        double ratio = InventoryMath.criticalRatio(sellingPrice, unitCost, leftoverValue);
        double z = Statistics.normalInverse(ratio);
        int target = horizonDays == 0 ? 0 : (int) Math.round(Math.max(0, meanDemand + z * demandStd));

        // 4. How much to order on top of what we already have.
        int needed = Math.max(0, target - inventoryPosition);
        int order = InventoryMath.roundUpToMinimum(needed, minOrderQuantity);
        String note;
        if (horizonDays == 0) {
            note = outlook.isFinished() ? "Winter is over: do not buy more winter stock."
                    : "No cold days are expected, so no winter stock is needed.";
        } else if (needed == 0) {
            note = "Stock on hand and on order already covers the rest of this winter.";
        } else {
            note = "Order to reach the most profitable stock level.";
        }
        if (order > needed) {
            // The minimum order quantity forces us to buy more than needed: only do it if it still pays.
            double profitIfOrdered = InventoryMath.expectedOutcome(inventoryPosition + order, meanDemand, demandStd,
                    sellingPrice, unitCost, leftoverValue).getExpectedProfit();
            double profitIfNot = InventoryMath.expectedOutcome(inventoryPosition, meanDemand, demandStd,
                    sellingPrice, unitCost, leftoverValue).getExpectedProfit();
            if (profitIfOrdered <= profitIfNot) {
                order = 0;
                note = "The supplier's minimum order (" + minOrderQuantity
                        + ") is more than this winter needs, so ordering would lose money.";
            } else {
                note = "Rounded up to the supplier's minimum order quantity (" + minOrderQuantity + ").";
            }
        }

        // 5. Order early enough that the stock arrives a week before winter starts.
        LocalDate orderBy = null;
        if (order > 0) {
            orderBy = today;
            if (outlook.getExpectedStart() != null) {
                LocalDate latest = outlook.getExpectedStart().minusDays((long) Math.ceil(leadTimeDays) + 7);
                if (latest.isAfter(today)) {
                    orderBy = latest;
                }
            }
        }

        InventoryMath.SeasonOutcome recommended = InventoryMath.expectedOutcome(inventoryPosition + order,
                meanDemand, demandStd, sellingPrice, unitCost, leftoverValue);

        // 6. The usual habit: stock for a fixed number of winter days.
        int usualRemainingDays = Math.max(0, defaultStockDays - outlook.getColdDaysSoFar());
        int usualStock = (int) Math.round(dailyBase * horizonDays + model.getPerColdDay() * usualRemainingDays);
        usualStock = Math.max(usualStock, inventoryPosition);
        InventoryMath.SeasonOutcome usual = InventoryMath.expectedOutcome(usualStock,
                meanDemand, demandStd, sellingPrice, unitCost, leftoverValue);

        return new SeasonPlan(meanDemand, demandStd, horizonDays, ratio, target, inventoryPosition, order, orderBy,
                note, defaultStockDays, usualStock, recommended, usual, unitCost);
    }

    /** Expected units sold from today until winter ends. */
    public double getMeanDemand() { return meanDemand; }
    /** Uncertainty of that demand (standard deviation, units). */
    public double getDemandStd() { return demandStd; }
    /** Days the winter stock must cover: from today (or 30 days before winter) until the predicted last cold day. */
    public int getHorizonDays() { return horizonDays; }
    /** Most profitable chance of not running out (0 to 1). */
    public double getCriticalRatio() { return criticalRatio; }
    /** Most profitable total stock for the rest of the winter. */
    public int getTargetStock() { return targetStock; }
    public int getInventoryPosition() { return inventoryPosition; }
    /** Units to order now (0 = do not order). */
    public int getOrderQuantity() { return orderQuantity; }
    /** Purchase cost of {@link #getOrderQuantity()}. */
    public double getOrderCost() { return orderQuantity * unitCost; }
    /** Latest date to order so stock arrives before winter, or null if nothing to order. */
    public LocalDate getOrderByDate() { return orderByDate; }
    public String getNote() { return note; }

    public int getDefaultStockDays() { return defaultStockDays; }
    /** Stock the usual habit would hold. */
    public int getDefaultStock() { return defaultStock; }
    /** Purchase cost of what the usual habit would order now. */
    public double getDefaultOrderCost() { return Math.max(0, defaultStock - inventoryPosition) * unitCost; }

    public InventoryMath.SeasonOutcome getRecommended() { return recommended; }
    public InventoryMath.SeasonOutcome getUsualPlan() { return usualPlan; }

    /** Extra expected profit from following the recommendation instead of the usual habit. */
    public double getExtraProfit() {
        return recommended.getExpectedProfit() - usualPlan.getExpectedProfit();
    }
}

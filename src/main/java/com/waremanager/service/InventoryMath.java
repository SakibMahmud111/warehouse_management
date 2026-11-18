package com.waremanager.service;

/**
 * Inventory formulas for F2 (safety stock).
 * Plain Java with no database access, so each formula can be tested on its own.
 */
public final class InventoryMath {

    private InventoryMath() {
        // only static methods
    }

    /**
     * F2: safety stock = z × sqrt(LT × σd² + d² × σLT²), never below 0.
     *
     * @param z               from the service level, e.g. 1.645 for 95%
     * @param dailyDemand     d, expected units per day during the lead time
     * @param dailyDemandStd  σd, forecast error per day
     * @param leadTimeDays    LT, average days from order to delivery
     * @param leadTimeStdDays σLT, how much the lead time varies (days)
     */
    public static double safetyStock(double z, double dailyDemand, double dailyDemandStd,
                                     double leadTimeDays, double leadTimeStdDays) {
        double variance = leadTimeDays * dailyDemandStd * dailyDemandStd
                + dailyDemand * dailyDemand * leadTimeStdDays * leadTimeStdDays;
        return Math.max(0, z * Math.sqrt(variance));
    }

    /**
     * F2, winter products: the most profitable chance of NOT running out, for stock that loses value if unsold
     * (the "newsvendor" critical ratio): profit lost per missed sale / (that + loss per leftover unit).
     *
     * Example: buy 850, sell 1250, leftover sold at 550 → 400 / (400 + 300) = 0.57.
     * So it pays to cover demand only 57% of the time, not 95%.
     */
    public static double criticalRatio(double sellingPrice, double unitCost, double leftoverValue) {
        double lostProfitPerMissedSale = sellingPrice - unitCost;
        double lossPerLeftoverUnit = unitCost - leftoverValue;
        if (lostProfitPerMissedSale <= 0) {
            return 0.01;  // selling at a loss: stock as little as possible
        }
        if (lossPerLeftoverUnit <= 0) {
            return 0.99;  // leftovers cost nothing: cover almost all demand
        }
        double ratio = lostProfitPerMissedSale / (lostProfitPerMissedSale + lossPerLeftoverUnit);
        return Math.min(0.99, Math.max(0.01, ratio));
    }
}

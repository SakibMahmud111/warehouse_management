package com.waremanager.service;

/**
 * Inventory formulas for F2 (safety stock), F3 (reorder point) and F4 (order quantity).
 * Plain Java with no database access, so each formula can be tested on its own.
 */
public final class InventoryMath {

    private InventoryMath() {
        // only static methods
    }

    /**
     * F4: Economic Order Quantity, EOQ = sqrt(2 × D × S / H).
     *
     * @param annualDemand       D, units per year
     * @param orderingCost       S, cost of placing one order (BDT)
     * @param holdingCostPerUnit H, cost of storing one unit for a year (BDT)
     */
    public static double eoq(double annualDemand, double orderingCost, double holdingCostPerUnit) {
        if (annualDemand <= 0 || orderingCost <= 0 || holdingCostPerUnit <= 0) {
            return 0;
        }
        return Math.sqrt(2 * annualDemand * orderingCost / holdingCostPerUnit);
    }

    /** F4: yearly ordering cost + holding cost when ordering q units each time: (D / q) × S + (q / 2) × H. */
    public static double annualCost(double annualDemand, double orderingCost, double holdingCostPerUnit, double q) {
        if (q <= 0) {
            return 0;
        }
        return (annualDemand / q) * orderingCost + (q / 2) * holdingCostPerUnit;
    }

    /** F4: round up to a whole number of units, and never below the supplier's minimum order quantity. */
    public static int roundUpToMinimum(double quantity, int minimumOrderQuantity) {
        if (quantity <= 0) {
            return 0;
        }
        int whole = (int) Math.ceil(quantity - 1e-9);
        return Math.max(whole, minimumOrderQuantity);
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
     * F7: chance that demand before the next delivery is larger than the stock on hand,
     * assuming demand is roughly normal: 1 - Φ((stock - mean) / std).
     */
    public static double stockoutProbability(double stock, double meanDemand, double demandStd) {
        if (meanDemand <= 0) {
            return 0;
        }
        if (demandStd <= 0) {
            return stock < meanDemand ? 1 : 0;
        }
        return 1 - Statistics.normalCdf((stock - meanDemand) / demandStd);
    }

    /**
     * The most profitable chance of NOT running out, for stock that loses value if unsold
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

    /**
     * Expected result of holding {@code stock} units for a season whose demand is roughly normal
     * with the given mean and standard deviation. Unsold units are sold off at {@code leftoverValue}.
     */
    public static SeasonOutcome expectedOutcome(double stock, double meanDemand, double demandStd,
                                                double sellingPrice, double unitCost, double leftoverValue) {
        double expectedSold;
        if (stock <= 0) {
            expectedSold = 0;
        } else if (demandStd <= 0) {
            expectedSold = Math.min(stock, Math.max(0, meanDemand));
        } else {
            double z = (stock - meanDemand) / demandStd;
            double expectedShort = demandStd * Statistics.expectedShortageFactor(z);
            expectedSold = Math.max(0, Math.min(stock, meanDemand - expectedShort));
        }
        double expectedLeftover = Math.max(0, stock - expectedSold);
        return new SeasonOutcome(stock, expectedSold, expectedLeftover, sellingPrice, unitCost, leftoverValue);
    }

    /** Expected units sold, units left over and profit for one stocking decision. */
    public static final class SeasonOutcome {
        private final double stock;
        private final double expectedSold;
        private final double expectedLeftover;
        private final double stockCost;
        private final double salesRevenue;
        private final double leftoverRecovery;

        SeasonOutcome(double stock, double expectedSold, double expectedLeftover,
                      double sellingPrice, double unitCost, double leftoverValue) {
            this.stock = stock;
            this.expectedSold = expectedSold;
            this.expectedLeftover = expectedLeftover;
            this.stockCost = stock * unitCost;
            this.salesRevenue = expectedSold * sellingPrice;
            this.leftoverRecovery = expectedLeftover * leftoverValue;
        }

        public double getStock() { return stock; }
        public double getExpectedSold() { return expectedSold; }
        public double getExpectedLeftover() { return expectedLeftover; }
        /** Cost of all units held, valued at purchase cost. */
        public double getStockCost() { return stockCost; }
        public double getSalesRevenue() { return salesRevenue; }
        /** Money recovered by selling leftovers at the clearance price. */
        public double getLeftoverRecovery() { return leftoverRecovery; }
        /** Revenue + leftover recovery - stock cost. Negative means a loss. */
        public double getExpectedProfit() { return salesRevenue + leftoverRecovery - stockCost; }
    }
}

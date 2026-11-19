package com.waremanager.service;

public final class StockHealthClassifier {
    public enum Status { DEAD, OVERSTOCK, SLOW, HEALTHY }
    public record Result(Status status, double daysOfCover, double valueTiedUp, String suggestion) {}

    private StockHealthClassifier() {}

    public static Result classify(int onHand, double avgWeeklyDemand, Integer daysSinceLastSale,
                                  double unitCost, int deadDays, int overstockCoverDays) {
        double value = onHand * unitCost;
        double daily = avgWeeklyDemand / 7.0;
        double cover = daily <= 0 ? (onHand > 0 ? 9999 : 0) : Math.min(9999, onHand / daily);
        boolean noRecentSale = daysSinceLastSale == null || daysSinceLastSale >= deadDays;

        if (onHand > 0 && noRecentSale)
            return new Result(Status.DEAD, cover, value, "Stop reordering; discount or return to supplier");
        if (cover > overstockCoverDays)
            return new Result(Status.OVERSTOCK, cover, value, "Stop reordering until cover falls; consider a discount");
        if (cover > overstockCoverDays / 2.0)
            return new Result(Status.SLOW, cover, value, "Reduce the next order quantity");
        return new Result(Status.HEALTHY, cover, value, "No action needed");
    }
}
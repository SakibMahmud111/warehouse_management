// F5
package com.waremanager.service;

import java.util.List;

public final class SupplierScoring {
    public record Receipt(boolean onTime, int orderedQty, int receivedQty, int defectiveQty) {}
    public record Result(double onTimeRate, double fillRate, double defectRate, double score, int receipts) {}

    public static final double W_ON_TIME = 0.4, W_FILL = 0.4, W_DEFECT = 0.2;

    private SupplierScoring() {}

    public static Result score(List<Receipt> receipts) {
        if (receipts.isEmpty()) return new Result(0, 0, 0, 0, 0);
        int onTime = 0;
        long ordered = 0, received = 0, defective = 0;
        for (Receipt r : receipts) {
            if (r.onTime()) onTime++;
            ordered += r.orderedQty();
            received += Math.min(r.receivedQty(), r.orderedQty());
            defective += r.defectiveQty();
        }
        double onTimeRate = (double) onTime / receipts.size();
        double fillRate = ordered == 0 ? 1.0 : (double) received / ordered;
        double defectRate = received == 0 ? 0.0 : Math.min(1.0, (double) defective / received);
        double score = 100 * (W_ON_TIME * onTimeRate + W_FILL * fillRate + W_DEFECT * (1 - defectRate));
        return new Result(onTimeRate, fillRate, defectRate, Math.round(score * 10) / 10.0, receipts.size());
    }
}


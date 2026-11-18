package com.waremanager.service;

/**
 * The F1, F6, F7 and F20 models in plain Java: the backup for {@link PythonModelTrainer} when Python is
 * not available, and what the plain-Java unit tests use. It follows {@code ml/model_worker.py} step by step
 * and rounds the same way, so both give identical numbers. The one exception is F7: Python uses SciPy's
 * exact normal distribution, Java the Abramowitz–Stegun formula, so the chance can differ by at most
 * 0.0000001 (0.00001 percentage points).
 */
public final class JavaModelTrainer implements ModelTrainer {

    public static final JavaModelTrainer INSTANCE = new JavaModelTrainer();

    /** F6: each new delivery counts 30% of the prediction. */
    static final double NEWEST_WEIGHT = 0.3;

    private JavaModelTrainer() {
        // use INSTANCE
    }

    @Override
    public Statistics.LineFit fitLine(double[] x, double[] y) {
        Statistics.LineFit line = Statistics.fitLine(x, y);
        return new Statistics.LineFit(round(line.getIntercept(), 1e6), round(line.getSlope(), 1e6),
                round(line.getRSquared(), 1e6), round(line.getResidualStd(), 1e6), line.getPoints());
    }

    @Override
    public LeadTimeFit leadTime(double[] days) {
        if (days.length < LeadTimePredictor.MIN_DELIVERIES) {
            throw new IllegalArgumentException("At least " + LeadTimePredictor.MIN_DELIVERIES + " deliveries are needed");
        }
        // Exponentially weighted average: prediction = 0.3 × newest + 0.7 × previous prediction.
        double weighted = days[0];
        for (int i = 1; i < days.length; i++) {
            weighted = NEWEST_WEIGHT * days[i] + (1 - NEWEST_WEIGHT) * weighted;
        }
        // Spread: standard deviation of all lead times, at least half a day.
        double spread = Math.max(0.5, Statistics.standardDeviation(days));
        // Trend: slope of a line through the lead times (days per delivery).
        double[] index = new double[days.length];
        for (int i = 0; i < days.length; i++) {
            index[i] = i;
        }
        double trend = fitLine(index, days).getSlope();
        // Rounded to 2 decimals so floating-point noise (6 coming out as 6.000000000000001) never
        // turns into an extra whole day when F2 and F7 round the lead time up.
        return new LeadTimeFit(round(weighted, 100), round(spread, 100), trend);
    }

    @Override
    public StockoutRisk stockoutRisk(double stock, double demand, int horizonDays, double dailyErrorStd,
                                     double leadTimeStdDays) {
        double dailyDemand = demand / horizonDays;
        double std = Math.sqrt(horizonDays * dailyErrorStd * dailyErrorStd
                + dailyDemand * dailyDemand * leadTimeStdDays * leadTimeStdDays);
        return new StockoutRisk(InventoryMath.stockoutProbability(stock, demand, std), std);
    }

    @Override
    public AnomalyCheck anomalies(double[] actual, double[] expected, double thresholdPercent, double minUnits) {
        boolean[] unusual = new boolean[actual.length];
        double[] deviation = new double[actual.length];
        for (int i = 0; i < actual.length; i++) {
            boolean usable = expected[i] > 0.5;   // no forecast means nothing to compare with
            double difference = Math.abs(actual[i] - expected[i]);
            deviation[i] = usable ? (actual[i] - expected[i]) / expected[i] * 100 : 0;
            unusual[i] = usable && difference >= minUnits && difference / expected[i] * 100 >= thresholdPercent;
        }
        return new AnomalyCheck(unusual, deviation);
    }

    /** Rounds like {@code round_to} in ml/model_worker.py: factor 100 = 2 decimals, 1e6 = 6 decimals. */
    static double round(double value, double factor) {
        return Math.floor(value * factor + 0.5) / factor;
    }
}

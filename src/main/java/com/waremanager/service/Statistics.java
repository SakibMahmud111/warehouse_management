package com.waremanager.service;

/**
 * Small maths helpers used by the forecasting (F1) and safety-stock (F2) calculations.
 *
 * This is plain Java: it does not use Spring or the database, so it is easy to test
 * and easy to explain. Everything here is standard textbook statistics.
 */
public final class Statistics {

    private Statistics() {
        // only static methods
    }

    public static double mean(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /** Sample standard deviation (divides by n - 1). Returns 0 when there are fewer than 2 values. */
    public static double standardDeviation(double[] values) {
        if (values.length < 2) {
            return 0;
        }
        double m = mean(values);
        double squares = 0;
        for (double v : values) {
            squares += (v - m) * (v - m);
        }
        return Math.sqrt(squares / (values.length - 1));
    }

    /**
     * Simple linear regression: finds the straight line y = intercept + slope * x
     * that best fits the points (least squares). This is the "machine learning" model
     * used by F1: it learns the line from past data and then predicts new values.
     */
    public static LineFit fitLine(double[] x, double[] y) {
        int n = x.length;
        double meanX = mean(x);
        double meanY = mean(y);

        double sxx = 0; // how much x varies
        double sxy = 0; // how x and y vary together
        for (int i = 0; i < n; i++) {
            sxx += (x[i] - meanX) * (x[i] - meanX);
            sxy += (x[i] - meanX) * (y[i] - meanY);
        }
        double slope = sxx == 0 ? 0 : sxy / sxx;
        double intercept = meanY - slope * meanX;

        // How good is the line? R² = 1 means perfect, 0 means no better than the average.
        double totalError = 0;
        double residualError = 0;
        for (int i = 0; i < n; i++) {
            double predicted = intercept + slope * x[i];
            totalError += (y[i] - meanY) * (y[i] - meanY);
            residualError += (y[i] - predicted) * (y[i] - predicted);
        }
        double rSquared = totalError == 0 ? 0 : 1 - residualError / totalError;
        double residualStd = n > 2 ? Math.sqrt(residualError / (n - 2)) : 0;

        return new LineFit(intercept, slope, rSquared, residualStd, n);
    }

    /** The result of {@link #fitLine}. */
    public static final class LineFit {
        private final double intercept;
        private final double slope;
        private final double rSquared;
        private final double residualStd;
        private final int points;

        public LineFit(double intercept, double slope, double rSquared, double residualStd, int points) {
            this.intercept = intercept;
            this.slope = slope;
            this.rSquared = rSquared;
            this.residualStd = residualStd;
            this.points = points;
        }

        public double predict(double x) {
            return intercept + slope * x;
        }

        public double getIntercept() { return intercept; }
        public double getSlope() { return slope; }
        public double getRSquared() { return rSquared; }
        /** Typical size of the prediction error (standard deviation of the residuals). */
        public double getResidualStd() { return residualStd; }
        public int getPoints() { return points; }
    }

    // ---------- Normal (bell-curve) distribution ----------

    /** Probability that a standard normal value is at most z, e.g. normalCdf(1.645) ≈ 0.95. */
    public static double normalCdf(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    /**
     * The z value for a probability, e.g. normalInverse(0.95) ≈ 1.645.
     * Uses bisection: keep halving the interval until normalCdf(z) is close enough to p.
     */
    public static double normalInverse(double p) {
        if (p <= 0) {
            return -8;
        }
        if (p >= 1) {
            return 8;
        }
        double low = -8;
        double high = 8;
        for (int i = 0; i < 100; i++) {
            double middle = (low + high) / 2;
            if (normalCdf(middle) < p) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2;
    }

    /** Error function, Abramowitz and Stegun formula 7.1.26 (accurate to about 0.0000002). */
    private static double erf(double x) {
        double sign = x < 0 ? -1 : 1;
        x = Math.abs(x);
        double t = 1 / (1 + 0.3275911 * x);
        double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741
                + t * (-1.453152027 + t * 1.061405429))));
        return sign * (1 - poly * Math.exp(-x * x));
    }
}

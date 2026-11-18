package com.waremanager.service;

/**
 * The F1 model in plain Java: the backup for {@link PythonModelTrainer} when Python is not available,
 * and what the plain-Java unit tests use. It follows {@code ml/model_worker.py} and rounds the same way,
 * so both give identical numbers.
 */
public final class JavaModelTrainer implements ModelTrainer {

    public static final JavaModelTrainer INSTANCE = new JavaModelTrainer();

    private JavaModelTrainer() {
        // use INSTANCE
    }

    @Override
    public Statistics.LineFit fitLine(double[] x, double[] y) {
        Statistics.LineFit line = Statistics.fitLine(x, y);
        return new Statistics.LineFit(round(line.getIntercept(), 1e6), round(line.getSlope(), 1e6),
                round(line.getRSquared(), 1e6), round(line.getResidualStd(), 1e6), line.getPoints());
    }

    /** Rounds like {@code round_to} in ml/model_worker.py: factor 1e6 = 6 decimals. */
    static double round(double value, double factor) {
        return Math.floor(value * factor + 0.5) / factor;
    }
}

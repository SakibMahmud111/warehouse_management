package com.waremanager.service;

/**
 * The F1 machine-learning model: a least-squares straight line, used for the winter-length prediction
 * and for the "weekly sales = base + extra × cold days" model of winter products.
 *
 * Two implementations give the same numbers:
 * <ul>
 *   <li>{@link PythonModelTrainer}: runs {@code ml/model_worker.py} (Python + scikit-learn). Used by the app.</li>
 *   <li>{@link JavaModelTrainer}: the same formula in Java. The backup when Python is not available,
 *       and what the plain-Java unit tests use.</li>
 * </ul>
 */
public interface ModelTrainer {

    /** F1: least-squares straight line y = intercept + slope × x (all values rounded to 6 decimals). */
    Statistics.LineFit fitLine(double[] x, double[] y);
}

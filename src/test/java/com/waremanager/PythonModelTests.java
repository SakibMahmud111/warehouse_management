package com.waremanager;

import com.waremanager.service.AnomalyService;
import com.waremanager.service.DemoDataGenerator;
import com.waremanager.service.JavaModelTrainer;
import com.waremanager.service.LeadTimeEstimate;
import com.waremanager.service.LeadTimePredictor;
import com.waremanager.service.ModelTrainer;
import com.waremanager.service.PythonModelTrainer;
import com.waremanager.service.Statistics;
import com.waremanager.service.WinterForecaster;
import com.waremanager.service.WinterOutlook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.SortedMap;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The F1, F6, F7 and F20 models run in Python (ml/model_worker.py: scikit-learn, SciPy, NumPy) with a Java backup.
 * These tests check that both give the same numbers, and that the app falls back to Java when Python
 * cannot be used. The Python tests are skipped on a PC without Python and scikit-learn
 * (install them with: pip install -r ml/requirements.txt); GitHub Actions installs them.
 */
class PythonModelTests {

    private static final PythonModelTrainer PYTHON = new PythonModelTrainer(true, "", "ml/model_worker.py");
    private static final ModelTrainer JAVA = JavaModelTrainer.INSTANCE;

    @AfterAll
    static void stopPython() {
        PYTHON.close();
    }

    @Test
    void javaBackupIsUsedWhenPythonCannotStart() {
        PythonModelTrainer missing = new PythonModelTrainer(true, "no-such-python-command", "ml/model_worker.py");
        double[] x = {1, 2, 3, 4};
        double[] y = {3, 5, 7, 9.5};
        Statistics.LineFit line = missing.fitLine(x, y);
        assertEquals(JAVA.fitLine(x, y).getSlope(), line.getSlope());
        assertEquals(JAVA.leadTime(new double[]{5, 6, 9}), missing.leadTime(new double[]{5, 6, 9}));
        assertEquals(JAVA.stockoutRisk(50, 80, 7, 3, 1), missing.stockoutRisk(50, 80, 7, 3, 1));
        assertTrue(missing.anomalies(new double[]{444}, new double[]{239}, 50, 5).unusual()[0]);
        assertFalse(missing.isUsingPython());
        assertTrue(missing.getStatus().startsWith("Java backup"), missing.getStatus());
    }

    @Test
    void javaBackupIsUsedWhenTheScriptIsMissingOrPythonIsSwitchedOff() {
        PythonModelTrainer noScript = new PythonModelTrainer(true, "", "ml/no_such_script.py");
        PythonModelTrainer off = new PythonModelTrainer(false, "", "ml/model_worker.py");
        assertFalse(noScript.isUsingPython());
        assertFalse(off.isUsingPython());
        assertEquals(2.0, noScript.fitLine(new double[]{0, 1, 2}, new double[]{1, 3, 5}).getSlope(), 1e-9);
        assertEquals(2.0, off.fitLine(new double[]{0, 1, 2}, new double[]{1, 3, 5}).getSlope(), 1e-9);
    }

    @Test
    void pythonTrainsTheSameRegressionAsJava() {
        assumeTrue(PYTHON.isUsingPython(), "Python with scikit-learn is not available: " + PYTHON.getStatus());
        assertTrue(PYTHON.getStatus().contains("scikit-learn"));
        SplittableRandom random = new SplittableRandom(470);
        for (int test = 0; test < 40; test++) {
            int n = 4 + random.nextInt(150);
            double[] x = new double[n];
            double[] y = new double[n];
            double slope = random.nextDouble(-20, 20);
            for (int i = 0; i < n; i++) {
                x[i] = test % 2 == 0 ? random.nextInt(8) : random.nextDouble(15, 30);   // cold days or October °C
                y[i] = Math.max(0, Math.round(50 + slope * x[i] + random.nextDouble(-15, 15)));
            }
            assertSameLine(JAVA.fitLine(x, y), PYTHON.fitLine(x, y));
        }
        // edge cases: perfect line, flat sales, all x equal, two points
        assertSameLine(JAVA.fitLine(new double[]{1, 2, 3}, new double[]{2, 4, 6}), PYTHON.fitLine(new double[]{1, 2, 3}, new double[]{2, 4, 6}));
        assertSameLine(JAVA.fitLine(new double[]{0, 3, 5, 7}, new double[]{9, 9, 9, 9}), PYTHON.fitLine(new double[]{0, 3, 5, 7}, new double[]{9, 9, 9, 9}));
        assertSameLine(JAVA.fitLine(new double[]{4, 4, 4}, new double[]{1, 2, 6}), PYTHON.fitLine(new double[]{4, 4, 4}, new double[]{1, 2, 6}));
        assertSameLine(JAVA.fitLine(new double[]{1, 2}, new double[]{5, 3}), PYTHON.fitLine(new double[]{1, 2}, new double[]{5, 3}));
    }

    @Test
    void pythonPredictsTheSameLeadTimesAsJava() {
        assumeTrue(PYTHON.isUsingPython(), "Python with scikit-learn is not available: " + PYTHON.getStatus());
        List<double[]> cases = List.of(new double[]{5, 5, 5, 5}, new double[]{10, 10, 10, 10, 16, 16, 16},
                new double[]{6, 6, 6}, new double[]{12, 14, 13, 15, 14, 16, 15, 17}, new double[]{3, 9, 4, 8, 5});
        for (double[] days : cases) {
            assertEquals(JAVA.leadTime(days), PYTHON.leadTime(days));
        }
        SplittableRandom random = new SplittableRandom(6);
        for (int test = 0; test < 40; test++) {
            double[] days = new double[3 + random.nextInt(10)];
            for (int i = 0; i < days.length; i++) {
                days[i] = 2 + random.nextInt(20);
            }
            ModelTrainer.LeadTimeFit java = JAVA.leadTime(days);
            ModelTrainer.LeadTimeFit python = PYTHON.leadTime(days);
            assertEquals(java.predictedDays(), python.predictedDays());
            assertEquals(java.spreadDays(), python.spreadDays());
            assertEquals(java.trendPerDelivery(), python.trendPerDelivery(), 1e-6);
        }
        LeadTimeEstimate estimate = LeadTimePredictor.estimate(List.of(10, 10, 10, 10, 16, 16, 16), 7, 2, PYTHON);
        assertTrue(estimate.isFromDeliveries());
        assertEquals(LeadTimePredictor.estimate(List.of(10, 10, 10, 10, 16, 16, 16), 7, 2).getPredictedDays(),
                estimate.getPredictedDays());
    }

    @Test
    void pythonGivesTheSameStockoutRiskAsJava() {
        assumeTrue(PYTHON.isUsingPython(), "Python with scikit-learn is not available: " + PYTHON.getStatus());
        SplittableRandom random = new SplittableRandom(7);
        for (int test = 0; test < 60; test++) {
            double stock = random.nextInt(400);
            double demand = random.nextDouble(0, 400);
            int days = 1 + random.nextInt(30);
            double dailyError = random.nextDouble(0, 30);
            double leadTimeStd = random.nextDouble(0, 4);
            ModelTrainer.StockoutRisk java = JAVA.stockoutRisk(stock, demand, days, dailyError, leadTimeStd);
            ModelTrainer.StockoutRisk python = PYTHON.stockoutRisk(stock, demand, days, dailyError, leadTimeStd);
            assertEquals(java.demandStd(), python.demandStd());
            // SciPy's bell curve is exact; the Java backup's formula is accurate to about 0.0000001
            assertEquals(java.probability(), python.probability(), 1e-6);
        }
        assertEquals(0.0, PYTHON.stockoutRisk(10, 0, 7, 0, 1).probability());          // no demand, no risk
        assertEquals(0.5, PYTHON.stockoutRisk(100, 100, 7, 5, 1).probability(), 1e-12); // stock = expected demand
        assertEquals(1.0, PYTHON.stockoutRisk(5, 10, 7, 0, 0).probability());          // certain demand above stock
    }

    @Test
    void pythonFlagsTheSameUnusualWeeksAsJava() {
        assumeTrue(PYTHON.isUsingPython(), "Python with scikit-learn is not available: " + PYTHON.getStatus());
        double[] actual = {444, 29, 260, 4, 7, 120};
        double[] expected = {239, 81, 239, 2, 0.3, 80};
        ModelTrainer.AnomalyCheck python = PYTHON.anomalies(actual, expected, 50, AnomalyService.MIN_UNITS);
        ModelTrainer.AnomalyCheck java = JAVA.anomalies(actual, expected, 50, AnomalyService.MIN_UNITS);
        boolean[] wanted = {true, true, false, false, false, true};   // +86%, -64%, +9%, only 2 units, no forecast, +50%
        for (int i = 0; i < actual.length; i++) {
            assertEquals(wanted[i], python.unusual()[i], "week " + i);
            assertEquals(java.unusual()[i], python.unusual()[i], "week " + i);
            assertEquals(java.deviationPercent()[i], python.deviationPercent()[i]);
            assertEquals(wanted[i], AnomalyService.isAnomaly(actual[i], expected[i], 50), "week " + i);
        }
        SplittableRandom random = new SplittableRandom(23);
        double[] a = new double[300];
        double[] e = new double[300];
        for (int i = 0; i < a.length; i++) {
            e[i] = random.nextDouble(0, 300);
            a[i] = Math.round(e[i] * random.nextDouble(0.2, 2.2));
        }
        python = PYTHON.anomalies(a, e, 40, 5);
        java = JAVA.anomalies(a, e, 40, 5);
        for (int i = 0; i < a.length; i++) {
            assertEquals(java.unusual()[i], python.unusual()[i]);
            assertEquals(java.deviationPercent()[i], python.deviationPercent()[i]);
        }
    }

    @Test
    void winterPredictionIsTheSameWithPythonAndJava() {
        assumeTrue(PYTHON.isUsingPython(), "Python with scikit-learn is not available: " + PYTHON.getStatus());
        for (LocalDate today : List.of(LocalDate.of(2026, 11, 14), LocalDate.of(2027, 1, 5), LocalDate.of(2026, 6, 15))) {
            SortedMap<LocalDate, Double> temperatures = DemoDataGenerator.temperatures(today);
            WinterOutlook java = WinterForecaster.predict(temperatures, today, 20.0);
            WinterOutlook python = WinterForecaster.predict(temperatures, today, 20.0, PYTHON);
            assertEquals(java.getMethod(), python.getMethod());
            assertEquals(java.getPredictedDays(), python.getPredictedDays());
            assertEquals(java.getLowDays(), python.getLowDays());
            assertEquals(java.getHighDays(), python.getHighDays());
            assertEquals(java.getRSquared(), python.getRSquared(), 1e-6);
        }
    }

    private static void assertSameLine(Statistics.LineFit java, Statistics.LineFit python) {
        assertEquals(java.getIntercept(), python.getIntercept(), 1e-6);
        assertEquals(java.getSlope(), python.getSlope(), 1e-6);
        assertEquals(java.getRSquared(), python.getRSquared(), 1e-6);
        assertEquals(java.getResidualStd(), python.getResidualStd(), 1e-6);
        assertEquals(java.getPoints(), python.getPoints());
    }
}

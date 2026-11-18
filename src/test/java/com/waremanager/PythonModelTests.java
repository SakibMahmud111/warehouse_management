package com.waremanager;

import com.waremanager.service.DemoDataGenerator;
import com.waremanager.service.JavaModelTrainer;
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
 * The F1 regression is trained in Python (ml/model_worker.py, scikit-learn) with a Java backup.
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

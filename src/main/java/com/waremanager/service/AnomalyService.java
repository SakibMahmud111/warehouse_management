package com.waremanager.service;

import com.waremanager.model.Alert;
import com.waremanager.model.AlertStatus;
import com.waremanager.model.AlertType;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.Product;
import com.waremanager.model.SalesRecord;
import com.waremanager.repository.AlertRepository;
import com.waremanager.repository.SalesRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * F20 Demand Anomaly Detection (idea list item 23).
 *
 * For each of the last 12 weeks, compare actual sales with what the F1 model expected for that week
 * (it already knows about cold days and holidays, so a cold-week spike in blankets is NOT unusual).
 * A week is flagged when it is at least the threshold (setting, default 50%) above or below the
 * forecast AND at least 5 units away, so tiny products do not raise false alarms. The check runs in
 * {@link ModelTrainer#anomalies} (Python with NumPy, or the Java backup).
 * The planner marks each alert as "investigating" or "explained" (with a note). If the sales are
 * corrected and the week is no longer unusual, an open alert is resolved automatically.
 */
@Service
public class AnomalyService {

    /** Differences smaller than this many units are never flagged. */
    public static final double MIN_UNITS = 5;
    static final int WINDOW_WEEKS = 12;
    private static final Set<AlertStatus> ACTIVE = EnumSet.of(AlertStatus.OPEN, AlertStatus.INVESTIGATING);

    private final SalesRecordRepository sales;
    private final AlertRepository alerts;
    private final ModelTrainer models;

    public AnomalyService(SalesRecordRepository sales, AlertRepository alerts, ModelTrainer models) {
        this.sales = sales;
        this.alerts = alerts;
        this.models = models;
    }

    /** The rule for one week: far enough from the forecast, in percent and in units (same as the model's rule). */
    public static boolean isAnomaly(double actual, double expected, double thresholdPercent) {
        if (expected <= 0.5) {
            return false;
        }
        double difference = Math.abs(actual - expected);
        return difference >= MIN_UNITS && difference / expected * 100 >= thresholdPercent;
    }

    /** +85 means 85% above the forecast; -64 means 64% below. */
    public static double deviationPercent(double actual, double expected) {
        return (actual - expected) / expected * 100;
    }

    /** Planning run, step F20 (needs the F1 model). */
    public void detect(Product product, DemandModel model, PlanningSettings settings, LocalDate today) {
        LocalDate firstWeek = ForecastService.mondayOf(today).minusWeeks(WINDOW_WEEKS);
        double threshold = settings.getAnomalyThresholdPercent();

        // 1. The recent weeks that the F1 model has an expected value for.
        List<SalesRecord> weeks = new ArrayList<>();
        for (SalesRecord record : sales.findByProductOrderByWeekStartAsc(product)) {
            if (!record.getWeekStart().isBefore(firstWeek) && model.getFitted().get(record.getWeekStart()) != null) {
                weeks.add(record);
            }
        }
        if (weeks.isEmpty()) {
            return;
        }

        // 2. Ask the model which weeks are unusual (run in Python).
        double[] actual = new double[weeks.size()];
        double[] expectedUnits = new double[weeks.size()];
        for (int i = 0; i < weeks.size(); i++) {
            actual[i] = weeks.get(i).getQuantity();
            expectedUnits[i] = model.getFitted().get(weeks.get(i).getWeekStart());
        }
        ModelTrainer.AnomalyCheck check = models.anomalies(actual, expectedUnits, threshold, MIN_UNITS);

        // 3. Raise, update or resolve an alert for each week.
        for (int i = 0; i < weeks.size(); i++) {
            SalesRecord record = weeks.get(i);
            double expected = expectedUnits[i];
            Alert existing = alerts.findFirstByAlertTypeAndProductAndWeekStart(
                    AlertType.DEMAND_ANOMALY, product, record.getWeekStart()).orElse(null);

            if (check.unusual()[i]) {
                double deviation = check.deviationPercent()[i];
                Alert alert = existing != null ? existing : new Alert(AlertType.DEMAND_ANOMALY, product, record.getWeekStart());
                if (alert.getStatus() == AlertStatus.RESOLVED) {
                    alert.setStatus(AlertStatus.OPEN);
                }
                alert.setSeverity(Math.abs(deviation) >= 2 * threshold ? "High" : "Medium");
                alert.setMetric(deviation);
                alert.setActualUnits((double) record.getQuantity());
                alert.setExpectedUnits(expected);
                alert.setMessage(String.format(Locale.ENGLISH, "Sold %d, %.0f%% %s the forecast of %.0f.",
                        record.getQuantity(), Math.abs(deviation), deviation > 0 ? "above" : "below", expected));
                if (existing == null) {
                    alerts.save(alert);
                }
            } else if (existing != null && existing.getStatus() == AlertStatus.OPEN) {
                existing.setStatus(AlertStatus.RESOLVED);
                existing.setNote("Resolved automatically: sales for this week now match the forecast.");
            }
        }
    }

    /** The planner explains the week (for example a one-off bulk order). A note is required. */
    @Transactional
    public void markExplained(Long alertId, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Please write what explains this week");
        }
        checkNoteLength(note);
        Alert alert = find(alertId);
        alert.setStatus(AlertStatus.EXPLAINED);
        alert.setNote(note.trim());
    }

    /** The planner is looking into it (for example checking the sales records). */
    @Transactional
    public void markInvestigating(Long alertId, String note) {
        if (note != null) {
            checkNoteLength(note);
        }
        Alert alert = find(alertId);
        alert.setStatus(AlertStatus.INVESTIGATING);
        if (note != null && !note.isBlank()) {
            alert.setNote(note.trim());
        }
    }

    /** The note column holds 300 characters; say so instead of failing in the database. */
    private static void checkNoteLength(String note) {
        if (note.trim().length() > 300) {
            throw new IllegalArgumentException("Note must be at most 300 characters");
        }
    }

    @Transactional(readOnly = true)
    public List<Alert> recentAnomalies() {
        return alerts.findTop50ByAlertTypeOrderByCreatedAtDescIdDesc(AlertType.DEMAND_ANOMALY);
    }

    @Transactional(readOnly = true)
    public long activeCount() {
        return alerts.countByAlertTypeAndStatusIn(AlertType.DEMAND_ANOMALY, ACTIVE);
    }

    private Alert find(Long id) {
        Alert alert = alerts.findById(id).orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        if (alert.getAlertType() != AlertType.DEMAND_ANOMALY) {
            throw new IllegalArgumentException("Only demand anomalies can be explained here");
        }
        return alert;
    }
}

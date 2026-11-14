package com.waremanager.service;

import com.waremanager.model.Alert;
import com.waremanager.model.AlertStatus;
import com.waremanager.model.AlertType;
import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.Product;
import com.waremanager.model.PurchaseOrder;
import com.waremanager.model.ReorderSuggestion;
import com.waremanager.model.SuggestionStatus;
import com.waremanager.repository.AlertRepository;
import com.waremanager.repository.InventoryPolicyRepository;
import com.waremanager.repository.PurchaseOrderRepository;
import com.waremanager.repository.ReorderSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * F7 Stockout Probability Estimation.
 *
 * Question: will the stock on hand last until the next delivery arrives?
 *  - Next delivery: the earliest open purchase order's expected date (F6), or, if nothing is on order,
 *    one predicted lead time from today ("if you order today").
 *  - Demand until then: the F1 forecast for those days (mean) with uncertainty from the F1 forecast
 *    error and the F6 lead-time spread: σ = √(days × σd² + d² × σLT²).
 *  - Chance of running out = 1 - Φ((on hand - mean) / σ), calculated by {@link ModelTrainer#stockoutRisk}
 *    (Python with SciPy's normal distribution, or the Java backup).
 * At or above the alert level (setting, default 30%) an alert is raised and linked to the product's
 * reorder suggestion (F3); when the chance falls below it, the alert is resolved automatically.
 */
@Service
public class RiskService {

    private static final Set<AlertStatus> ACTIVE = EnumSet.of(AlertStatus.OPEN, AlertStatus.INVESTIGATING);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final InventoryPolicyRepository policies;
    private final PurchaseOrderRepository orders;
    private final AlertRepository alerts;
    private final ReorderSuggestionRepository suggestions;
    private final ModelTrainer models;

    public RiskService(InventoryPolicyRepository policies, PurchaseOrderRepository orders,
                       AlertRepository alerts, ReorderSuggestionRepository suggestions, ModelTrainer models) {
        this.policies = policies;
        this.orders = orders;
        this.alerts = alerts;
        this.suggestions = suggestions;
        this.models = models;
    }

    /** Planning run, step F7 (after F3, so the alert can link to the latest suggestion). */
    public void calculate(InventoryPolicy policy, Product product, DemandModel model, List<WeekForecast> forecasts,
                          PlanningSettings settings, LocalDate today) {
        // 1. How many days must the stock on hand last?
        int horizon;
        String basis;
        PurchaseOrder next = null;
        for (PurchaseOrder order : orders.findByProductAndReceivedDateIsNullOrderByOrderDateAscIdAsc(product)) {
            if (next == null || order.getExpectedDate().isBefore(next.getExpectedDate())) {
                next = order;
            }
        }
        if (next != null) {
            long days = ChronoUnit.DAYS.between(today, next.getExpectedDate());
            if (days >= 1) {
                horizon = (int) days;
                basis = "until the open order of " + next.getQuantity() + " arrives (expected " + next.getExpectedDate().format(DAY) + ")";
            } else {
                horizon = Math.max(1, (int) Math.ceil(policy.getLeadTimeStdDays()));
                basis = "the open order expected " + next.getExpectedDate().format(DAY)
                        + " is due or late; assuming it arrives within " + horizon + " days";
            }
        } else {
            horizon = Math.max(1, (int) Math.ceil(policy.getLeadTimeDays()));
            basis = "nothing on order: if you order today it arrives in about " + horizon + " days";
        }

        // 2. Demand during those days (F1 forecast) and the daily forecast error.
        double demand = DemandForecaster.demandOverDays(forecasts, today, horizon);
        double dailyError = model.getErrorStd() / Math.sqrt(7);

        // 3. Chance that the stock on hand is not enough (the prediction model, run in Python).
        ModelTrainer.StockoutRisk risk = models.stockoutRisk(product.getQuantity(), demand, horizon, dailyError,
                policy.getLeadTimeStdDays());
        double probability = risk.probability();
        policy.setStockoutProbability(probability);
        policy.setRiskHorizonDays(horizon);
        policy.setRiskDemand(demand);
        policy.setRiskNote(basis);

        // 4. Raise, update or resolve the alert.
        Alert active = alerts.findFirstByAlertTypeAndProductAndStatusIn(AlertType.STOCKOUT_RISK, product, ACTIVE).orElse(null);
        if (probability * 100 >= settings.getStockoutAlertPercent()) {
            boolean isNew = active == null;
            if (isNew) {
                active = new Alert(AlertType.STOCKOUT_RISK, product, null);
            }
            active.setSeverity(probability >= 0.6 ? "High" : "Medium");
            active.setMetric(probability);
            active.setMessage(String.format(Locale.ENGLISH, "%.0f%% chance of running out: %d on hand, about %.0f forecast to sell (%s).",
                    probability * 100, product.getQuantity(), demand, basis));
            active.setSuggestion(currentSuggestion(product));
            if (isNew) {
                alerts.save(active);
            }
        } else if (active != null) {
            active.setStatus(AlertStatus.RESOLVED);
            active.setNote("Resolved automatically: the risk fell below the alert level.");
        }
    }

    /** The open suggestion, or the one already ordered, that fixes the risk. */
    private ReorderSuggestion currentSuggestion(Product product) {
        return suggestions.findFirstByProductAndStatus(product, SuggestionStatus.OPEN)
                .orElseGet(() -> suggestions.findFirstByProductAndStatus(product, SuggestionStatus.ORDERED).orElse(null));
    }

    /** All products, highest risk first; products with the same risk as shown (0.1% steps) by name. */
    @Transactional(readOnly = true)
    public List<InventoryPolicy> ranked() {
        List<InventoryPolicy> list = policies.findAll();
        list.sort(Comparator.comparingLong((InventoryPolicy policy) -> Math.round(policy.getStockoutProbability() * 1000)).reversed()
                .thenComparing(policy -> policy.getProduct().getName()));
        return list;
    }

    @Transactional(readOnly = true)
    public List<Alert> recentAlerts() {
        return alerts.findTop50ByAlertTypeOrderByCreatedAtDescIdDesc(AlertType.STOCKOUT_RISK);
    }

    @Transactional(readOnly = true)
    public long activeAlertCount() {
        return alerts.countByAlertTypeAndStatusIn(AlertType.STOCKOUT_RISK, ACTIVE);
    }
}

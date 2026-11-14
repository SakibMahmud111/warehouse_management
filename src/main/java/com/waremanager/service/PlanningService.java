package com.waremanager.service;

import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.Product;
import com.waremanager.repository.InventoryPolicyRepository;
import com.waremanager.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Runs the planning features for every product, in this order, because each step uses the ones before:
 *
 *   F6 lead time  ->  F1 forecast  ->  F2 safety stock (forecast error + F6 lead time)
 *                                  ->  F4 order quantity (forecast; winter plan uses the F6 lead time)
 *                                  ->  F3 reorder point and suggestions (F2 safety stock, F4 quantity)
 *                                  ->  F7 stockout risk and alerts (forecast, F6 lead time, F3 suggestion)
 *                                  ->  F20 demand anomalies (F1 model vs actual sales)
 *
 * It runs at startup and after every change (new sales, temperature, settings, product edits, overrides,
 * suggestion actions), so all four pages always show numbers that agree with each other.
 */
@Service
public class PlanningService {

    private final ProductRepository products;
    private final InventoryPolicyRepository policies;
    private final SettingsService settingsService;
    private final WinterService winterService;
    private final ForecastService forecastService;
    private final SafetyStockService safetyStockService;
    private final OrderQuantityService orderQuantityService;
    private final ReorderPointService reorderPointService;
    private final LeadTimeService leadTimeService;
    private final RiskService riskService;
    private final AnomalyService anomalyService;

    public PlanningService(ProductRepository products, InventoryPolicyRepository policies,
                           SettingsService settingsService, WinterService winterService,
                           ForecastService forecastService, SafetyStockService safetyStockService,
                           OrderQuantityService orderQuantityService, ReorderPointService reorderPointService,
                           LeadTimeService leadTimeService, RiskService riskService, AnomalyService anomalyService) {
        this.products = products;
        this.policies = policies;
        this.settingsService = settingsService;
        this.winterService = winterService;
        this.forecastService = forecastService;
        this.safetyStockService = safetyStockService;
        this.orderQuantityService = orderQuantityService;
        this.reorderPointService = reorderPointService;
        this.leadTimeService = leadTimeService;
        this.riskService = riskService;
        this.anomalyService = anomalyService;
    }

    /** Recalculates every product. Returns how many products were planned. */
    @Transactional
    public int recalculateAll() {
        LocalDate today = LocalDate.now();
        PlanningSettings settings = settingsService.get();
        WinterOutlook outlook = winterService.outlook(settings.getColdDayThreshold(), today);

        List<Product> all = products.findAll();
        for (Product product : all) {
            InventoryPolicy policy = policies.findByProduct(product).orElseGet(() -> new InventoryPolicy(product));

            // F6: lead time learned from deliveries (or the product's manual value)
            LeadTimeEstimate leadTime = leadTimeService.estimate(product);
            policy.setLeadTimeDays(leadTime.getPredictedDays());
            policy.setLeadTimeStdDays(leadTime.getStdDays());
            policy.setLeadTimeSource(leadTime.getSourceLabel());

            // F1: learn the model and save 12 weeks of forecasts
            DemandModel model = forecastService.learnModel(product, outlook);
            List<WeekForecast> forecasts = forecastService.forecastAndSave(product, model, outlook, today);
            policy.setForecastMethod(model.getMethod());
            policy.setAvgWeeklyDemand(average(forecasts));
            policy.setForecastErrorStd(model.getErrorStd());

            // F2: safety stock
            safetyStockService.calculate(policy, product, model, forecasts, settings, today);

            // F4: order quantity (EOQ, or the winter plan for winter products)
            orderQuantityService.calculate(policy, product, model, forecasts, outlook, settings, today);

            policies.save(policy);

            // F3: reorder point, suggestion and history
            reorderPointService.calculate(policy, product, today);

            // F7: chance of running out before the next delivery, and its alert
            riskService.calculate(policy, product, model, forecasts, settings, today);

            // F20: unusual sales weeks
            anomalyService.detect(product, model, settings, today);
        }
        return all.size();
    }

    /** This winter's prediction with the current settings (F1). Not read-only: the settings row may be created. */
    @Transactional
    public WinterOutlook currentOutlook() {
        return winterService.outlook(settingsService.get().getColdDayThreshold(), LocalDate.now());
    }

    @Transactional
    public DashboardSummary dashboard() {
        PlanningSettings settings = settingsService.get();
        LocalDate today = LocalDate.now();
        WinterOutlook outlook = winterService.outlook(settings.getColdDayThreshold(), today);
        double extraProfit = 0;
        double orderCost = 0;
        for (SeasonRow row : orderQuantityService.seasonRows(outlook, settings, today)) {
            extraProfit += row.getPlan().getExtraProfit();
            orderCost += row.getPlan().getOrderCost();
        }
        return new DashboardSummary(products.count(), outlook, reorderPointService.openSuggestionCount(),
                policies.countByBelowReorderPointTrue(), extraProfit, orderCost,
                riskService.activeAlertCount(), anomalyService.activeCount(), leadTimeService.openOrders().size());
    }

    private static double average(List<WeekForecast> forecasts) {
        if (forecasts.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (WeekForecast week : forecasts) {
            total += week.getQuantity();
        }
        return total / forecasts.size();
    }
}

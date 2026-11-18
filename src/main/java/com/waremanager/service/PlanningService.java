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
 * Runs the planning features for every product, in this order, because F2 uses F1's results:
 *
 *   F1 forecast (winter prediction, weekly sales model, 12 weeks of forecasts)
 *     ->  F2 safety stock (forecast demand and error + the product's supplier lead time)
 *
 * It runs at startup and after every change (new sales, temperatures, events, settings, product edits,
 * overrides), so both pages always show numbers that agree with each other.
 */
@Service
public class PlanningService {

    private final ProductRepository products;
    private final InventoryPolicyRepository policies;
    private final SettingsService settingsService;
    private final WinterService winterService;
    private final ForecastService forecastService;
    private final SafetyStockService safetyStockService;

    public PlanningService(ProductRepository products, InventoryPolicyRepository policies,
                           SettingsService settingsService, WinterService winterService,
                           ForecastService forecastService, SafetyStockService safetyStockService) {
        this.products = products;
        this.policies = policies;
        this.settingsService = settingsService;
        this.winterService = winterService;
        this.forecastService = forecastService;
        this.safetyStockService = safetyStockService;
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

            // Supplier lead time and its variation, as entered on the product
            policy.setLeadTimeDays(product.getLeadTimeDays());
            policy.setLeadTimeStdDays(product.getLeadTimeStdDays());

            // F1: learn the model and save 12 weeks of forecasts
            DemandModel model = forecastService.learnModel(product, outlook);
            List<WeekForecast> forecasts = forecastService.forecastAndSave(product, model, outlook, today);
            policy.setForecastMethod(model.getMethod());
            policy.setAvgWeeklyDemand(average(forecasts));
            policy.setForecastErrorStd(model.getErrorStd());

            // F2: safety stock
            safetyStockService.calculate(policy, product, model, forecasts, settings, today);

            policies.save(policy);
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
        WinterOutlook outlook = currentOutlook();
        long totalSafetyStock = 0;
        long belowSafetyStock = 0;
        long overrides = 0;
        for (InventoryPolicy policy : policies.findAll()) {
            totalSafetyStock += policy.getEffectiveSafetyStock();
            if (policy.getProduct().getQuantity() < policy.getEffectiveSafetyStock()) {
                belowSafetyStock++;
            }
            if (policy.isOverridden()) {
                overrides++;
            }
        }
        return new DashboardSummary(products.count(), outlook, totalSafetyStock, belowSafetyStock, overrides);
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

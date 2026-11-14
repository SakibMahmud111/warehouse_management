package com.waremanager.service;

import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.Product;
import com.waremanager.repository.InventoryPolicyRepository;
import com.waremanager.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * F4 Economic Order Quantity Recommendations.
 *
 *  - Steady products: EOQ = sqrt(2 × D × S / H), rounded up to the supplier's minimum order,
 *    compared with the order size the business uses today.
 *  - Winter products: EOQ does not fit (demand is not steady and leftovers lose value), so the
 *    winter stock plan ({@link SeasonPlan}) decides how much to order for the rest of the winter.
 */
@Service
public class OrderQuantityService {

    private final ProductRepository products;
    private final InventoryPolicyRepository policies;
    private final ForecastService forecastService;
    private final LeadTimeService leadTimeService;

    public OrderQuantityService(ProductRepository products, InventoryPolicyRepository policies,
                                ForecastService forecastService, LeadTimeService leadTimeService) {
        this.products = products;
        this.policies = policies;
        this.forecastService = forecastService;
        this.leadTimeService = leadTimeService;
    }

    /** Planning run, step F4. Fills the order-quantity fields of {@code policy}. */
    public void calculate(InventoryPolicy policy, Product product, DemandModel model, List<WeekForecast> forecasts,
                          WinterOutlook outlook, PlanningSettings settings, LocalDate today) {
        double annualDemand = averageWeekly(forecasts) * 52;
        double holdingCost = holdingCostPerUnit(product, settings);
        double eoq = InventoryMath.eoq(annualDemand, settings.getOrderingCost(), holdingCost);
        policy.setAnnualDemand(annualDemand);
        policy.setEoq(eoq);

        if (product.isSeasonal()) {
            SeasonPlan plan = seasonPlan(product, model, outlook, settings, today, policy.getLeadTimeDays());
            policy.setOrderQuantity(plan.getOrderQuantity());
            policy.setSeasonTarget(plan.getTargetStock());
            policy.setOrderByDate(plan.getOrderByDate());
        } else {
            policy.setOrderQuantity(InventoryMath.roundUpToMinimum(eoq, product.getMinOrderQty()));
            policy.setSeasonTarget(null);
            policy.setOrderByDate(null);
        }
    }

    /** @param leadTimeDays from F6 (predicted from deliveries, or the product's manual value) */
    public SeasonPlan seasonPlan(Product product, DemandModel model, WinterOutlook outlook,
                                 PlanningSettings settings, LocalDate today, double leadTimeDays) {
        return SeasonPlan.create(product.getSellingPrice(), product.getUnitCost(), product.getClearancePrice(),
                product.getInventoryPosition(), product.getMinOrderQty(), leadTimeDays,
                model, outlook, today, settings.getDefaultStockDays());
    }

    /** F4 table for steady products, using the numbers saved by the last planning run. */
    @Transactional(readOnly = true)
    public List<EoqRow> eoqRows(PlanningSettings settings) {
        List<EoqRow> rows = new ArrayList<>();
        for (Product product : products.findAllByOrderByNameAsc()) {
            if (product.isSeasonal()) {
                continue;
            }
            policies.findByProduct(product).ifPresent(policy -> rows.add(new EoqRow(product,
                    policy.getAnnualDemand(), settings.getOrderingCost(), holdingCostPerUnit(product, settings),
                    policy.getEoq(), policy.getOrderQuantity())));
        }
        return rows;
    }

    /** F4 winter stock plan for every winter product. */
    @Transactional(readOnly = true)
    public List<SeasonRow> seasonRows(WinterOutlook outlook, PlanningSettings settings, LocalDate today) {
        List<SeasonRow> rows = new ArrayList<>();
        for (Product product : products.findAllByOrderByNameAsc()) {
            if (product.isSeasonal()) {
                DemandModel model = forecastService.learnModel(product, outlook);
                double leadTimeDays = leadTimeService.estimate(product).getPredictedDays();
                rows.add(new SeasonRow(product, model, seasonPlan(product, model, outlook, settings, today, leadTimeDays)));
            }
        }
        return rows;
    }

    private static double holdingCostPerUnit(Product product, PlanningSettings settings) {
        return product.getUnitCost() * settings.getHoldingRatePercent() / 100.0;
    }

    private static double averageWeekly(List<WeekForecast> forecasts) {
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

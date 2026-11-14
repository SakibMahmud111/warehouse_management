package com.waremanager.service;

import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.Product;
import com.waremanager.repository.InventoryPolicyRepository;
import com.waremanager.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * F2 Dynamic Safety-Stock Calculation.
 *
 * Safety stock = z × sqrt(LT × σd² + d² × σLT²) where
 *  d   = forecast demand per day during the lead time (from F1),
 *  σd  = forecast error per day (from F1),
 *  LT  = lead time in days, σLT = how much it varies (from F6, stored on the policy by PlanningService),
 *  z   = from the service level.
 * Service level: the setting (95%) for steady products; for winter products the profit-optimal
 * critical ratio, because unsold winter stock loses value (see InventoryMath.criticalRatio).
 */
@Service
public class SafetyStockService {

    private final InventoryPolicyRepository policies;
    private final ProductRepository products;

    public SafetyStockService(InventoryPolicyRepository policies, ProductRepository products) {
        this.policies = policies;
        this.products = products;
    }

    /** Planning run, step F2. Fills the safety-stock fields of {@code policy}. */
    public void calculate(InventoryPolicy policy, Product product, DemandModel model,
                          List<WeekForecast> forecasts, PlanningSettings settings, LocalDate today) {
        double serviceLevel = product.isSeasonal()
                ? InventoryMath.criticalRatio(product.getSellingPrice(), product.getUnitCost(), product.getClearancePrice())
                : settings.getServiceLevelPercent() / 100.0;
        double z = Statistics.normalInverse(serviceLevel);

        int leadDays = Math.max(1, (int) Math.ceil(policy.getLeadTimeDays()));
        double leadTimeDemand = DemandForecaster.demandOverDays(forecasts, today, leadDays);
        double dailyDemand = leadTimeDemand / leadDays;
        double dailyError = model.getErrorStd() / Math.sqrt(7);  // weekly error -> daily error
        double safetyStock = InventoryMath.safetyStock(z, dailyDemand, dailyError,
                policy.getLeadTimeDays(), policy.getLeadTimeStdDays());

        policy.setServiceLevel(serviceLevel);
        policy.setSafetyFactor(z);
        policy.setLeadTimeDemand(leadTimeDemand);
        policy.setSafetyStockCalculated((int) Math.ceil(safetyStock));
    }

    /** The planner replaces the calculated value; a reason is required so the decision is recorded. */
    @Transactional
    public void setOverride(Long productId, Integer units, String reason) {
        if (units == null || units < 0) {
            throw new IllegalArgumentException("Override must be 0 or more units");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Please give a reason for the override");
        }
        if (reason.trim().length() > 300) {
            throw new IllegalArgumentException("Reason must be at most 300 characters");
        }
        InventoryPolicy policy = policyFor(productId);
        policy.setSafetyStockOverride(units);
        policy.setOverrideReason(reason.trim());
    }

    @Transactional
    public void clearOverride(Long productId) {
        InventoryPolicy policy = policyFor(productId);
        policy.setSafetyStockOverride(null);
        policy.setOverrideReason(null);
    }

    /** All policies, sorted by product name. */
    @Transactional(readOnly = true)
    public List<InventoryPolicy> allPolicies() {
        List<InventoryPolicy> list = policies.findAll();
        list.sort(Comparator.comparing(policy -> policy.getProduct().getName()));
        return list;
    }

    private InventoryPolicy policyFor(Long productId) {
        return products.findById(productId)
                .flatMap(policies::findByProduct)
                .orElseThrow(() -> new IllegalArgumentException("No planning data for this product yet"));
    }
}

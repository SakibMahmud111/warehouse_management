package com.waremanager;

import com.waremanager.model.Alert;
import com.waremanager.model.AlertStatus;
import com.waremanager.model.AlertType;
import com.waremanager.model.DemandForecast;
import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.Product;
import com.waremanager.model.ReorderSuggestion;
import com.waremanager.model.SuggestionStatus;
import com.waremanager.model.Supplier;
import com.waremanager.repository.AlertRepository;
import com.waremanager.repository.DemandForecastRepository;
import com.waremanager.repository.InventoryPolicyRepository;
import com.waremanager.repository.ProductRepository;
import com.waremanager.repository.ReorderSuggestionRepository;
import com.waremanager.service.DemandForecaster;
import com.waremanager.service.DemandModel;
import com.waremanager.service.ForecastService;
import com.waremanager.service.InventoryMath;
import com.waremanager.service.LeadTimeService;
import com.waremanager.service.OrderQuantityService;
import com.waremanager.service.PlanningService;
import com.waremanager.service.ProductService;
import com.waremanager.service.SeasonRow;
import com.waremanager.service.SettingsService;
import com.waremanager.service.Statistics;
import com.waremanager.service.WeekForecast;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that the idea-list features 1, 2, 3, 4, 6, 7 and 23 (SRS F1, F2, F3, F4, F6, F7, F20)
 * pass their results to each other correctly. Each check recalculates the expected value
 * from the formula and compares it with what the application saved.
 *
 * The main test uses its own product, supplier, sales and deliveries, so other tests cannot
 * change its numbers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin")
class FeatureChainTests {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductService productService;
    @Autowired LeadTimeService leadTimes;
    @Autowired ForecastService forecastService;
    @Autowired PlanningService planning;
    @Autowired SettingsService settings;
    @Autowired OrderQuantityService orderQuantities;
    @Autowired InventoryPolicyRepository policies;
    @Autowired DemandForecastRepository forecasts;
    @Autowired ReorderSuggestionRepository suggestions;
    @Autowired AlertRepository alerts;

    @Test
    void everyFeatureHasAWorkingPage() throws Exception {
        mvc.perform(get("/forecast")).andExpect(status().isOk()).andExpect(content().string(containsString("DEMAND FORECASTING")));
        mvc.perform(get("/safety-stock")).andExpect(status().isOk()).andExpect(content().string(containsString("DYNAMIC SAFETY-STOCK")));
        mvc.perform(get("/reorder-points")).andExpect(status().isOk()).andExpect(content().string(containsString("REORDER-POINT")));
        mvc.perform(get("/eoq")).andExpect(status().isOk()).andExpect(content().string(containsString("ECONOMIC ORDER QUANTITY")));
        mvc.perform(get("/lead-times")).andExpect(status().isOk()).andExpect(content().string(containsString("SUPPLIER LEAD-TIME")));
        mvc.perform(get("/stockout-risk")).andExpect(status().isOk()).andExpect(content().string(containsString("STOCKOUT PROBABILITY")));
        mvc.perform(get("/anomalies")).andExpect(status().isOk()).andExpect(content().string(containsString("DEMAND ANOMALY")));
    }

    @Test
    void featuresPassTheirResultsToEachOther() throws Exception {
        // ---------- set up a product with its own supplier, deliveries and sales ----------
        Supplier supplier = leadTimes.addSupplier("Chain Test Supplier");
        Product form = new Product();
        form.setName("Chain Test Lentils");
        form.setSku("CHAIN-LNT-01");
        form.setCategory("Grocery");
        form.setQuantity(50);
        form.setUnitCost(100);
        form.setSellingPrice(130);
        form.setClearancePrice(100);
        form.setLeadTimeDays(10);        // manual value: F6 must replace it
        form.setLeadTimeStdDays(2);
        form.setMinOrderQty(20);
        form.setCurrentOrderQty(150);
        productService.add(form, supplier.getId());
        Product product = products.findBySku("CHAIN-LNT-01").orElseThrow();

        LocalDate today = LocalDate.now();
        for (int daysAgo : new int[]{100, 80, 60, 40}) {          // four deliveries of exactly 6 days
            leadTimes.recordPastDelivery(product.getId(), today.minusDays(daysAgo), today.minusDays(daysAgo - 6), 150);
        }
        LocalDate thisMonday = ForecastService.mondayOf(today);
        for (int weeksAgo = 17; weeksAgo >= 1; weeksAgo--) {      // steady sales around 70 a week
            forecastService.recordWeeklySales(product.getId(), thisMonday.minusWeeks(weeksAgo), weeksAgo % 2 == 0 ? 65 : 75);
        }
        planning.recalculateAll();

        InventoryPolicy policy = policies.findByProduct(product).orElseThrow();
        PlanningSettings current = settings.get();
        List<WeekForecast> weeks = savedForecasts(product);
        DemandModel model = forecastService.learnModel(product, planning.currentOutlook());

        // ---------- F1: 12 weekly forecasts saved ----------
        assertEquals(12, weeks.size());
        assertTrue(weeks.get(0).getQuantity() > 50 && weeks.get(0).getQuantity() < 90);

        // ---------- F6 -> planning: the learned lead time replaces the manual 10 days ----------
        assertEquals(6.0, policy.getLeadTimeDays(), 1e-9);
        assertEquals(0.5, policy.getLeadTimeStdDays(), 1e-9);       // constant deliveries -> minimum spread
        assertTrue(policy.getLeadTimeSource().startsWith("Predicted"));

        // ---------- F1 + F6 -> F2: safety stock follows the formula ----------
        double leadTimeDemand = DemandForecaster.demandOverDays(weeks, today, 6);
        double z = Statistics.normalInverse(current.getServiceLevelPercent() / 100);
        double expectedSafety = Math.ceil(InventoryMath.safetyStock(z, leadTimeDemand / 6,
                model.getErrorStd() / Math.sqrt(7), 6.0, 0.5));
        assertEquals(leadTimeDemand, policy.getLeadTimeDemand(), 1e-6);
        assertEquals((int) expectedSafety, policy.getSafetyStockCalculated());

        // ---------- F1 -> F4: EOQ from the forecast, rounded up to the supplier minimum ----------
        double annualDemand = average(weeks) * 52;
        double eoq = InventoryMath.eoq(annualDemand, current.getOrderingCost(), 100 * current.getHoldingRatePercent() / 100);
        assertEquals(eoq, policy.getEoq(), 1e-6);
        assertEquals(InventoryMath.roundUpToMinimum(eoq, 20), policy.getOrderQuantity());

        // ---------- F2 + F4 -> F3: reorder point and suggestion ----------
        int reorderPoint = (int) Math.ceil(leadTimeDemand + policy.getEffectiveSafetyStock());
        assertEquals(reorderPoint, policy.getReorderPoint());
        assertTrue(policy.isBelowReorderPoint(), "50 on hand is below about 60 needed in 6 days");
        ReorderSuggestion suggestion = suggestions.findFirstByProductAndStatus(product, SuggestionStatus.OPEN).orElseThrow();
        assertEquals(InventoryMath.roundUpToMinimum(Math.max(policy.getOrderQuantity(), reorderPoint - 50 + 1), 20),
                suggestion.getQuantity());

        // ---------- F1 + F6 -> F7: stockout probability and its alert linked to the F3 suggestion ----------
        double dailyError = model.getErrorStd() / Math.sqrt(7);
        double daily = leadTimeDemand / 6;
        double sigma = Math.sqrt(6 * dailyError * dailyError + daily * daily * 0.5 * 0.5);
        // 1e-6: with Python the chance comes from SciPy's exact bell curve, the Java formula is accurate to about 1e-7
        assertEquals(InventoryMath.stockoutProbability(50, leadTimeDemand, sigma), policy.getStockoutProbability(), 1e-6);
        assertTrue(policy.getStockoutProbability() * 100 >= current.getStockoutAlertPercent());
        Alert risk = activeStockoutAlert(product);
        assertNotNull(risk.getSuggestion());
        assertEquals(suggestion.getId(), risk.getSuggestion().getId());

        // ---------- F3 -> F6 -> F7: order 7 days ago, receive today ----------
        mvc.perform(post("/reorder-points/suggestions/" + suggestion.getId() + "/ordered").with(csrf())
                .param("orderedOn", today.minusDays(7).toString())).andExpect(redirectedUrl("/reorder-points"));
        mvc.perform(post("/reorder-points/suggestions/" + suggestion.getId() + "/received").with(csrf())
                .param("receivedOn", today.toString())).andExpect(redirectedUrl("/reorder-points"));

        policy = policies.findByProduct(product).orElseThrow();
        assertEquals(0.3 * 7 + 0.7 * 6, policy.getLeadTimeDays(), 1e-9, "F6 must learn the new 7-day delivery");
        assertEquals(50 + suggestion.getQuantity(), products.findBySku("CHAIN-LNT-01").orElseThrow().getQuantity().intValue());
        assertEquals(AlertStatus.RESOLVED, alerts.findById(risk.getId()).orElseThrow().getStatus(), "F7 alert must close after the stock arrives");
        assertFalse(suggestions.findFirstByProductAndStatus(product, SuggestionStatus.OPEN).isPresent());

        // ---------- F1 -> F20: an unusual week is flagged, and corrected sales close it ----------
        // (use a week with no holiday, so the holiday uplift cannot explain it away on any date)
        LocalDate unusualWeek = thisMonday.minusWeeks(3);
        for (int weeksAgo = 3; weeksAgo <= 10; weeksAgo++) {
            LocalDate candidate = thisMonday.minusWeeks(weeksAgo);
            if (forecastService.allEvents().stream().noneMatch(event -> event.overlapsWeek(candidate))) {
                unusualWeek = candidate;
                break;
            }
        }
        forecastService.recordWeeklySales(product.getId(), unusualWeek, 200);
        planning.recalculateAll();
        Alert anomaly = alerts.findFirstByAlertTypeAndProductAndWeekStart(AlertType.DEMAND_ANOMALY, product, unusualWeek).orElseThrow();
        assertTrue(anomaly.isAbove());
        assertEquals(AlertStatus.OPEN, anomaly.getStatus());

        forecastService.recordWeeklySales(product.getId(), unusualWeek, 75);
        planning.recalculateAll();
        assertEquals(AlertStatus.RESOLVED, alerts.findById(anomaly.getId()).orElseThrow().getStatus());

        // ---------- the F1 page shows the product ----------
        mvc.perform(get("/forecast").param("productId", product.getId().toString()))
           .andExpect(status().isOk()).andExpect(content().string(containsString("Chain Test Lentils")));
    }

    @Test
    void winterProductsUseTheSameNumbersOnTheF3AndF4Pages() {
        planning.recalculateAll();   // start from a fresh run, whatever other tests did before
        PlanningSettings current = settings.get();
        for (SeasonRow row : orderQuantities.seasonRows(planning.currentOutlook(), current, LocalDate.now())) {
            InventoryPolicy policy = policies.findByProduct(row.getProduct()).orElseThrow();
            assertEquals(row.getPlan().getOrderQuantity(), policy.getOrderQuantity(), row.getProduct().getName());
            assertEquals(row.getPlan().getOrderByDate(), policy.getOrderByDate(), row.getProduct().getName());
            assertEquals(row.getPlan().getTargetStock(), policy.getSeasonTarget().intValue(), row.getProduct().getName());
        }
    }

    @Test
    void everyPlanIsInternallyConsistent() {
        planning.recalculateAll();
        PlanningSettings current = settings.get();
        for (InventoryPolicy policy : policies.findAll()) {
            Product product = policy.getProduct();
            String name = product.getName();
            assertEquals(12, forecasts.findByProductOrderByWeekStartAsc(product).size(), name);
            assertEquals(leadTimes.estimate(product).getPredictedDays(), policy.getLeadTimeDays(), 1e-9, name);
            if (policy.getReorderPoint() > 0) {
                assertEquals((int) Math.ceil(policy.getLeadTimeDemand() + policy.getEffectiveSafetyStock()), policy.getReorderPoint(), name);
            }
            assertTrue(policy.getStockoutProbability() >= 0 && policy.getStockoutProbability() <= 1, name);
            boolean shouldAlert = policy.getStockoutProbability() * 100 >= current.getStockoutAlertPercent();
            assertEquals(shouldAlert, alerts.findFirstByAlertTypeAndProductAndStatusIn(AlertType.STOCKOUT_RISK, product,
                    List.of(AlertStatus.OPEN, AlertStatus.INVESTIGATING)).isPresent(), name);
        }
    }

    private Alert activeStockoutAlert(Product product) {
        return alerts.findFirstByAlertTypeAndProductAndStatusIn(AlertType.STOCKOUT_RISK, product,
                List.of(AlertStatus.OPEN, AlertStatus.INVESTIGATING)).orElseThrow();
    }

    private List<WeekForecast> savedForecasts(Product product) {
        List<WeekForecast> weeks = new ArrayList<>();
        for (DemandForecast saved : forecasts.findByProductOrderByWeekStartAsc(product)) {
            weeks.add(new WeekForecast(saved.getWeekStart(), saved.getExpectedColdDays(), saved.isEventWeek(), saved.getQuantity()));
        }
        return weeks;
    }

    private static double average(List<WeekForecast> weeks) {
        double total = 0;
        for (WeekForecast week : weeks) {
            total += week.getQuantity();
        }
        return total / weeks.size();
    }
}

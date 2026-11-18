package com.waremanager;

import com.waremanager.model.DemandForecast;
import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.Product;
import com.waremanager.repository.DemandForecastRepository;
import com.waremanager.repository.InventoryPolicyRepository;
import com.waremanager.repository.ProductRepository;
import com.waremanager.service.DemandForecaster;
import com.waremanager.service.DemandModel;
import com.waremanager.service.ForecastService;
import com.waremanager.service.InventoryMath;
import com.waremanager.service.PlanningService;
import com.waremanager.service.ProductService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that F1 (demand forecasting) passes its results to F2 (dynamic safety stock) correctly.
 * Each check recalculates the expected value from the formula and compares it with what the
 * application saved.
 *
 * The main test uses its own product and sales, so other tests cannot change its numbers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin")
class FeatureChainTests {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductService productService;
    @Autowired ForecastService forecastService;
    @Autowired PlanningService planning;
    @Autowired SettingsService settings;
    @Autowired InventoryPolicyRepository policies;
    @Autowired DemandForecastRepository forecasts;

    @Test
    void bothFeaturesHaveAWorkingPage() throws Exception {
        mvc.perform(get("/forecast")).andExpect(status().isOk()).andExpect(content().string(containsString("F1 · DEMAND FORECASTING")));
        mvc.perform(get("/safety-stock")).andExpect(status().isOk()).andExpect(content().string(containsString("F2 · DYNAMIC SAFETY-STOCK")));
    }

    @Test
    void forecastFeedsTheSafetyStock() throws Exception {
        // ---------- set up a product with its own sales ----------
        Product form = new Product();
        form.setName("Chain Test Lentils");
        form.setSku("CHAIN-LNT-01");
        form.setCategory("Grocery");
        form.setQuantity(50);
        form.setUnitCost(100);
        form.setSellingPrice(130);
        form.setClearancePrice(100);
        form.setLeadTimeDays(6);
        form.setLeadTimeStdDays(0.5);
        productService.add(form);
        Product product = products.findBySku("CHAIN-LNT-01").orElseThrow();

        LocalDate today = LocalDate.now();
        LocalDate thisMonday = ForecastService.mondayOf(today);
        for (int weeksAgo = 17; weeksAgo >= 1; weeksAgo--) {      // steady sales around 70 a week
            forecastService.recordWeeklySales(product.getId(), thisMonday.minusWeeks(weeksAgo), weeksAgo % 2 == 0 ? 65 : 75);
        }
        planning.recalculateAll();

        InventoryPolicy policy = policies.findByProduct(product).orElseThrow();
        PlanningSettings current = settings.get();
        List<WeekForecast> weeks = savedForecasts(product);
        DemandModel model = forecastService.learnModel(product, planning.currentOutlook());

        // ---------- F1: 12 weekly forecasts saved, and the plan records the model ----------
        assertEquals(12, weeks.size());
        assertTrue(weeks.get(0).getQuantity() > 50 && weeks.get(0).getQuantity() < 90);
        assertEquals(model.getMethod(), policy.getForecastMethod());
        assertEquals(model.getErrorStd(), policy.getForecastErrorStd(), 1e-9);

        // ---------- the product's lead time is used ----------
        assertEquals(6.0, policy.getLeadTimeDays(), 1e-9);
        assertEquals(0.5, policy.getLeadTimeStdDays(), 1e-9);

        // ---------- F1 -> F2: safety stock follows the formula ----------
        double leadTimeDemand = DemandForecaster.demandOverDays(weeks, today, 6);
        double z = Statistics.normalInverse(current.getServiceLevelPercent() / 100);
        double expectedSafety = Math.ceil(InventoryMath.safetyStock(z, leadTimeDemand / 6,
                model.getErrorStd() / Math.sqrt(7), 6.0, 0.5));
        assertEquals(leadTimeDemand, policy.getLeadTimeDemand(), 1e-6);
        assertEquals(z, policy.getSafetyFactor(), 1e-9);
        assertEquals((int) expectedSafety, policy.getSafetyStockCalculated());
        assertEquals((int) expectedSafety, policy.getEffectiveSafetyStock());

        // ---------- a longer, less reliable lead time needs more safety stock ----------
        Product edit = products.findBySku("CHAIN-LNT-01").orElseThrow();
        edit.setLeadTimeDays(12);
        edit.setLeadTimeStdDays(3);
        productService.update(edit.getId(), edit);
        planning.recalculateAll();
        int longerLeadTimeSafety = policies.findByProduct(product).orElseThrow().getSafetyStockCalculated();
        assertTrue(longerLeadTimeSafety > expectedSafety, "12 ± 3 days must need more safety stock than 6 ± 0.5");

        // ---------- the planner's override is used until removed ----------
        mvc.perform(post("/safety-stock/" + product.getId() + "/override").with(csrf())
                .param("units", "500").param("reason", "Supplier strike announced"))
           .andExpect(redirectedUrl("/safety-stock"));
        policy = policies.findByProduct(product).orElseThrow();
        assertEquals(500, policy.getEffectiveSafetyStock());
        assertEquals(longerLeadTimeSafety, policy.getSafetyStockCalculated(), "the calculated value is still shown next to it");

        mvc.perform(post("/safety-stock/" + product.getId() + "/override/clear").with(csrf()))
           .andExpect(redirectedUrl("/safety-stock"));
        assertEquals(longerLeadTimeSafety, policies.findByProduct(product).orElseThrow().getEffectiveSafetyStock());

        // ---------- both pages show the product ----------
        mvc.perform(get("/forecast").param("productId", product.getId().toString()))
           .andExpect(status().isOk()).andExpect(content().string(containsString("Chain Test Lentils")));
        mvc.perform(get("/safety-stock"))
           .andExpect(status().isOk()).andExpect(content().string(containsString("Chain Test Lentils")));
    }

    @Test
    void everyPlanIsInternallyConsistent() {
        planning.recalculateAll();   // start from a fresh run, whatever other tests did before
        PlanningSettings current = settings.get();
        for (InventoryPolicy policy : policies.findAll()) {
            Product product = policy.getProduct();
            String name = product.getName();
            assertEquals(12, forecasts.findByProductOrderByWeekStartAsc(product).size(), name);
            assertEquals(product.getLeadTimeDays(), policy.getLeadTimeDays(), 1e-9, name);
            assertEquals(product.getLeadTimeStdDays(), policy.getLeadTimeStdDays(), 1e-9, name);
            double expectedLevel = product.isSeasonal()
                    ? InventoryMath.criticalRatio(product.getSellingPrice(), product.getUnitCost(), product.getClearancePrice())
                    : current.getServiceLevelPercent() / 100;
            assertEquals(expectedLevel, policy.getServiceLevel(), 1e-9, name);
            assertTrue(policy.getSafetyStockCalculated() >= 0, name);
        }
    }

    private List<WeekForecast> savedForecasts(Product product) {
        List<WeekForecast> weeks = new ArrayList<>();
        for (DemandForecast saved : forecasts.findByProductOrderByWeekStartAsc(product)) {
            weeks.add(new WeekForecast(saved.getWeekStart(), saved.getExpectedColdDays(), saved.isEventWeek(), saved.getQuantity()));
        }
        return weeks;
    }
}

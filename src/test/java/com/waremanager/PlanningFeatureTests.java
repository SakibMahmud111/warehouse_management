package com.waremanager;

import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.Product;
import com.waremanager.model.SalesRecord;
import com.waremanager.repository.DemandForecastRepository;
import com.waremanager.repository.InventoryPolicyRepository;
import com.waremanager.repository.ProductRepository;
import com.waremanager.repository.SalesRecordRepository;
import com.waremanager.service.ForecastService;
import com.waremanager.service.PlanningService;
import com.waremanager.service.ProductService;
import com.waremanager.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for F1 and F2: the demo data is loaded at startup and the planning run has
 * already happened, exactly as when the app starts for real (but on the H2 test database).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin")
class PlanningFeatureTests {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductService productService;
    @Autowired InventoryPolicyRepository policies;
    @Autowired DemandForecastRepository forecasts;
    @Autowired SalesRecordRepository sales;
    @Autowired PlanningService planning;
    @Autowired SettingsService settings;
    @Autowired ForecastService forecastService;

    // ---------------------------------------------------------------- start-up

    @Test
    void demoDataIsLoadedAndEveryProductHasAPlan() {
        assertTrue(products.count() >= 6);
        for (Product product : products.findAll()) {
            assertTrue(policies.findByProduct(product).isPresent(), "no plan for " + product.getName());
            assertEquals(12, forecasts.findByProductOrderByWeekStartAsc(product).size(), "forecast weeks for " + product.getName());
        }
        assertEquals(156, sales.findByProductOrderByWeekStartAsc(bySku("WM-RCE-04")).size());
    }

    // ---------------------------------------------------------------- every page renders

    @Test
    void allPagesRender() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk()).andExpect(content().string(containsString("Below safety stock")));
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(content().string(containsString("Fleece Blanket")));
        mvc.perform(get("/products/new")).andExpect(status().isOk()).andExpect(content().string(containsString("Add product")));
        mvc.perform(get("/products/" + bySku("WM-BLK-01").getId() + "/edit"))
           .andExpect(status().isOk()).andExpect(content().string(containsString("Edit product")));
        mvc.perform(get("/forecast")).andExpect(status().isOk()).andExpect(content().string(containsString("outlook")))
           .andExpect(content().string(containsString("Machine-learning models trained by")));
        mvc.perform(get("/safety-stock")).andExpect(status().isOk()).andExpect(content().string(containsString("Safety stock")));
        mvc.perform(get("/settings")).andExpect(status().isOk()).andExpect(content().string(containsString("Planning settings")));
    }

    @Test
    void forecastPageRendersForEveryProduct() throws Exception {
        for (Product product : products.findAll()) {
            mvc.perform(get("/forecast").param("productId", product.getId().toString()))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString(product.getName().replace("'", "&#39;")))); // Thymeleaf escapes 
        }
    }

    // ---------------------------------------------------------------- F1

    @Test
    void recordingSalesSavesTheWeekAndReplans() throws Exception {
        Product rice = bySku("WM-RCE-04");
        LocalDate lastWeek = LocalDate.now().minusWeeks(1);
        mvc.perform(post("/forecast/sales").with(csrf())
                .param("productId", rice.getId().toString())
                .param("week", lastWeek.toString())
                .param("quantity", "123"))
           .andExpect(redirectedUrl("/forecast?productId=" + rice.getId()))
           .andExpect(flash().attributeExists("message"));
        SalesRecord saved = sales.findByProductAndWeekStart(rice, ForecastService.mondayOf(lastWeek)).orElseThrow();
        assertEquals(123, saved.getQuantity());
    }

    @Test
    void futureTemperatureIsRejectedWithAMessage() throws Exception {
        mvc.perform(post("/forecast/temperatures").with(csrf())
                .param("date", LocalDate.now().plusDays(3).toString())
                .param("averageC", "18"))
           .andExpect(redirectedUrl("/forecast"))
           .andExpect(flash().attribute("error", "Temperature readings cannot be in the future"));
    }

    @Test
    void temperatureAndEventCanBeAdded() throws Exception {
        mvc.perform(post("/forecast/temperatures").with(csrf())
                .param("date", LocalDate.now().toString())
                .param("averageC", "24.5"))
           .andExpect(flash().attributeExists("message"));
        mvc.perform(post("/forecast/events").with(csrf())
                .param("name", "Test promotion")
                .param("startDate", LocalDate.now().plusWeeks(2).toString())
                .param("endDate", LocalDate.now().plusWeeks(2).plusDays(2).toString()))
           .andExpect(flash().attributeExists("message"));
        assertTrue(forecastService.allEvents().stream().anyMatch(e -> e.getName().equals("Test promotion")));
    }

    // ---------------------------------------------------------------- F2

    @Test
    void safetyStockOverrideIsUsedAndCanBeRemoved() throws Exception {
        Product rice = bySku("WM-RCE-04");
        mvc.perform(post("/safety-stock/" + rice.getId() + "/override").with(csrf())
                .param("units", "500").param("reason", "Supplier strike expected"))
           .andExpect(redirectedUrl("/safety-stock"));
        InventoryPolicy policy = policies.findByProduct(rice).orElseThrow();
        assertEquals(500, policy.getEffectiveSafetyStock());
        assertEquals("Supplier strike expected", policy.getOverrideReason());

        mvc.perform(post("/safety-stock/" + rice.getId() + "/override").with(csrf()).param("units", "10").param("reason", " "))
           .andExpect(flash().attribute("error", "Please give a reason for the override"));

        mvc.perform(post("/safety-stock/" + rice.getId() + "/override/clear").with(csrf()))
           .andExpect(redirectedUrl("/safety-stock"));
        policy = policies.findByProduct(rice).orElseThrow();
        assertNull(policy.getSafetyStockOverride());
        assertEquals(policy.getSafetyStockCalculated(), policy.getEffectiveSafetyStock());
    }

    @Test
    void stockBelowSafetyStockIsShownOnTheDashboard() throws Exception {
        Product rice = setStock("WM-RCE-04", 0);
        int safety = policies.findByProduct(rice).orElseThrow().getEffectiveSafetyStock();
        assertTrue(safety > 0);
        assertTrue(planning.dashboard().getBelowSafetyStock() >= 1);
        mvc.perform(get("/safety-stock")).andExpect(status().isOk()).andExpect(content().string(containsString("Miniket Rice")));

        setStock("WM-RCE-04", 260);   // put the demo stock back for the other tests
        assertTrue(policies.findByProduct(bySku("WM-RCE-04")).orElseThrow().getEffectiveSafetyStock() < 260);
    }

    @Test
    void settingsChangeRecalculatesEveryPlan() throws Exception {
        Product soap = bySku("WM-SOP-06");
        int before = policies.findByProduct(soap).orElseThrow().getSafetyStockCalculated();
        mvc.perform(post("/settings").with(csrf())
                .param("serviceLevelPercent", "99").param("coldDayThreshold", "20"))
           .andExpect(redirectedUrl("/settings"));
        InventoryPolicy safer = policies.findByProduct(soap).orElseThrow();
        assertEquals(0.99, safer.getServiceLevel(), 1e-9);
        assertEquals(99, settings.get().getServiceLevelPercent(), 1e-9);
        assertTrue(safer.getSafetyStockCalculated() > before, "a higher service level needs more safety stock");

        mvc.perform(post("/settings").with(csrf())
                .param("serviceLevelPercent", "95").param("coldDayThreshold", "20"))
           .andExpect(redirectedUrl("/settings"));
        assertEquals(0.95, policies.findByProduct(soap).orElseThrow().getServiceLevel(), 1e-9);
        assertEquals(before, policies.findByProduct(soap).orElseThrow().getSafetyStockCalculated());
    }

    @Test
    void invalidSettingsAreRejected() throws Exception {
        mvc.perform(post("/settings").with(csrf())
                .param("serviceLevelPercent", "40").param("coldDayThreshold", "20"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Service level must be at least 50%")));
        assertEquals(95, settings.get().getServiceLevelPercent(), 1e-9);
    }

    // ---------------------------------------------------------------- products

    @Test
    void newProductGetsAPlanStraightAway() throws Exception {
        mvc.perform(post("/products").with(csrf())
                .param("name", "Hurricane Lantern").param("sku", "TEST-LAN-01").param("category", "Hardware")
                .param("quantity", "10").param("unitCost", "300").param("sellingPrice", "420")
                .param("clearancePrice", "300").param("leadTimeDays", "5").param("leadTimeStdDays", "1"))
           .andExpect(redirectedUrl("/products"));
        Product lantern = bySku("TEST-LAN-01");
        InventoryPolicy policy = policies.findByProduct(lantern).orElseThrow();
        assertNotNull(policy.getForecastMethod());   // "No sales history yet..."
        assertEquals(0, policy.getAvgWeeklyDemand(), 1e-9);
        assertEquals(0, policy.getSafetyStockCalculated());   // no forecast demand yet, so no buffer needed
    }

    @Test
    void productFormShowsValidationErrors() throws Exception {
        mvc.perform(post("/products").with(csrf())
                .param("name", "").param("sku", "BAD-1").param("category", "X")
                .param("quantity", "-1").param("unitCost", "1").param("sellingPrice", "1")
                .param("clearancePrice", "0").param("leadTimeDays", "5").param("leadTimeStdDays", "1"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Product name is required")))
           .andExpect(content().string(containsString("Quantity must not be negative")));
    }

    @Test
    void tooLongTextGetsAMessageInsteadOfADatabaseError() throws Exception {
        String tooLong = "x".repeat(400);
        Product rice = bySku("WM-RCE-04");
        mvc.perform(post("/products").with(csrf())
                .param("name", tooLong).param("sku", "LONG-1").param("category", "General")
                .param("quantity", "1").param("unitCost", "1").param("sellingPrice", "2")
                .param("clearancePrice", "0").param("leadTimeDays", "5").param("leadTimeStdDays", "1"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Product name must be at most 255 characters")));
        mvc.perform(post("/forecast/events").with(csrf()).param("name", tooLong)
                .param("startDate", LocalDate.now().toString()).param("endDate", LocalDate.now().toString()))
           .andExpect(flash().attribute("error", "Event name must be at most 100 characters"));
        mvc.perform(post("/safety-stock/" + rice.getId() + "/override").with(csrf()).param("units", "10").param("reason", tooLong))
           .andExpect(flash().attribute("error", "Reason must be at most 300 characters"));
        assertTrue(products.findBySku("LONG-1").isEmpty());
        assertNull(policies.findByProduct(rice).orElseThrow().getSafetyStockOverride());
    }

    // ---------------------------------------------------------------- helpers

    private Product bySku(String sku) {
        return products.findBySku(sku).orElseThrow();
    }

    /** Sets a product's stock through the normal service (so validation rules apply) and replans. */
    private Product setStock(String sku, int quantity) {
        Product product = bySku(sku);
        product.setQuantity(quantity);
        productService.update(product.getId(), product);
        planning.recalculateAll();
        return bySku(sku);
    }
}

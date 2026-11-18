package com.waremanager;

import com.waremanager.model.Alert;
import com.waremanager.model.AlertStatus;
import com.waremanager.model.AlertType;
import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.Product;
import com.waremanager.model.PurchaseOrder;
import com.waremanager.model.ReorderSuggestion;
import com.waremanager.model.SalesRecord;
import com.waremanager.model.SuggestionStatus;
import com.waremanager.repository.AlertRepository;
import com.waremanager.repository.DemandForecastRepository;
import com.waremanager.repository.InventoryPolicyRepository;
import com.waremanager.repository.ProductRepository;
import com.waremanager.repository.PurchaseOrderRepository;
import com.waremanager.repository.ReorderSuggestionRepository;
import com.waremanager.repository.SalesRecordRepository;
import com.waremanager.service.ForecastService;
import com.waremanager.service.OrderQuantityService;
import com.waremanager.service.PlanningService;
import com.waremanager.service.ProductService;
import com.waremanager.service.SeasonRow;
import com.waremanager.service.SettingsService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for F1-F4: the demo data is loaded at startup and the planning run has
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
    @Autowired ReorderSuggestionRepository suggestions;
    @Autowired SalesRecordRepository sales;
    @Autowired PlanningService planning;
    @Autowired SettingsService settings;
    @Autowired ForecastService forecastService;
    @Autowired OrderQuantityService orderQuantities;
    @Autowired PurchaseOrderRepository purchaseOrders;
    @Autowired AlertRepository alerts;

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
        mvc.perform(get("/dashboard")).andExpect(status().isOk()).andExpect(content().string(containsString("Winter stock plan")));
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(content().string(containsString("Fleece Blanket")));
        mvc.perform(get("/products/new")).andExpect(status().isOk()).andExpect(content().string(containsString("Add product")));
        mvc.perform(get("/products/" + bySku("WM-BLK-01").getId() + "/edit"))
           .andExpect(status().isOk()).andExpect(content().string(containsString("Edit product")));
        mvc.perform(get("/forecast")).andExpect(status().isOk()).andExpect(content().string(containsString("outlook")))
           .andExpect(content().string(containsString("Machine-learning models trained by")));
        mvc.perform(get("/safety-stock")).andExpect(status().isOk()).andExpect(content().string(containsString("Safety stock")));
        mvc.perform(get("/reorder-points")).andExpect(status().isOk()).andExpect(content().string(containsString("Reorder suggestions")));
        mvc.perform(get("/eoq")).andExpect(status().isOk()).andExpect(content().string(containsString("Economic Order Quantity")));
        mvc.perform(get("/settings")).andExpect(status().isOk()).andExpect(content().string(containsString("Planning settings")));
        mvc.perform(get("/lead-times")).andExpect(status().isOk()).andExpect(content().string(containsString("Supplier lead times")))
           .andExpect(content().string(containsString("Prediction model trained by")));
        mvc.perform(get("/stockout-risk")).andExpect(status().isOk()).andExpect(content().string(containsString("Stockout alerts")))
           .andExpect(content().string(containsString("Probability model calculated by")));
        mvc.perform(get("/anomalies")).andExpect(status().isOk()).andExpect(content().string(containsString("Unusual sales weeks")))
           .andExpect(content().string(containsString("Anomaly check calculated by")));
    }

    @Test
    void pagesShowFeatureNamesWithoutFeatureNumbers() throws Exception {
        Pattern featureNumber = Pattern.compile("\\bF\\d{1,2}\\b");   // F1, F2, ... F20 must not appear on any page
        List<String> pages = new ArrayList<>(List.of("/dashboard", "/products", "/products/new", "/forecast", "/safety-stock",
                "/reorder-points", "/eoq", "/lead-times", "/stockout-risk", "/anomalies", "/settings"));
        for (Product product : products.findAll()) {
            pages.add("/products/" + product.getId() + "/edit");
            pages.add("/forecast?productId=" + product.getId());
        }
        for (String page : pages) {
            String html = mvc.perform(get(page)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                    .replaceAll("<input[^>]*type=\"hidden\"[^>]*>", "");   // skip hidden fields such as the random CSRF token
            Matcher found = featureNumber.matcher(html);
            if (found.find()) {
                fail(page + " shows the feature number \"" + found.group() + "\"");
            }
        }
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
    void safetyStockOverrideFlowsIntoTheReorderPoint() throws Exception {
        Product rice = bySku("WM-RCE-04");
        mvc.perform(post("/safety-stock/" + rice.getId() + "/override").with(csrf())
                .param("units", "500").param("reason", "Supplier strike expected"))
           .andExpect(redirectedUrl("/safety-stock"));
        InventoryPolicy policy = policies.findByProduct(rice).orElseThrow();
        assertEquals(500, policy.getEffectiveSafetyStock());
        assertTrue(policy.getReorderPoint() >= 500, "F3 must use the F2 override");

        mvc.perform(post("/safety-stock/" + rice.getId() + "/override").with(csrf()).param("units", "10").param("reason", " "))
           .andExpect(flash().attribute("error", "Please give a reason for the override"));

        mvc.perform(post("/safety-stock/" + rice.getId() + "/override/clear").with(csrf()))
           .andExpect(redirectedUrl("/safety-stock"));
        policy = policies.findByProduct(rice).orElseThrow();
        assertNull(policy.getSafetyStockOverride());
        assertEquals(policy.getSafetyStockCalculated(), policy.getEffectiveSafetyStock());
    }

    @Test
    void settingsChangeRecalculatesEveryPlan() throws Exception {
        Product soap = bySku("WM-SOP-06");
        mvc.perform(post("/settings").with(csrf())
                .param("orderingCost", "1500").param("holdingRatePercent", "20")
                .param("serviceLevelPercent", "99").param("coldDayThreshold", "20").param("defaultStockDays", "60")
                .param("stockoutAlertPercent", "45").param("anomalyThresholdPercent", "70"))
           .andExpect(redirectedUrl("/settings"));
        assertEquals(0.99, policies.findByProduct(soap).orElseThrow().getServiceLevel(), 1e-9);
        assertEquals(45, settings.get().getStockoutAlertPercent(), 1e-9);
        assertEquals(70, settings.get().getAnomalyThresholdPercent(), 1e-9);

        mvc.perform(post("/settings").with(csrf())
                .param("orderingCost", "1500").param("holdingRatePercent", "20")
                .param("serviceLevelPercent", "95").param("coldDayThreshold", "20").param("defaultStockDays", "60")
                .param("stockoutAlertPercent", "30").param("anomalyThresholdPercent", "50"))
           .andExpect(redirectedUrl("/settings"));
        assertEquals(0.95, policies.findByProduct(soap).orElseThrow().getServiceLevel(), 1e-9);
    }

    @Test
    void invalidSettingsAreRejected() throws Exception {
        mvc.perform(post("/settings").with(csrf())
                .param("orderingCost", "-5").param("holdingRatePercent", "20")
                .param("serviceLevelPercent", "95").param("coldDayThreshold", "20").param("defaultStockDays", "60"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Ordering cost must not be negative")));
    }

    // ---------------------------------------------------------------- F3

    @Test
    void reorderSuggestionCanBeOrderedAndReceived() throws Exception {
        Product soap = setStock("WM-SOP-06", 0);
        ReorderSuggestion open = suggestions.findFirstByProductAndStatus(soap, SuggestionStatus.OPEN).orElseThrow();
        assertTrue(open.getQuantity() >= soap.getMinOrderQty());
        InventoryPolicy policy = policies.findByProduct(soap).orElseThrow();
        assertTrue(policy.isBelowReorderPoint());

        LocalDate orderedOn = LocalDate.now().minusDays(9);
        mvc.perform(post("/reorder-points/suggestions/" + open.getId() + "/ordered").with(csrf())
                .param("orderedOn", orderedOn.toString()))
           .andExpect(redirectedUrl("/reorder-points"));
        soap = bySku("WM-SOP-06");
        assertEquals(open.getQuantity(), soap.getQuantityOnOrder());
        PurchaseOrder order = purchaseOrders.findBySuggestion(suggestions.findById(open.getId()).orElseThrow()).orElseThrow();
        assertTrue(order.isOpen());

        // Same-day receipt is rejected: a lead time must be at least 1 day
        mvc.perform(post("/reorder-points/suggestions/" + open.getId() + "/received").with(csrf())
                .param("receivedOn", orderedOn.toString()))
           .andExpect(flash().attributeExists("error"));

        mvc.perform(post("/reorder-points/suggestions/" + open.getId() + "/received").with(csrf())
                .param("receivedOn", LocalDate.now().toString()))
           .andExpect(redirectedUrl("/reorder-points"));
        assertEquals(9, purchaseOrders.findById(order.getId()).orElseThrow().getLeadDays().intValue());
        soap = bySku("WM-SOP-06");
        assertEquals(0, soap.getQuantityOnOrder());
        assertEquals(open.getQuantity(), soap.getQuantity().intValue());
        assertEquals(SuggestionStatus.RECEIVED, suggestions.findById(open.getId()).orElseThrow().getStatus());
    }

    @Test
    void dismissedSuggestionIsNotRecreatedImmediately() throws Exception {
        Product oil = setStock("WM-OIL-05", 0);
        ReorderSuggestion open = suggestions.findFirstByProductAndStatus(oil, SuggestionStatus.OPEN).orElseThrow();

        mvc.perform(post("/reorder-points/suggestions/" + open.getId() + "/dismiss").with(csrf()))
           .andExpect(redirectedUrl("/reorder-points"));
        planning.recalculateAll();

        assertFalse(suggestions.findFirstByProductAndStatus(oil, SuggestionStatus.OPEN).isPresent());
        assertTrue(policies.findByProduct(oil).orElseThrow().getReorderStatus().contains("dismissed"));
    }

    // ---------------------------------------------------------------- F4

    @Test
    void eoqIsRoundedUpToTheSupplierMinimum() {
        for (Product product : products.findAll()) {
            if (product.isSeasonal()) {
                continue;
            }
            InventoryPolicy policy = policies.findByProduct(product).orElseThrow();
            if (policy.getAnnualDemand() == 0) {
                // A product with no sales yet (e.g. one added by another test) has nothing to order
                assertEquals(0, policy.getOrderQuantity(), product.getName());
                continue;
            }
            assertTrue(policy.getOrderQuantity() >= product.getMinOrderQty(), product.getName());
            assertTrue(policy.getOrderQuantity() >= Math.floor(policy.getEoq()), product.getName());
        }
    }

    @Test
    void winterPlanIsNeverWorseThanTheUsualHabit() {
        planning.recalculateAll();
        PlanningSettings current = settings.get();
        List<SeasonRow> rows = orderQuantities.seasonRows(planning.currentOutlook(), current, LocalDate.now());
        assertEquals(3, rows.stream().filter(r -> r.getProduct().getSku().startsWith("WM-")).count());
        for (SeasonRow row : rows) {
            if (row.getPlan().getDefaultStock() >= row.getPlan().getTargetStock()) {
                assertTrue(row.getPlan().getExtraProfit() >= -1, row.getProduct().getName());
            }
        }
    }

    // ---------------------------------------------------------------- F6

    @Test
    void demoSuppliersHaveDeliveriesAndPredictionsFeedPlanning() {
        for (Product product : products.findAll()) {
            if (!product.getSku().startsWith("WM-")) {
                continue;
            }
            assertNotNull(product.getSupplier(), "demo product without supplier: " + product.getName());
            InventoryPolicy policy = policies.findByProduct(product).orElseThrow();
            assertTrue(policy.getLeadTimeSource().startsWith("Predicted"), policy.getLeadTimeSource());
        }
        // Rahman Textiles has been getting slower: predicted lead time is above the manual 12 days
        assertTrue(policies.findByProduct(bySku("WM-BLK-01")).orElseThrow().getLeadTimeDays() > 12.5);
    }

    @Test
    void recordedDeliveriesChangeThePrediction() throws Exception {
        Product jacket = bySku("WM-JKT-02");
        double before = policies.findByProduct(jacket).orElseThrow().getLeadTimeDays();
        for (int i = 3; i >= 1; i--) {
            LocalDate ordered = LocalDate.now().minusDays(30L * i + 30);
            mvc.perform(post("/lead-times/deliveries").with(csrf())
                    .param("productId", jacket.getId().toString())
                    .param("orderedOn", ordered.toString())
                    .param("receivedOn", ordered.plusDays(28).toString())
                    .param("quantity", "40"))
               .andExpect(redirectedUrl("/lead-times"))
               .andExpect(flash().attributeExists("message"));
        }
        assertTrue(policies.findByProduct(jacket).orElseThrow().getLeadTimeDays() > before,
                "three 28-day deliveries must raise the predicted lead time");

        mvc.perform(post("/lead-times/deliveries").with(csrf())
                .param("productId", jacket.getId().toString())
                .param("orderedOn", LocalDate.now().minusDays(2).toString())
                .param("receivedOn", LocalDate.now().plusDays(3).toString())
                .param("quantity", "40"))
           .andExpect(flash().attribute("error", "Received date cannot be in the future"));
    }

    @Test
    void supplierCanBeAddedAndChosenOnAProduct() throws Exception {
        mvc.perform(post("/lead-times/suppliers").with(csrf()).param("name", "Test Supplier Co"))
           .andExpect(flash().attributeExists("message"));
        mvc.perform(post("/lead-times/suppliers").with(csrf()).param("name", "test supplier co"))
           .andExpect(flash().attribute("error", "A supplier with this name already exists"));
    }

    // ---------------------------------------------------------------- F7

    @Test
    void stockoutAlertIsRaisedLinkedAndResolved() {
        Product rice = setStock("WM-RCE-04", 0);
        InventoryPolicy policy = policies.findByProduct(rice).orElseThrow();
        assertTrue(policy.getStockoutProbability() > 0.9);
        Alert alert = alerts.findFirstByAlertTypeAndProductAndStatusIn(AlertType.STOCKOUT_RISK, rice,
                List.of(AlertStatus.OPEN, AlertStatus.INVESTIGATING)).orElseThrow();
        assertNotNull(alert.getSuggestion(), "the alert must link to the F3 reorder suggestion");
        assertEquals(rice.getId(), alert.getSuggestion().getProduct().getId());

        setStock("WM-RCE-04", 260);
        assertEquals(AlertStatus.RESOLVED, alerts.findById(alert.getId()).orElseThrow().getStatus());
        assertTrue(policies.findByProduct(rice).orElseThrow().getStockoutProbability() < 0.05);
    }

    // ---------------------------------------------------------------- F20

    @Test
    void unusualWeeksAreFlaggedAndCanBeExplained() throws Exception {
        Product soap = bySku("WM-SOP-06");
        Alert anomaly = alerts.findTop50ByAlertTypeOrderByCreatedAtDescIdDesc(AlertType.DEMAND_ANOMALY).stream()
                .filter(a -> a.getProduct().getId().equals(soap.getId())).findFirst().orElseThrow();
        assertTrue(anomaly.getMetric() > 50, "soap week should be far above the forecast");

        mvc.perform(post("/anomalies/" + anomaly.getId() + "/explained").with(csrf()).param("note", " "))
           .andExpect(flash().attribute("error", "Please write what explains this week"));
        mvc.perform(post("/anomalies/" + anomaly.getId() + "/explained").with(csrf()).param("note", "Bulk order from a hotel"))
           .andExpect(redirectedUrl("/anomalies"));
        planning.recalculateAll();
        Alert after = alerts.findById(anomaly.getId()).orElseThrow();
        assertEquals(AlertStatus.EXPLAINED, after.getStatus());
        assertEquals("Bulk order from a hotel", after.getNote());
    }

    @Test
    void newSalesFarFromTheForecastRaiseAnAnomaly() throws Exception {
        Product rice = bySku("WM-RCE-04");
        LocalDate twoWeeksAgo = ForecastService.mondayOf(LocalDate.now()).minusWeeks(2);
        mvc.perform(post("/forecast/sales").with(csrf())
                .param("productId", rice.getId().toString())
                .param("week", twoWeeksAgo.toString())
                .param("quantity", "600"))
           .andExpect(flash().attributeExists("message"));
        Alert alert = alerts.findFirstByAlertTypeAndProductAndWeekStart(AlertType.DEMAND_ANOMALY, rice, twoWeeksAgo).orElseThrow();
        assertTrue(alert.isAbove());
        assertEquals(AlertStatus.OPEN, alert.getStatus());
    }

    // ---------------------------------------------------------------- products

    @Test
    void newProductGetsAPlanStraightAway() throws Exception {
        mvc.perform(post("/products").with(csrf())
                .param("name", "Hurricane Lantern").param("sku", "TEST-LAN-01").param("category", "Hardware")
                .param("quantity", "10").param("unitCost", "300").param("sellingPrice", "420")
                .param("clearancePrice", "300").param("leadTimeDays", "5").param("leadTimeStdDays", "1")
                .param("minOrderQty", "10").param("currentOrderQty", "0"))
           .andExpect(redirectedUrl("/products"));
        Product lantern = bySku("TEST-LAN-01");
        InventoryPolicy policy = policies.findByProduct(lantern).orElseThrow();
        assertNotNull(policy.getForecastMethod());   // "No sales history yet..."
        assertEquals(0, policy.getAvgWeeklyDemand(), 1e-9);
    }

    @Test
    void productFormShowsValidationErrors() throws Exception {
        mvc.perform(post("/products").with(csrf())
                .param("name", "").param("sku", "BAD-1").param("category", "X")
                .param("quantity", "-1").param("unitCost", "1").param("sellingPrice", "1")
                .param("clearancePrice", "0").param("leadTimeDays", "5").param("leadTimeStdDays", "1")
                .param("minOrderQty", "1").param("currentOrderQty", "0"))
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
                .param("clearancePrice", "0").param("leadTimeDays", "5").param("leadTimeStdDays", "1")
                .param("minOrderQty", "1").param("currentOrderQty", "0"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Product name must be at most 255 characters")));
        mvc.perform(post("/lead-times/suppliers").with(csrf()).param("name", tooLong))
           .andExpect(flash().attribute("error", "Supplier name must be at most 100 characters"));
        mvc.perform(post("/forecast/events").with(csrf()).param("name", tooLong)
                .param("startDate", LocalDate.now().toString()).param("endDate", LocalDate.now().toString()))
           .andExpect(flash().attribute("error", "Event name must be at most 100 characters"));
        mvc.perform(post("/safety-stock/" + rice.getId() + "/override").with(csrf()).param("units", "10").param("reason", tooLong))
           .andExpect(flash().attribute("error", "Reason must be at most 300 characters"));
        Alert anomaly = alerts.findTop50ByAlertTypeOrderByCreatedAtDescIdDesc(AlertType.DEMAND_ANOMALY).get(0);
        mvc.perform(post("/anomalies/" + anomaly.getId() + "/explained").with(csrf()).param("note", tooLong))
           .andExpect(flash().attribute("error", "Note must be at most 300 characters"));
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
        Long supplierId = product.getSupplier() == null ? null : product.getSupplier().getId();
        productService.update(product.getId(), product, supplierId);
        planning.recalculateAll();
        return bySku(sku);
    }
}

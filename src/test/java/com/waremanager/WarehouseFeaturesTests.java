package com.waremanager;

import com.waremanager.model.*;
import com.waremanager.repository.*;
import com.waremanager.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration tests for Emon's F14–F19 workflows; no external PostgreSQL needed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", roles = "ADMIN")
class WarehouseFeaturesTests {
    @Autowired ProductRepository products;
    @Autowired SupplierRepository suppliers;
    @Autowired ReorderSuggestionRepository suggestions;
    @Autowired ReservationRepository reservations;
    @Autowired ShipmentRepository shipments;
    @Autowired StockService stock;
    @Autowired QualityService quality;
    @Autowired ReservationService orders;
    @Autowired SubstituteService substitutes;
    @Autowired ScarcityService scarcity;
    @Autowired ConsolidationService purchasing;
    @Autowired MockMvc mvc;

    private Product product(String name, int qty) {
        Product p = new Product();
        p.setName(name);
        p.setSku("EMON-" + UUID.randomUUID());
        p.setCategory("Medicine");
        p.setQuantity(qty);
        p.setUnitCost(100);
        return products.saveAndFlush(p);
    }

    @Test
    void reservationsCannotDoubleBookAndCancellationReleasesThem() {
        Product p = product("Reservation test", 7);
        SalesOrder a = orders.newOrder("Customer A", 1);
        orders.addLine(a.getId(), p.getId(), 6, 0);
        orders.confirm(a.getId());
        assertEquals(1, stock.available(p.getId()));
        SalesOrder b = orders.newOrder("Customer B", 2);
        orders.addLine(b.getId(), p.getId(), 4, 0);
        assertThrows(IllegalArgumentException.class, () -> orders.confirm(b.getId()));
        orders.cancel(a.getId());
        orders.confirm(b.getId());
        assertEquals(3, stock.available(p.getId()));
    }

    @Test
    void recallBlocksReservationsAndReportIdentifiesAffectedOrders() {
        Product p = product("Recall test", 0);
        Batch batch = stock.receiveBatch(p.getId(), "B-" + UUID.randomUUID(), "BIN-R", LocalDate.now().plusYears(1), 8);
        SalesOrder order = orders.newOrder("Affected Customer", 2);
        orders.addLine(order.getId(), p.getId(), 5, 0);
        orders.confirm(order.getId());
        assertEquals(3, stock.available(p.getId()));
        RecallCase record = quality.recallBatch(batch.getId(), "Suspected defect");
        assertTrue(batch.isHeld());
        assertEquals(0, stock.available(p.getId()));
        assertEquals(SalesOrder.Status.PARTIAL, order.getStatus());
        assertTrue(quality.impactCsv(record.getId()).contains("Affected Customer"));
        assertEquals(Reservation.Status.BLOCKED, quality.impactReservations(batch.getId()).get(0).getStatus());
    }

    @Test
    void shippedBatchIsStillFoundByRecallImpactReport() {
        Product p = product("Shipment recall", 0);
        Batch batch = stock.receiveBatch(p.getId(), "SHIP-" + UUID.randomUUID(), "BIN-S", LocalDate.now().plusYears(1), 4);
        SalesOrder order = orders.newOrder("Shipped Customer", 2);
        orders.addLine(order.getId(), p.getId(), 3, 0);
        orders.confirm(order.getId());
        orders.ship(order.getId());
        assertEquals(1, batch.getQuantity());
        assertEquals(1, p.getQuantity());
        RecallCase record = quality.recallBatch(batch.getId(), "Trace shipment");
        String report = quality.impactCsv(record.getId());
        assertTrue(report.contains("Shipment"));
        assertTrue(report.contains("Shipped Customer"));
        assertEquals(1, quality.impactShipments(batch.getId()).size());
    }

    @Test
    void coldChainHoldRequiresQualityDecisionAndCanDiscard() {
        Product p = product("Cold medicine", 0);
        p.setMinTemperatureC(2.0);
        p.setMaxTemperatureC(8.0);
        Batch b = stock.receiveBatch(p.getId(), "C-" + UUID.randomUUID(), "COLD-1", LocalDate.now().plusMonths(6), 9);
        assertEquals(1, quality.recordTemperature("COLD-1", 16, LocalDateTime.now()));
        assertTrue(b.isHeld());
        var incident = quality.incidents().stream().filter(x -> x.getBatch().getId().equals(b.getId())).findFirst().orElseThrow();
        quality.review(incident.getId(), "DISCARD", "Inspected; product no longer safe");
        assertEquals(0, b.getQuantity());
        assertEquals(0, p.getQuantity());
        assertEquals(ExcursionIncident.Status.DISCARDED, incident.getStatus());
    }

    @Test
    void substituteMustMatchAllRequiredAttributesAndBeInStock() {
        Product requested = product("Original", 0);
        requested.setKeyAttributes("size=M,color=blue");
        Product good = product("Alternative", 30);
        good.setKeyAttributes("size=M,color=blue,brand=other");
        Product bad = product("Wrong color", 30);
        bad.setKeyAttributes("size=M,color=red");
        SalesOrder order = orders.newOrder("Customer", 2);
        orders.addLine(order.getId(), requested.getId(), 10, 0);
        Long lineId = orders.lines(order.getId()).get(0).getId();
        assertEquals(1, substitutes.alternatives(lineId).size());
        assertThrows(IllegalArgumentException.class, () -> substitutes.accept(lineId, bad.getId()));
        substitutes.accept(lineId, good.getId());
        orders.confirm(order.getId());
        assertEquals(good.getId(), orders.lines(order.getId()).get(0).getProduct().getId());
    }

    @Test
    void scarcityPreviewAndCommitCreatePartialBackorders() {
        Product p = product("Scarce medicine", 5);
        SalesOrder first = orders.newOrder("High priority", 1);
        orders.addLine(first.getId(), p.getId(), 4, 0);
        SalesOrder second = orders.newOrder("Normal priority", 2);
        orders.addLine(second.getId(), p.getId(), 4, 0);
        var preview = scarcity.preview(ScarcityService.Rule.PRIORITY).stream()
            .filter(x -> x.line().getProduct().getId().equals(p.getId())).toList();
        assertEquals(4, preview.get(0).allocated());
        assertEquals(1, preview.get(1).allocated());
        scarcity.commit(ScarcityService.Rule.PRIORITY);
        assertEquals(0, stock.available(p.getId()));
        assertEquals(SalesOrder.Status.CONFIRMED, first.getStatus());
        assertEquals(3, orders.lines(second.getId()).get(0).getBackorderedQuantity());
    }

    @Test
    void consolidatedDraftCreatesNormalPurchaseOrdersOnConfirmation() {
        Supplier s = suppliers.save(new Supplier("Supplier " + UUID.randomUUID()));
        s.setMinOrderValue(800);
        Product p = product("Reorder test", 0);
        p.setSupplier(s);
        ReorderSuggestion suggestion = suggestions.saveAndFlush(new ReorderSuggestion(p, 10, "Below reorder point"));
        ConsolidatedOrder draft = purchasing.createDraft(s.getId());
        assertEquals(1, purchasing.lines(draft.getId()).size());
        purchasing.confirm(draft.getId());
        assertEquals(ConsolidatedOrder.Status.CONFIRMED, draft.getStatus());
        assertEquals(SuggestionStatus.ORDERED, suggestion.getStatus());
        assertEquals(10, p.getQuantityOnOrder());
    }

    @Test
    void everyNewFeaturePageRenders() throws Exception {
        for (String url : new String[]{"/batches", "/recalls", "/cold-chain", "/orders", "/scarcity", "/consolidation"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }
}

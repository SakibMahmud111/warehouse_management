package com.waremanager.service;

import com.waremanager.model.*;
import com.waremanager.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

/** F19: one editable supplier draft grouping F3/F4 suggestions; confirmation uses existing F3/F6 order flow. */
@Service
public class ConsolidationService {
    public record SupplierGroup(Supplier supplier, List<ReorderSuggestion> suggestions, double estimatedValue) {}
    private final SupplierRepository suppliers;
    private final ReorderSuggestionRepository suggestions;
    private final ConsolidatedOrderRepository orders;
    private final ConsolidatedOrderLineRepository lines;
    private final ReorderPointService reorderPoints;

    public ConsolidationService(SupplierRepository suppliers, ReorderSuggestionRepository suggestions,
            ConsolidatedOrderRepository orders, ConsolidatedOrderLineRepository lines,
            ReorderPointService reorderPoints) {
        this.suppliers = suppliers;
        this.suggestions = suggestions;
        this.orders = orders;
        this.lines = lines;
        this.reorderPoints = reorderPoints;
    }

    @Transactional(readOnly = true)
    public List<Supplier> suppliers() { return suppliers.findAll(); }
    @Transactional(readOnly = true)
    public List<ConsolidatedOrder> draftsAndOrders() { return orders.findAllByOrderByCreatedAtDesc(); }
    @Transactional(readOnly = true)
    public List<ConsolidatedOrderLine> lines(Long id) { return lines.findByConsolidatedOrderIdOrderByIdAsc(id); }
    @Transactional(readOnly = true)
    public ConsolidatedOrder order(Long id) { return orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Draft not found")); }

    @Transactional(readOnly = true)
    public List<SupplierGroup> preview() {
        Map<Long, List<ReorderSuggestion>> groups = new LinkedHashMap<>();
        for (ReorderSuggestion suggestion : suggestions.findByStatus(SuggestionStatus.OPEN)) {
            Supplier supplier = suggestion.getProduct().getSupplier();
            if (supplier == null || lines.existsBySuggestionId(suggestion.getId())) continue;
            groups.computeIfAbsent(supplier.getId(), x -> new ArrayList<>()).add(suggestion);
        }
        List<SupplierGroup> result = new ArrayList<>();
        for (var e : groups.entrySet()) {
            Supplier s = suppliers.findById(e.getKey()).orElseThrow();
            double value = e.getValue().stream().mapToDouble(x ->
                Math.max(x.getQuantity(), x.getProduct().getMinOrderQty()) * x.getProduct().getUnitCost()).sum();
            result.add(new SupplierGroup(s, e.getValue(), value));
        }
        return result;
    }

    @Transactional
    public ConsolidatedOrder createDraft(Long supplierId) {
        Supplier supplier = suppliers.findById(supplierId).orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        List<ReorderSuggestion> eligible = suggestions.findByStatus(SuggestionStatus.OPEN).stream()
            .filter(x -> x.getProduct().getSupplier() != null
                && supplierId.equals(x.getProduct().getSupplier().getId())
                && !lines.existsBySuggestionId(x.getId())).toList();
        if (eligible.isEmpty()) throw new IllegalArgumentException("No ungrouped reorder suggestions for this supplier");
        ConsolidatedOrder order = orders.save(new ConsolidatedOrder(supplier));
        for (ReorderSuggestion suggestion : eligible) {
            lines.save(new ConsolidatedOrderLine(order, suggestion,
                Math.max(suggestion.getQuantity(), suggestion.getProduct().getMinOrderQty())));
        }
        return order;
    }

    @Transactional
    public void setQuantity(Long lineId, int quantity) {
        ConsolidatedOrderLine line = lines.findById(lineId).orElseThrow(() -> new IllegalArgumentException("Line not found"));
        if (line.getConsolidatedOrder().getStatus() != ConsolidatedOrder.Status.DRAFT)
            throw new IllegalArgumentException("Confirmed purchase orders cannot be changed");
        if (quantity < line.getProduct().getMinOrderQty() || quantity > 1_000_000)
            throw new IllegalArgumentException("Quantity must meet product minimum and be at most 1,000,000");
        line.setQuantity(quantity);
    }

    @Transactional
    public void setSupplierMinimum(Long supplierId, double minimum) {
        if (!Double.isFinite(minimum) || minimum < 0 || minimum > 1_000_000_000)
            throw new IllegalArgumentException("Enter a valid minimum order value");
        Supplier supplier = suppliers.findById(supplierId).orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        supplier.setMinOrderValue(minimum);
    }

    @Transactional
    public void confirm(Long orderId) {
        ConsolidatedOrder order = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Draft not found"));
        if (order.getStatus() != ConsolidatedOrder.Status.DRAFT)
            throw new IllegalArgumentException("Order is already confirmed");
        List<ConsolidatedOrderLine> items = lines(orderId);
        if (items.isEmpty()) throw new IllegalArgumentException("Order has no lines");
        double value = items.stream().mapToDouble(ConsolidatedOrderLine::getLineValue).sum();
        if (value + 0.00001 < order.getSupplier().getMinOrderValue())
            throw new IllegalArgumentException("Order value is below this supplier's required minimum");
        for (ConsolidatedOrderLine line : items) {
            if (line.getSuggestion().getStatus() != SuggestionStatus.OPEN)
                throw new IllegalArgumentException("A source suggestion is no longer open; recreate the draft");
        }
        for (ConsolidatedOrderLine line : items) {
            line.getSuggestion().setQuantity(line.getQuantity());
            reorderPoints.markOrdered(line.getSuggestion().getId(), LocalDate.now());
        }
        order.setStatus(ConsolidatedOrder.Status.CONFIRMED);
    }
}

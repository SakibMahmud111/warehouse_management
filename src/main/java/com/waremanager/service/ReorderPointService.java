package com.waremanager.service;

import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.Product;
import com.waremanager.model.ReorderPointHistory;
import com.waremanager.model.ReorderSuggestion;
import com.waremanager.model.SuggestionStatus;
import com.waremanager.repository.ReorderPointHistoryRepository;
import com.waremanager.repository.ReorderSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * F3 Reorder-Point Optimization.
 *
 * Reorder point = lead-time demand (F1 forecast for the next LT days) + safety stock (F2).
 * When stock on hand + on order falls to the reorder point, a reorder suggestion is created
 * for the quantity from F4. Winter products follow the winter plan instead, so the system
 * never reorders winter stock that would be left over after winter.
 */
@Service
public class ReorderPointService {

    /** Winter stock suggestions appear this many days before the order-by date. */
    static final int WINTER_ORDER_NOTICE_DAYS = 30;
    /** After the planner dismisses a suggestion, the same product is not suggested again for this many days. */
    static final int DISMISS_QUIET_DAYS = 7;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final ReorderSuggestionRepository suggestions;
    private final ReorderPointHistoryRepository history;
    private final LeadTimeService leadTimes;

    public ReorderPointService(ReorderSuggestionRepository suggestions, ReorderPointHistoryRepository history,
                               LeadTimeService leadTimes) {
        this.suggestions = suggestions;
        this.history = history;
        this.leadTimes = leadTimes;
    }

    /** Planning run, step F3 (after F2 and F4). Updates the policy, the suggestion and the history. */
    public void calculate(InventoryPolicy policy, Product product, LocalDate today) {
        int reorderPoint = (int) Math.ceil(policy.getLeadTimeDemand() + policy.getEffectiveSafetyStock());
        int position = product.getInventoryPosition();
        boolean noDemand = reorderPoint == 0 && policy.getAvgWeeklyDemand() == 0;
        boolean below = !noDemand && position <= reorderPoint;
        policy.setReorderPoint(reorderPoint);
        policy.setBelowReorderPoint(below);

        Integer suggestQuantity = null;
        String reason = null;
        String status;

        if (noDemand) {
            status = "No forecast demand yet (record weekly sales in F1)";
        } else if (product.isSeasonal()) {
            int order = policy.getOrderQuantity();          // from the winter plan (F4)
            LocalDate orderBy = policy.getOrderByDate();
            boolean orderSoon = orderBy != null && !orderBy.isAfter(today.plusDays(WINTER_ORDER_NOTICE_DAYS));
            if (order > 0 && (below || orderSoon)) {
                suggestQuantity = order;
                reason = "Winter stock: order by " + orderBy.format(DAY)
                        + " so it arrives before the cold days (winter plan, F4).";
                status = "Order " + order + " by " + orderBy.format(DAY);
            } else if (order > 0) {
                status = "Planned: order " + order + " by " + orderBy.format(DAY);
            } else if (below) {
                status = "Below reorder point, but current stock covers the rest of the winter: do not reorder";
            } else {
                status = "OK";
            }
        } else if (below) {
            // Order the F4 quantity, or enough to get back above the reorder point if that is more.
            int needed = reorderPoint - position + 1;
            suggestQuantity = InventoryMath.roundUpToMinimum(Math.max(policy.getOrderQuantity(), needed),
                    product.getMinOrderQty());
            reason = "Stock + on order (" + position + ") is at or below the reorder point (" + reorderPoint + ").";
            status = "Reorder now";
        } else {
            status = "OK";
        }
        // Respect the planner's decision: no new suggestion for 7 days after a dismissal.
        ReorderSuggestion open = suggestions.findFirstByProductAndStatus(product, SuggestionStatus.OPEN).orElse(null);
        ReorderSuggestion latest = suggestions.findFirstByProductOrderByCreatedAtDescIdDesc(product).orElse(null);
        if (suggestQuantity != null && open == null && latest != null
                && latest.getStatus() == SuggestionStatus.DISMISSED
                && latest.getUpdatedAt().toLocalDate().isAfter(today.minusDays(DISMISS_QUIET_DAYS))) {
            suggestQuantity = null;
            status = status + " (suggestion dismissed on " + latest.getUpdatedAt().toLocalDate().format(DAY) + ")";
        }
        policy.setReorderStatus(status);

        // Keep at most one open suggestion per product.
        if (suggestQuantity != null) {
            if (open == null) {
                suggestions.save(new ReorderSuggestion(product, suggestQuantity, reason));
            } else {
                open.setQuantity(suggestQuantity);
                open.setReason(reason);
            }
        } else if (open != null) {
            open.setStatus(SuggestionStatus.CLOSED);
        }

        // History: save a row only when the numbers changed.
        ReorderPointHistory last = history.findFirstByProductOrderByRecordedAtDescIdDesc(product).orElse(null);
        int safetyStock = policy.getEffectiveSafetyStock();
        if (last == null || last.getReorderPoint() != reorderPoint || last.getSafetyStock() != safetyStock) {
            history.save(new ReorderPointHistory(product, reorderPoint, safetyStock, policy.getLeadTimeDemand()));
        }
    }

    /**
     * The planner placed the order on {@code orderedOn}: the units now count as "on order",
     * and a purchase order is recorded with the lead time F6 predicts.
     */
    @Transactional
    public void markOrdered(Long suggestionId, LocalDate orderedOn) {
        ReorderSuggestion suggestion = find(suggestionId);
        if (suggestion.getStatus() != SuggestionStatus.OPEN) {
            throw new IllegalArgumentException("Only open suggestions can be marked as ordered");
        }
        if (orderedOn == null || orderedOn.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The order date cannot be in the future");
        }
        Product product = suggestion.getProduct();
        product.setQuantityOnOrder(product.getQuantityOnOrder() + suggestion.getQuantity());
        suggestion.setStatus(SuggestionStatus.ORDERED);
        leadTimes.placeOrder(product, suggestion.getQuantity(), orderedOn, suggestion);
    }

    /**
     * The goods arrived on {@code receivedOn}: move the units from "on order" into stock.
     * The real lead time is saved on the purchase order and teaches F6.
     */
    @Transactional
    public void markReceived(Long suggestionId, LocalDate receivedOn) {
        ReorderSuggestion suggestion = find(suggestionId);
        if (suggestion.getStatus() != SuggestionStatus.ORDERED) {
            throw new IllegalArgumentException("Only ordered suggestions can be marked as received");
        }
        // Orders placed before F6 existed have no purchase order; they simply update the stock.
        leadTimes.orderFor(suggestion).ifPresent(order -> leadTimes.receive(order, receivedOn));
        Product product = suggestion.getProduct();
        product.setQuantityOnOrder(Math.max(0, product.getQuantityOnOrder() - suggestion.getQuantity()));
        product.setQuantity(product.getQuantity() + suggestion.getQuantity());
        suggestion.setStatus(SuggestionStatus.RECEIVED);
    }

    @Transactional
    public void dismiss(Long suggestionId) {
        ReorderSuggestion suggestion = find(suggestionId);
        if (suggestion.getStatus() != SuggestionStatus.OPEN) {
            throw new IllegalArgumentException("Only open suggestions can be dismissed");
        }
        suggestion.setStatus(SuggestionStatus.DISMISSED);
    }

    /** The 30 most recent suggestions, newest first. */
    @Transactional(readOnly = true)
    public List<ReorderSuggestion> recentSuggestions() {
        return suggestions.findTop30ByOrderByCreatedAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public long openSuggestionCount() {
        return suggestions.countByStatus(SuggestionStatus.OPEN);
    }

    /** The last 5 saved reorder points of a product, newest first. */
    @Transactional(readOnly = true)
    public List<ReorderPointHistory> historyFor(Product product) {
        return history.findTop5ByProductOrderByRecordedAtDescIdDesc(product);
    }

    private ReorderSuggestion find(Long id) {
        return suggestions.findById(id).orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));
    }
}

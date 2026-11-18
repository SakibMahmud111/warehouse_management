package com.waremanager.controller;

import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.PlanningSettings;
import com.waremanager.model.ReorderPointHistory;
import com.waremanager.service.OrderQuantityService;
import com.waremanager.service.PlanningService;
import com.waremanager.service.ReorderPointService;
import com.waremanager.service.SafetyStockService;
import com.waremanager.service.SettingsService;
import com.waremanager.service.WinterOutlook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F2 Safety stock (safety-stock.html), F3 Reorder points (reorder-points.html)
 * and F4 Order quantities (eoq.html). Every change runs the planning again, so the pages agree.
 */
@Controller
public class ReplenishmentController {

    private final SafetyStockService safetyStock;
    private final ReorderPointService reorderPoints;
    private final OrderQuantityService orderQuantities;
    private final SettingsService settings;
    private final PlanningService planning;

    public ReplenishmentController(SafetyStockService safetyStock, ReorderPointService reorderPoints,
                                   OrderQuantityService orderQuantities, SettingsService settings,
                                   PlanningService planning) {
        this.safetyStock = safetyStock;
        this.reorderPoints = reorderPoints;
        this.orderQuantities = orderQuantities;
        this.settings = settings;
        this.planning = planning;
    }

    // ---------------------------------------------------------------- F2

    @GetMapping("/safety-stock")
    public String safetyStockPage(Model model) {
        model.addAttribute("policies", safetyStock.allPolicies());
        model.addAttribute("settings", settings.get());
        return "safety-stock";
    }

    @PostMapping("/safety-stock/{productId}/override")
    public String setOverride(@PathVariable Long productId, @RequestParam(required = false) Integer units,
                              @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        try {
            safetyStock.setOverride(productId, units, reason);
            planning.recalculateAll();
            redirect.addFlashAttribute("message", "Override saved. Reorder points were recalculated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/safety-stock";
    }

    @PostMapping("/safety-stock/{productId}/override/clear")
    public String clearOverride(@PathVariable Long productId, RedirectAttributes redirect) {
        try {
            safetyStock.clearOverride(productId);
            planning.recalculateAll();
            redirect.addFlashAttribute("message", "Override removed: the calculated safety stock is used again.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/safety-stock";
    }

    @PostMapping("/safety-stock/recalculate")
    public String recalculateSafetyStock(RedirectAttributes redirect) {
        planning.recalculateAll();
        redirect.addFlashAttribute("message", "Safety stock recalculated.");
        return "redirect:/safety-stock";
    }

    // ---------------------------------------------------------------- F3

    @GetMapping("/reorder-points")
    public String reorderPointsPage(Model model) {
        List<InventoryPolicy> policies = safetyStock.allPolicies();
        Map<Long, List<ReorderPointHistory>> history = new HashMap<>();
        for (InventoryPolicy policy : policies) {
            history.put(policy.getProduct().getId(), reorderPoints.historyFor(policy.getProduct()));
        }
        model.addAttribute("policies", policies);
        model.addAttribute("history", history);
        model.addAttribute("suggestions", reorderPoints.recentSuggestions());
        model.addAttribute("today", LocalDate.now());
        return "reorder-points";
    }

    /** {@code orderedOn} defaults to today; a past date can be chosen if the order was placed earlier. */
    @PostMapping("/reorder-points/suggestions/{id}/ordered")
    public String markOrdered(@PathVariable Long id,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedOn,
                              RedirectAttributes redirect) {
        LocalDate date = orderedOn == null ? LocalDate.now() : orderedOn;
        return suggestionAction(() -> reorderPoints.markOrdered(id, date),
                "Marked as ordered: the units now count as on order, and a purchase order was recorded.", redirect);
    }

    /** {@code receivedOn} defaults to today and must be at least 1 day after the order date. */
    @PostMapping("/reorder-points/suggestions/{id}/received")
    public String markReceived(@PathVariable Long id,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedOn,
                               RedirectAttributes redirect) {
        LocalDate date = receivedOn == null ? LocalDate.now() : receivedOn;
        return suggestionAction(() -> reorderPoints.markReceived(id, date),
                "Marked as received: the units were added to stock and the real lead time was learned.", redirect);
    }

    @PostMapping("/reorder-points/suggestions/{id}/dismiss")
    public String dismiss(@PathVariable Long id, RedirectAttributes redirect) {
        return suggestionAction(() -> reorderPoints.dismiss(id), "Suggestion dismissed. It will not be suggested again for 7 days.", redirect);
    }

    @PostMapping("/reorder-points/recalculate")
    public String recalculateReorderPoints(RedirectAttributes redirect) {
        planning.recalculateAll();
        redirect.addFlashAttribute("message", "Reorder points recalculated.");
        return "redirect:/reorder-points";
    }

    private String suggestionAction(Runnable action, String success, RedirectAttributes redirect) {
        try {
            action.run();
            planning.recalculateAll();
            redirect.addFlashAttribute("message", success);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/reorder-points";
    }

    // ---------------------------------------------------------------- F4

    @GetMapping("/eoq")
    public String eoqPage(Model model) {
        PlanningSettings current = settings.get();
        WinterOutlook outlook = planning.currentOutlook();
        model.addAttribute("settings", current);
        model.addAttribute("outlook", outlook);
        model.addAttribute("eoqRows", orderQuantities.eoqRows(current));
        model.addAttribute("seasonRows", orderQuantities.seasonRows(outlook, current, LocalDate.now()));
        return "eoq";
    }

    @PostMapping("/eoq/recalculate")
    public String recalculateEoq(RedirectAttributes redirect) {
        planning.recalculateAll();
        redirect.addFlashAttribute("message", "Order quantities recalculated.");
        return "redirect:/eoq";
    }
}

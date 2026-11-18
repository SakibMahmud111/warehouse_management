package com.waremanager.controller;

import com.waremanager.service.PlanningService;
import com.waremanager.service.SafetyStockService;
import com.waremanager.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** F2 Dynamic safety stock (safety-stock.html). Every change runs the planning again, so the numbers stay current. */
@Controller
public class SafetyStockController {

    private final SafetyStockService safetyStock;
    private final SettingsService settings;
    private final PlanningService planning;

    public SafetyStockController(SafetyStockService safetyStock, SettingsService settings, PlanningService planning) {
        this.safetyStock = safetyStock;
        this.settings = settings;
        this.planning = planning;
    }

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
            redirect.addFlashAttribute("message", "Override saved: this safety stock is now used.");
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
}

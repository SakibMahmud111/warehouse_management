package com.waremanager.controller;

import com.waremanager.service.PlanningService;
import com.waremanager.service.RiskService;
import com.waremanager.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** F7 Stockout Probability Estimation page (view: stockout-risk.html). */
@Controller
@RequestMapping("/stockout-risk")
public class RiskController {

    private final RiskService risks;
    private final SettingsService settings;
    private final PlanningService planning;

    public RiskController(RiskService risks, SettingsService settings, PlanningService planning) {
        this.risks = risks;
        this.settings = settings;
        this.planning = planning;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("ranked", risks.ranked());
        model.addAttribute("alerts", risks.recentAlerts());
        model.addAttribute("settings", settings.get());
        return "stockout-risk";
    }

    @PostMapping("/recalculate")
    public String recalculate(RedirectAttributes redirect) {
        planning.recalculateAll();
        redirect.addFlashAttribute("message", "Stockout risks recalculated.");
        return "redirect:/stockout-risk";
    }
}

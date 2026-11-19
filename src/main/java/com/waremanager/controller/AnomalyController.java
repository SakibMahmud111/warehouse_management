package com.waremanager.controller;

import com.waremanager.service.AnomalyService;
import com.waremanager.service.PlanningService;
import com.waremanager.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** F20 Demand Anomaly Detection page (view: anomalies.html). Item 23 in the original idea list. */
@Controller
@RequestMapping("/anomalies")
public class AnomalyController {

    private final AnomalyService anomalies;
    private final SettingsService settings;
    private final PlanningService planning;

    public AnomalyController(AnomalyService anomalies, SettingsService settings, PlanningService planning) {
        this.anomalies = anomalies;
        this.settings = settings;
        this.planning = planning;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("alerts", anomalies.recentAnomalies());
        model.addAttribute("settings", settings.get());
        return "anomalies";
    }

    @PostMapping("/{id}/explained")
    public String explained(@PathVariable Long id, @RequestParam(required = false) String note,
                            RedirectAttributes redirect) {
        try {
            anomalies.markExplained(id, note);
            redirect.addFlashAttribute("message", "Marked as explained.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/anomalies";
    }

    @PostMapping("/{id}/investigating")
    public String investigating(@PathVariable Long id, @RequestParam(required = false) String note,
                                RedirectAttributes redirect) {
        try {
            anomalies.markInvestigating(id, note);
            redirect.addFlashAttribute("message", "Marked as being investigated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/anomalies";
    }

    @PostMapping("/recalculate")
    public String recalculate(RedirectAttributes redirect) {
        planning.recalculateAll();
        redirect.addFlashAttribute("message", "Anomaly check run again.");
        return "redirect:/anomalies";
    }
}

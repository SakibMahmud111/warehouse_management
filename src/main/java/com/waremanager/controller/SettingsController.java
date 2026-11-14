package com.waremanager.controller;

import com.waremanager.model.PlanningSettings;
import com.waremanager.service.PlanningService;
import com.waremanager.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Planning settings used by F1-F4 (view: settings.html). */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settings;
    private final PlanningService planning;

    public SettingsController(SettingsService settings, PlanningService planning) {
        this.settings = settings;
        this.planning = planning;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("settings", settings.get());
        return "settings";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("settings") PlanningSettings form, BindingResult errors,
                       RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return "settings";
        }
        settings.update(form);
        planning.recalculateAll();
        redirect.addFlashAttribute("message", "Settings saved and all plans recalculated.");
        return "redirect:/settings";
    }
}

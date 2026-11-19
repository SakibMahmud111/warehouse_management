package com.waremanager.controller;

import com.waremanager.service.StockHealthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StockHealthController {

    private final StockHealthService service;

    public StockHealthController(StockHealthService service) { this.service = service; }

    @GetMapping("/stock-health")
    public String page(@RequestParam(defaultValue = "90") int deadDays,
                       @RequestParam(defaultValue = "120") int coverDays, Model model) {
        if (deadDays < 1 || coverDays < 1) {            // validation: fall back to defaults
            deadDays = StockHealthService.DEFAULT_DEAD_DAYS;
            coverDays = StockHealthService.DEFAULT_COVER_DAYS;
            model.addAttribute("error", "Thresholds must be at least 1 day; defaults used.");   // ADJUST name
        }
        model.addAttribute("rows", service.analyse(deadDays, coverDays));
        model.addAttribute("deadDays", deadDays);
        model.addAttribute("coverDays", coverDays);
        return "stock-health";
    }
}
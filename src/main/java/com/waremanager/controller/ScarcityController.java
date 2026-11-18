package com.waremanager.controller;

import com.waremanager.service.ScarcityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/scarcity")
public class ScarcityController {
    private final ScarcityService scarcity;
    public ScarcityController(ScarcityService scarcity) { this.scarcity = scarcity; }

    @GetMapping
    public String preview(@RequestParam(defaultValue = "PRIORITY") ScarcityService.Rule rule, Model model) {
        model.addAttribute("rule", rule);
        model.addAttribute("decisions", scarcity.preview(rule));
        return "scarcity";
    }

    @PostMapping
    public String commit(@RequestParam ScarcityService.Rule rule, RedirectAttributes flash) {
        try {
            int allocated = scarcity.commit(rule);
            flash.addFlashAttribute("message", allocated + " units reserved; backorders kept for the rest.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/scarcity?rule=" + rule;
    }
}

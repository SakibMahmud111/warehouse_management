package com.waremanager.controller;

import com.waremanager.service.ConsolidationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/consolidation")
public class ConsolidationController {
    private final ConsolidationService purchasing;
    public ConsolidationController(ConsolidationService purchasing) { this.purchasing = purchasing; }

    @GetMapping
    public String page(@RequestParam(required = false) Long id, Model model) {
        model.addAttribute("groups", purchasing.preview());
        model.addAttribute("suppliers", purchasing.suppliers());
        model.addAttribute("orders", purchasing.draftsAndOrders());
        if (id != null) {
            model.addAttribute("chosenId", id);
            model.addAttribute("chosenOrder", purchasing.order(id));
            model.addAttribute("chosenLines", purchasing.lines(id));
        }
        return "consolidation";
    }

    @PostMapping("/drafts")
    public String draft(@RequestParam Long supplierId, RedirectAttributes flash) {
        try {
            Long id = purchasing.createDraft(supplierId).getId();
            flash.addFlashAttribute("message", "Consolidated supplier draft created.");
            return "redirect:/consolidation?id=" + id;
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/consolidation";
        }
    }

    @PostMapping("/lines/{id}")
    public String quantity(@PathVariable Long id, @RequestParam int quantity,
            @RequestParam Long orderId, RedirectAttributes flash) {
        try {
            purchasing.setQuantity(id, quantity);
            flash.addFlashAttribute("message", "Draft quantity updated.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/consolidation?id=" + orderId;
    }

    @PostMapping("/suppliers/{id}")
    public String minimum(@PathVariable Long id, @RequestParam double minOrderValue, RedirectAttributes flash) {
        try {
            purchasing.setSupplierMinimum(id, minOrderValue);
            flash.addFlashAttribute("message", "Supplier minimum order value updated.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/consolidation";
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes flash) {
        try {
            purchasing.confirm(id);
            flash.addFlashAttribute("message", "Consolidated order confirmed; purchase orders placed through F3/F6.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/consolidation?id=" + id;
    }
}

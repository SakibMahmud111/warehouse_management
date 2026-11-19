// F5
package com.waremanager.controller;

import com.waremanager.service.SupplierScoreService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SupplierScoreController {

    private final SupplierScoreService service;

    public SupplierScoreController(SupplierScoreService service) { this.service = service; }

    @GetMapping("/supplier-scores")
    public String ranking(Model model) {
        model.addAttribute("rows", service.ranking());
        return "supplier-scores";
    }

    @GetMapping("/supplier-scores/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("supplier", service.supplier(id));
        model.addAttribute("orders", service.receiptsOf(id));
        return "supplier-scores-detail";
    }

    @PostMapping("/supplier-scores/{id}/receipts/{orderId}")
    public String record(@PathVariable Long id, @PathVariable Long orderId,
                         @RequestParam int receivedQuantity, @RequestParam int defectiveQuantity,
                         RedirectAttributes redirect) {
        try {
            service.recordReceipt(orderId, receivedQuantity, defectiveQuantity);
            redirect.addFlashAttribute("message", "Receipt saved.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/supplier-scores/" + id;
    }
}
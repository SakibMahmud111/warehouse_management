package com.waremanager.controller;

import com.waremanager.service.LeadTimeService;
import com.waremanager.service.PlanningService;
import com.waremanager.service.ProductService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/** F6 Supplier Lead-Time Prediction page (view: lead-times.html). */
@Controller
@RequestMapping("/lead-times")
public class LeadTimeController {

    private final LeadTimeService leadTimes;
    private final ProductService products;
    private final PlanningService planning;

    public LeadTimeController(LeadTimeService leadTimes, ProductService products, PlanningService planning) {
        this.leadTimes = leadTimes;
        this.products = products;
        this.planning = planning;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("rows", leadTimes.rows());
        model.addAttribute("openOrders", leadTimes.openOrders());
        model.addAttribute("receipts", leadTimes.recentReceipts());
        model.addAttribute("products", products.all());
        model.addAttribute("today", LocalDate.now());
        return "lead-times";
    }

    @PostMapping("/deliveries")
    public String recordDelivery(@RequestParam Long productId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedOn,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedOn,
                                 @RequestParam int quantity,
                                 RedirectAttributes redirect) {
        try {
            leadTimes.recordPastDelivery(productId, orderedOn, receivedOn, quantity);
            planning.recalculateAll();
            redirect.addFlashAttribute("message", "Delivery recorded. Lead-time predictions and all plans were updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/lead-times";
    }

    @PostMapping("/suppliers")
    public String addSupplier(@RequestParam String name, RedirectAttributes redirect) {
        try {
            leadTimes.addSupplier(name);
            redirect.addFlashAttribute("message", "Supplier added. Choose it on a product's edit page.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/lead-times";
    }

    @PostMapping("/recalculate")
    public String recalculate(RedirectAttributes redirect) {
        planning.recalculateAll();
        redirect.addFlashAttribute("message", "Lead times and all plans recalculated.");
        return "redirect:/lead-times";
    }
}

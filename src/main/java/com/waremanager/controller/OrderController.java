package com.waremanager.controller;

import com.waremanager.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderController {
    private final ReservationService orders;
    private final ProductService products;
    private final SubstituteService substitutes;

    public OrderController(ReservationService orders, ProductService products, SubstituteService substitutes) {
        this.orders = orders;
        this.products = products;
        this.substitutes = substitutes;
    }

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) Long id, Model model) {
        model.addAttribute("orders", orders.orders());
        model.addAttribute("products", products.all());
        if (id != null) {
            model.addAttribute("selected", orders.findOrder(id));
            model.addAttribute("lines", orders.lines(id));
            model.addAttribute("allocations", orders.allocations(id));
        }
        return "orders";
    }

    @PostMapping("/orders")
    public String create(@RequestParam String customerName, @RequestParam int priority, RedirectAttributes flash) {
        try {
            Long id = orders.newOrder(customerName, priority).getId();
            flash.addFlashAttribute("message", "Draft sales order created.");
            return "redirect:/orders?id=" + id;
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders";
        }
    }

    @PostMapping("/orders/{id}/lines")
    public String addLine(@PathVariable Long id, @RequestParam Long productId,
            @RequestParam int quantity, @RequestParam(defaultValue = "0") int minShelfLifeDays,
            RedirectAttributes flash) {
        try {
            orders.addLine(id, productId, quantity, minShelfLifeDays);
            flash.addFlashAttribute("message", "Order line added.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/orders?id=" + id;
    }

    @PostMapping("/orders/{id}/{operation}")
    public String change(@PathVariable Long id, @PathVariable String operation, RedirectAttributes flash) {
        try {
            switch (operation) {
                case "confirm" -> orders.confirm(id);
                case "ship" -> orders.ship(id);
                case "cancel" -> orders.cancel(id);
                default -> throw new IllegalArgumentException("Unknown order action");
            }
            flash.addFlashAttribute("message", "Order action completed: " + operation);
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/orders?id=" + id;
    }

    @GetMapping("/substitutes")
    public String alternatives(@RequestParam Long lineId, Model model) {
        model.addAttribute("line", substitutes.line(lineId));
        model.addAttribute("alternatives", substitutes.alternatives(lineId));
        return "substitutes";
    }

    @PostMapping("/substitutes")
    public String accept(@RequestParam Long lineId, @RequestParam Long productId, RedirectAttributes flash) {
        try {
            substitutes.accept(lineId, productId);
            flash.addFlashAttribute("message", "Selected substitute added to the order line.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/orders?id=" + substitutes.line(lineId).getSalesOrder().getId();
    }
}

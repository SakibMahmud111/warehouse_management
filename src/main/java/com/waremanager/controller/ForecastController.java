package com.waremanager.controller;

import com.waremanager.model.Product;
import com.waremanager.service.ForecastService;
import com.waremanager.service.PlanningService;
import com.waremanager.service.ProductForecast;
import com.waremanager.service.ProductService;
import com.waremanager.service.WinterOutlook;
import com.waremanager.service.WinterService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/** F1 Demand Forecasting page (view: forecast.html). */
@Controller
@RequestMapping("/forecast")
public class ForecastController {

    private final ProductService products;
    private final ForecastService forecasts;
    private final WinterService winter;
    private final PlanningService planning;

    public ForecastController(ProductService products, ForecastService forecasts, WinterService winter,
                              PlanningService planning) {
        this.products = products;
        this.forecasts = forecasts;
        this.winter = winter;
        this.planning = planning;
    }

    @GetMapping
    public String page(@RequestParam(required = false) Long productId, Model model) {
        WinterOutlook outlook = planning.currentOutlook();
        List<Product> all = products.all();
        model.addAttribute("outlook", outlook);
        model.addAttribute("products", all);
        model.addAttribute("events", forecasts.allEvents());
        model.addAttribute("recentReadings", winter.recentReadings());
        model.addAttribute("today", LocalDate.now());

        if (!all.isEmpty()) {
            Product selected = all.get(0);
            for (Product product : all) {
                if (product.getId().equals(productId)) {
                    selected = product;
                }
            }
            ProductForecast data = forecasts.productForecast(selected, outlook);
            model.addAttribute("selected", selected);
            model.addAttribute("data", data);
            model.addAttribute("chart", new ForecastChart(data));
        }
        return "forecast";
    }

    @PostMapping("/recalculate")
    public String recalculate(@RequestParam(required = false) Long productId, RedirectAttributes redirect) {
        int count = planning.recalculateAll();
        redirect.addFlashAttribute("message", "Forecasts recalculated for " + count + " products.");
        return backTo(productId);
    }

    @PostMapping("/temperatures")
    public String addTemperature(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam double averageC,
                                 @RequestParam(required = false) Long productId,
                                 RedirectAttributes redirect) {
        try {
            winter.saveReading(date, averageC);
            planning.recalculateAll();
            redirect.addFlashAttribute("message", "Temperature saved and forecasts updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return backTo(productId);
    }

    @PostMapping("/sales")
    public String addSales(@RequestParam Long productId,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                           @RequestParam int quantity,
                           RedirectAttributes redirect) {
        try {
            forecasts.recordWeeklySales(productId, week, quantity);
            planning.recalculateAll();
            redirect.addFlashAttribute("message", "Weekly sales saved and forecasts updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return backTo(productId);
    }

    @PostMapping("/events")
    public String addEvent(@RequestParam String name,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                           @RequestParam(required = false) Long productId,
                           RedirectAttributes redirect) {
        try {
            forecasts.addEvent(name, startDate, endDate);
            planning.recalculateAll();
            redirect.addFlashAttribute("message", "Event added and forecasts updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return backTo(productId);
    }

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id, @RequestParam(required = false) Long productId,
                              RedirectAttributes redirect) {
        forecasts.deleteEvent(id);
        planning.recalculateAll();
        redirect.addFlashAttribute("message", "Event removed and forecasts updated.");
        return backTo(productId);
    }

    private static String backTo(Long productId) {
        return productId == null ? "redirect:/forecast" : "redirect:/forecast?productId=" + productId;
    }
}

package com.waremanager.controller;

import com.waremanager.model.RecallCase;
import com.waremanager.service.ProductService;
import com.waremanager.service.QualityService;
import com.waremanager.service.StockService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import org.springframework.format.annotation.DateTimeFormat;

@Controller
public class QualityController {
    private final StockService stock;
    private final QualityService quality;
    private final ProductService products;

    public QualityController(StockService stock, QualityService quality, ProductService products) {
        this.stock = stock;
        this.quality = quality;
        this.products = products;
    }

    @GetMapping("/batches")
    public String batches(Model model) {
        model.addAttribute("batches", stock.batches());
        model.addAttribute("products", products.all());
        return "batches";
    }

    @PostMapping("/batches")
    public String receive(@RequestParam Long productId, @RequestParam String batchNumber,
            @RequestParam String binCode, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam int quantity, RedirectAttributes flash) {
        try {
            stock.receiveBatch(productId, batchNumber, binCode, expiryDate, quantity);
            flash.addFlashAttribute("message", "Batch received; product stock updated.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/batches";
    }

    @GetMapping("/recalls")
    public String recalls(@RequestParam(required = false) Long id, Model model) {
        model.addAttribute("batches", stock.batches());
        model.addAttribute("recalls", quality.recalls());
        if (id != null) {
            RecallCase chosen = quality.recall(id);
            model.addAttribute("chosen", chosen);
            model.addAttribute("reservations", quality.impactReservations(chosen.getBatch().getId()));
            model.addAttribute("shipments", quality.impactShipments(chosen.getBatch().getId()));
        }
        return "recalls";
    }

    @PostMapping("/recalls")
    public String openRecall(@RequestParam Long batchId, @RequestParam String reason, RedirectAttributes flash) {
        try {
            RecallCase opened = quality.recallBatch(batchId, reason);
            flash.addFlashAttribute("message", "Recall opened; remaining stock held and reservations blocked.");
            return "redirect:/recalls?id=" + opened.getId();
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/recalls";
        }
    }

    @PostMapping("/recalls/{id}/close")
    public String closeRecall(@PathVariable Long id, RedirectAttributes flash) {
        try {
            quality.closeRecall(id);
            flash.addFlashAttribute("message", "Recall closed; batch released only if no other hold remains.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/recalls?id=" + id;
    }

    @GetMapping(value = "/recalls/{id}/report.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        byte[] data = quality.impactCsv(id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=recall-" + id + ".csv")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8")).body(data);
    }

    @GetMapping("/cold-chain")
    public String coldChain(Model model) {
        model.addAttribute("readings", quality.readings());
        model.addAttribute("incidents", quality.incidents());
        model.addAttribute("batches", stock.batches());
        return "cold-chain";
    }

    @PostMapping("/cold-chain/readings")
    public String recordReading(@RequestParam String binCode, @RequestParam double celsius, RedirectAttributes flash) {
        try {
            int affected = quality.recordTemperature(binCode, celsius, LocalDateTime.now());
            flash.addFlashAttribute("message", "Reading saved; " + affected + " affected batch(es) placed on hold.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/cold-chain";
    }

    @PostMapping("/cold-chain/incidents/{id}/review")
    public String review(@PathVariable Long id, @RequestParam String decision,
            @RequestParam String reason, RedirectAttributes flash) {
        try {
            quality.review(id, decision, reason);
            flash.addFlashAttribute("message", "Quality decision recorded.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/cold-chain";
    }
}

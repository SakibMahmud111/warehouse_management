package com.waremanager.controller;

import com.waremanager.model.Product;
import com.waremanager.service.PlanningService;
import com.waremanager.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;
    private final PlanningService planning;

    public ProductController(ProductService service, PlanningService planning) {
        this.service = service;
        this.planning = planning;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", service.all());
        return "products";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("product", new Product());
        return showForm(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("product") Product product, BindingResult errors, Model model,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return showForm(model, null);
        }
        try {
            service.add(product);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return showForm(model, null);
        }
        planning.recalculateAll();   // give the new product its forecast (F1) and safety stock (F2)
        redirect.addFlashAttribute("message", "Product added.");
        return "redirect:/products";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("product", service.find(id));
        return showForm(model, id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("product") Product product,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return showForm(model, id);
        }
        try {
            service.update(id, product);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return showForm(model, id);
        }
        planning.recalculateAll();
        redirect.addFlashAttribute("message", "Product updated and plans recalculated.");
        return "redirect:/products";
    }

    /** Same form for adding and editing; only the title and the form's target URL differ. */
    private String showForm(Model model, Long id) {
        model.addAttribute("formTitle", id == null ? "Add product" : "Edit product");
        model.addAttribute("formAction", id == null ? "/products" : "/products/" + id);
        return "product-form";
    }
}

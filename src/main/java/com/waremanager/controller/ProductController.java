package com.waremanager.controller;
import com.waremanager.model.Product;
import com.waremanager.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
@Controller
@RequestMapping("/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service){this.service=service;}
    @GetMapping public String list(Model model){model.addAttribute("products",service.all());return "products";}
    @GetMapping("/new") public String createForm(Model model){model.addAttribute("product",new Product());return "product-form";}
    @PostMapping public String create(@Valid @ModelAttribute("product") Product product, BindingResult errors, Model model){
        if(errors.hasErrors())return "product-form";
        try {service.add(product);}catch(IllegalArgumentException ex){model.addAttribute("error",ex.getMessage());return "product-form";}
        return "redirect:/products";
    }
}

package com.waremanager.controller;
import com.waremanager.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class HomeController {
    private final ProductService products;
    public HomeController(ProductService products){this.products=products;}
    @GetMapping("/") public String home(){return "redirect:/dashboard";}
    @GetMapping("/login") public String login(){return "login";}
    @GetMapping("/dashboard") public String dashboard(Model model){model.addAttribute("productCount",products.count());return "dashboard";}
}

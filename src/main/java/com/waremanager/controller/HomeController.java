package com.waremanager.controller;

import com.waremanager.service.PlanningService;
import com.waremanager.service.UserAccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.waremanager.service.SupplierScoreService; //f5

import java.security.Principal;

@Controller
public class HomeController {

    private final PlanningService planning;
    private final UserAccountService accounts;
    private final SupplierScoreService supplierScores; //f5

    public HomeController(PlanningService planning, UserAccountService accounts, SupplierScoreService supplierScores) {
        this.planning = planning;
        this.accounts = accounts;
        this.supplierScores = supplierScores; //f5
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    // GET /login is in AuthController.

    /** principal = the signed-in user; Spring fills it in because the page requires login. */
    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        model.addAttribute("summary", planning.dashboard());
        model.addAttribute("supplierAvg", supplierScores.averageScore());//f5
        accounts.findByUsername(principal.getName())
                .ifPresent(user -> model.addAttribute("currentUser", user));
        return "dashboard";
    }
}

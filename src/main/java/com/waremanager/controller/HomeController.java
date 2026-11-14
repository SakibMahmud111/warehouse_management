package com.waremanager.controller;

import com.waremanager.service.PlanningService;
import com.waremanager.service.UserAccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class HomeController {

    private final PlanningService planning;
    private final UserAccountService accounts;

    public HomeController(PlanningService planning, UserAccountService accounts) {
        this.planning = planning;
        this.accounts = accounts;
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
        accounts.findByUsername(principal.getName())
                .ifPresent(user -> model.addAttribute("currentUser", user));
        return "dashboard";
    }
}

package com.waremanager.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Shows the login page (GET /login).
 *
 * The form itself is POSTed to /login, which Spring Security handles (see SecurityConfig).
 * After a failed attempt Spring Security redirects to /login?error and keeps the
 * reason in the session, which this controller turns into a readable message.
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        HttpServletRequest request,
                        Model model) {
        if (error != null) {
            model.addAttribute("loginError", errorMessage(request));
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have signed out.");
        }
        return "login";
    }

    private String errorMessage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object reason = (session == null) ? null : session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);

        if (reason instanceof DisabledException) {
            return "This account is disabled. Ask an admin to enable it.";
        }
        if (reason instanceof InternalAuthenticationServiceException) {
            // Usually means the database could not be reached; the full error is in the server log.
            return "Sign-in failed because of a server problem. Try again, or check the server log.";
        }
        // Same message for unknown username and wrong password, so attackers cannot tell which one exists.
        return "Incorrect username or password.";
    }
}

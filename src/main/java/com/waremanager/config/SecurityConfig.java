package com.waremanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Login and access rules.
 *
 * Users come from the PostgreSQL table app_users through
 * DatabaseUserDetailsService (the old in-memory demo user was removed).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Pages anyone can open without signing in
                .requestMatchers("/css/**", "/login", "/error").permitAll()
                // Everything else needs a signed-in user.
                // Later sprints can add role rules here, e.g.
                // .requestMatchers("/purchasing/**").hasAnyRole("PURCHASING_OFFICER", "ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")                     // GET /login shows our Thymeleaf page (AuthController)
                .defaultSuccessUrl("/dashboard", true)   // POST /login is handled by Spring Security itself
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll());
        return http.build();
    }

    /** Hashes new passwords and checks typed passwords against stored hashes. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

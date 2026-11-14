package com.waremanager.service;

import com.waremanager.model.AppUser;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Connects Spring Security's login to the app_users table.
 *
 * When the login form is submitted, Spring Security calls loadUserByUsername(),
 * then compares the typed password with the stored BCrypt hash using the
 * PasswordEncoder bean from SecurityConfig. Because this is the only
 * UserDetailsService bean, Spring Security uses it automatically.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserAccountService accounts;

    public DatabaseUserDetailsService(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = accounts.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No account for username: " + username));

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())   // becomes authority "ROLE_ADMIN", "ROLE_WAREHOUSE_STAFF", ...
                .disabled(!user.isEnabled())
                .build();
    }
}

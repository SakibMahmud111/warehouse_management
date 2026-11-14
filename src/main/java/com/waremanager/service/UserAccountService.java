package com.waremanager.service;

import com.waremanager.model.AppUser;
import com.waremanager.model.Role;
import com.waremanager.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Business rules for user accounts: usernames are case-insensitive,
 * passwords are hashed with BCrypt, and duplicate usernames are rejected.
 */
@Service
public class UserAccountService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** "  Admin " and "admin" are the same account. */
    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByUsername(String username) {
        return users.findByUsername(normalize(username));
    }

    @Transactional(readOnly = true)
    public long count() {
        return users.count();
    }

    @Transactional
    public AppUser createUser(String username, String rawPassword, String fullName, Role role) {
        String name = normalize(username);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (users.existsByUsername(name)) {
            throw new IllegalArgumentException("Username already exists");
        }
        String hash = passwordEncoder.encode(rawPassword);
        return users.save(new AppUser(name, hash, fullName, role));
    }

    /** Saves changes to an existing account (used by tests and, later, admin user management). */
    @Transactional
    public AppUser save(AppUser user) {
        return users.save(user);
    }
}

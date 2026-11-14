package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A person who can sign in to WareManager (Model layer).
 *
 * The table is called "app_users" because "user" is a reserved word in PostgreSQL.
 * Only a BCrypt hash of the password is stored, never the password itself.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Always stored in lower case (see UserAccountService.normalize). */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt hash, e.g. "$2a$10$...". 60 characters long. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    /** A disabled user exists in the database but cannot sign in. */
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA needs a no-argument constructor to create objects from database rows. */
    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String fullName, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    /** Runs automatically just before the row is first inserted. */
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getUsername() { return username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

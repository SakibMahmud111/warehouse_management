package com.waremanager.repository;

import com.waremanager.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Database access for app_users. Spring Data JPA writes the SQL from the method names:
 * findByUsername -> SELECT ... FROM app_users WHERE username = ?
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);
}

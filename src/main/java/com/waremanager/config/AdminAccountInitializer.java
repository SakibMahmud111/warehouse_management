package com.waremanager.config;

import com.waremanager.model.Role;
import com.waremanager.service.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin account when the app starts with an EMPTY app_users table.
 * If any user already exists it does nothing, so restarting never overwrites passwords.
 *
 * The values come from application.properties (app.admin.*), which read the
 * ADMIN_USERNAME / ADMIN_PASSWORD / ADMIN_FULL_NAME environment variables if they are set.
 */
@Component
@Order(1)
public class AdminAccountInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserAccountService accounts;
    private final String username;
    private final String password;
    private final String fullName;

    public AdminAccountInitializer(UserAccountService accounts,
                                   @Value("${app.admin.username}") String username,
                                   @Value("${app.admin.password}") String password,
                                   @Value("${app.admin.full-name}") String fullName) {
        this.accounts = accounts;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(String... args) {
        if (accounts.count() > 0) {
            return;
        }
        accounts.createUser(username, password, fullName, Role.ADMIN);
        log.warn("Created the first admin account '{}'. Change the default password before any real use.", username);
    }
}

package com.waremanager;

import com.waremanager.model.AppUser;
import com.waremanager.model.Role;
import com.waremanager.service.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the database-backed login (runs against the H2 test database).
 * The admin account is created by AdminAccountInitializer with the defaults admin / admin123.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginFlowTests {

    @Autowired MockMvc mvc;
    @Autowired UserAccountService accounts;

    @Test
    void seededAdminCanSignIn() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("admin123"))
           .andExpect(authenticated().withUsername("admin").withRoles("ADMIN"))
           .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void usernameIsCaseInsensitive() throws Exception {
        mvc.perform(formLogin("/login").user("ADMIN").password("admin123"))
           .andExpect(authenticated().withUsername("admin"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("not-the-password"))
           .andExpect(unauthenticated())
           .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unknownUserIsRejected() throws Exception {
        mvc.perform(formLogin("/login").user("nobody").password("whatever123"))
           .andExpect(unauthenticated())
           .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void disabledAccountCannotSignIn() throws Exception {
        AppUser staff = accounts.createUser("disabled.staff", "password123", "Disabled Staff", Role.WAREHOUSE_STAFF);
        staff.setEnabled(false);
        accounts.save(staff);

        mvc.perform(formLogin("/login").user("disabled.staff").password("password123"))
           .andExpect(unauthenticated());
    }

    @Test
    void passwordIsStoredAsBcryptHash() {
        AppUser admin = accounts.findByUsername("admin").orElseThrow();
        assertNotEquals("admin123", admin.getPasswordHash());
        assertTrue(admin.getPasswordHash().startsWith("$2"));
    }

    @Test
    void dashboardRedirectsToLoginWhenSignedOut() throws Exception {
        mvc.perform(get("/dashboard"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginPageShowsErrorMessage() throws Exception {
        mvc.perform(get("/login").param("error", ""))
           .andExpect(status().isOk())
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                   .string(org.hamcrest.Matchers.containsString("Incorrect username or password.")));
    }
}

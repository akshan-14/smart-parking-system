package com.smartparking.security;

import com.smartparking.exception.AccountLockedException;
import com.smartparking.exception.AuthException;
import com.smartparking.model.user.User;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.UserDao;
import com.smartparking.service.AuthService;
import com.smartparking.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the removed hard-coded login bypasses (admin/admin123, attendant1/attendant123).
 * Every sign-in must be decided solely by the stored password hash.
 */
@DisplayName("Security: no hard-coded credential bypass in AuthService.authenticate")
public class HardcodedCredentialBypassTest {

    private AuthService auth;
    private UserDao userDao;

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_hardcoded_bypass"); // new empty database per test = fresh installation
        DatabaseInitializer.initializeDatabase();
        auth = new AuthService();
        userDao = new UserDao();
    }

    @AfterEach
    void clearSession() {
        UserSession.getInstance().clearSession();
    }

    private User user(String username) {
        return userDao.findByUsername(username).orElseThrow(() -> new AssertionError(username + " missing"));
    }

    @Test
    @DisplayName("Fresh-install default credentials sign in normally")
    void freshInstallDefaultsWork() {
        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();
        assertTrue(admin.isForcePasswordChange(), "default admin must be forced to change the password");
        UserSession.getInstance().clearSession();
        assertTrue(auth.authenticate("attendant1", "Attendant@123").isPresent());
        UserSession.getInstance().clearSession();
        assertTrue(auth.authenticate("customer1", "Customer@123").isPresent());
    }

    @Test
    @DisplayName("Legacy alias passwords are rejected even on a fresh install")
    void aliasesRejectedOnFreshInstall() {
        assertThrows(AuthException.class, () -> auth.authenticate("admin", "admin123"));
        assertThrows(AuthException.class, () -> auth.authenticate("ADMIN", "admin123"));
        assertThrows(AuthException.class, () -> auth.authenticate("attendant1", "attendant123"));
        assertThrows(AuthException.class, () -> auth.authenticate("Attendant1", "attendant123"));
        assertThrows(AuthException.class, () -> auth.authenticate("customer1", "customer123"));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty(), "a rejected alias must not start a session");
        assertEquals(2, user("admin").getFailedAttempts(), "each alias attempt (admin, ADMIN) counts as a failed login");
    }

    @Test
    @DisplayName("Hard-coded credentials cannot bypass a changed password")
    void aliasesCannotBypassChangedPassword() {
        auth.changePassword(user("admin").getUserId(), "Admin@123", "Rotated#Admin2026");
        auth.changePassword(user("attendant1").getUserId(), "Attendant@123", "Rotated#Attend2026");

        assertThrows(AuthException.class, () -> auth.authenticate("admin", "admin123"));
        assertThrows(AuthException.class, () -> auth.authenticate("admin", "Admin@123"), "old default must be dead");
        assertThrows(AuthException.class, () -> auth.authenticate("attendant1", "attendant123"));
        assertThrows(AuthException.class, () -> auth.authenticate("attendant1", "Attendant@123"));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());

        User admin = auth.authenticate("admin", "Rotated#Admin2026").orElseThrow();
        assertFalse(admin.isForcePasswordChange(), "forced change is cleared once the password was changed");
        UserSession.getInstance().clearSession();
        assertTrue(auth.authenticate("attendant1", "Rotated#Attend2026").isPresent());
    }

    @Test
    @DisplayName("Repeated alias attempts lock the account; the alias cannot sign in a locked account")
    void aliasAttemptsLeadToLockout() {
        for (int i = 1; i <= 4; i++) {
            assertThrows(AuthException.class, () -> auth.authenticate("admin", "admin123"));
        }
        assertThrows(AccountLockedException.class, () -> auth.authenticate("admin", "admin123"));
        assertNotNull(user("admin").getLockedUntil());
        // Locked: even the correct password and the alias are refused until the lockout expires
        assertThrows(AccountLockedException.class, () -> auth.authenticate("admin", "Admin@123"));
        assertThrows(AccountLockedException.class, () -> auth.authenticate("admin", "admin123"));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
    }

    @Test
    @DisplayName("Invalid logins fail and role-based access follows the authenticated user's role")
    void invalidLoginAndRoles() {
        assertThrows(AuthException.class, () -> auth.authenticate("no_such_user", "Whatever@123"));
        assertThrows(AuthException.class, () -> auth.authenticate("customer1", "Wrong@Pass1"));
        assertThrows(AuthException.class, () -> auth.authenticate("customer1", ""));

        User attendant = auth.authenticate("attendant1", "Attendant@123").orElseThrow();
        assertTrue(AccessControl.hasPermission(attendant, Permission.PARK_VEHICLE_ENTRY));
        assertFalse(AccessControl.hasPermission(attendant, Permission.MANAGE_USERS));
        assertThrows(SecurityException.class, () -> AccessControl.checkPermission(Permission.MANAGE_USERS));
        UserSession.getInstance().clearSession();

        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();
        assertTrue(AccessControl.hasPermission(admin, Permission.MANAGE_USERS));
        assertDoesNotThrow(() -> AccessControl.checkPermission(Permission.MANAGE_USERS));
    }
}

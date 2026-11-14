package com.smartparking.security;

import com.smartparking.exception.AuthException;
import com.smartparking.exception.PasswordChangeRequiredException;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.User;
import com.smartparking.repository.AuditLogDao;
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
 * Passwords chosen by an administrator (reset or initial password) are temporary: the user must replace them at
 * the next sign-in, and a pending forced change is never cleared by a reset.
 */
@DisplayName("Security: admin-set passwords are temporary and force a change")
public class AdminTemporaryPasswordTest {

    private AuthService auth;
    private UserDao userDao;

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_admin_temp_password");
        DatabaseInitializer.initializeDatabase();
        auth = new AuthService();
        userDao = new UserDao();
        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();
        auth.completeForcedPasswordChange(admin, "Admin@123", "Rotated#Admin2026");
    }

    @AfterEach
    void clearSession() {
        UserSession.getInstance().clearSession();
    }

    private boolean flag(String username) {
        return userDao.findByUsername(username).orElseThrow().isForcePasswordChange();
    }

    private void signInAsAdmin() {
        UserSession.getInstance().clearSession();
        auth.authenticate("admin", "Rotated#Admin2026").orElseThrow();
    }

    @Test
    @DisplayName("Admin reset sets force_password_change and the user is restricted until they change it")
    void resetForcesChange() {
        int attendantId = userDao.findByUsername("attendant1").orElseThrow().getUserId();
        assertFalse(flag("attendant1"));

        auth.adminResetPassword(attendantId, "Temp#Reset2026");
        assertTrue(flag("attendant1"), "reset must require a change at next sign-in");

        UserSession.getInstance().clearSession();
        User attendant = auth.authenticate("attendant1", "Temp#Reset2026").orElseThrow();
        assertTrue(AccessControl.requiresPasswordChange(attendant));
        assertFalse(AccessControl.hasPermission(attendant, Permission.PARK_VEHICLE_ENTRY));
        assertThrows(PasswordChangeRequiredException.class, () -> AccessControl.requireFullAccess(attendant));

        auth.completeForcedPasswordChange(attendant, "Temp#Reset2026", "Own#Choice2026");
        assertFalse(flag("attendant1"));
        assertTrue(AccessControl.hasPermission(attendant, Permission.PARK_VEHICLE_ENTRY));

        UserSession.getInstance().clearSession();
        assertThrows(AuthException.class, () -> auth.authenticate("attendant1", "Temp#Reset2026"),
                "the temporary password no longer works");
        assertTrue(auth.authenticate("attendant1", "Own#Choice2026").isPresent());
    }

    @Test
    @DisplayName("Resetting a user who still has a pending forced change keeps it pending")
    void resetNeverClearsPendingChange() {
        User created = auth.adminCreateUser("Night Attendant", "9811223344", "n@test.com", "attendant7",
                "Initial#2026x", UserRole.ATTENDANT, "Night");
        assertTrue(flag("attendant7"));
        auth.adminResetPassword(created.getUserId(), "Second#2026x");
        assertTrue(flag("attendant7"));
    }

    @Test
    @DisplayName("Users created by an administrator must change the initial password")
    void createdUsersMustChangeInitialPassword() {
        auth.adminCreateUser("New Admin", "9811223355", "a@test.com", "admin2", "Initial#2026x", UserRole.ADMIN, null);
        assertTrue(flag("admin2"));

        UserSession.getInstance().clearSession();
        User admin2 = auth.authenticate("admin2", "Initial#2026x").orElseThrow();
        assertFalse(AccessControl.hasPermission(admin2, Permission.MANAGE_USERS),
                "a new admin cannot use admin rights with a password another admin knows");
    }

    @Test
    @DisplayName("Self-registered customers chose their own password and are not forced to change it")
    void selfRegistrationNotForced() {
        UserSession.getInstance().clearSession();
        auth.registerCustomer("Self Registered", "9811223366", "selfreg", "Mine#2026xx", true);
        assertFalse(flag("selfreg"));
    }

    @Test
    @DisplayName("The reset is audited with the forced-change requirement")
    void resetAudited() {
        int customerId = userDao.findByUsername("customer1").orElseThrow().getUserId();
        auth.adminResetPassword(customerId, "Temp#Reset2026");
        assertTrue(new AuditLogDao().findFiltered("ALL", "PASSWORD_RESET", "SUCCESS", null, 0, 10).stream()
                .anyMatch(e -> "forced_change_at_next_sign_in=1".equals(e.getNewValue())));
        signInAsAdmin();
    }
}

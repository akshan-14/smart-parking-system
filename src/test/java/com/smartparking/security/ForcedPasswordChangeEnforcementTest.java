package com.smartparking.security;

import com.smartparking.exception.PasswordChangeRequiredException;
import com.smartparking.model.audit.AuditLogEntry;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backend enforcement of force_password_change: a flagged user holds no permissions and cannot pass the application
 * access gate until the password has been changed through AuthService.
 */
@DisplayName("Security: forced password change is enforced in the backend")
public class ForcedPasswordChangeEnforcementTest {

    private AuthService auth;
    private UserDao userDao;

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_forced_password_change");
        DatabaseInitializer.initializeDatabase();
        auth = new AuthService();
        userDao = new UserDao();
    }

    @AfterEach
    void clearSession() {
        UserSession.getInstance().clearSession();
    }

    private boolean flagInDb(String username) {
        return userDao.findByUsername(username).orElseThrow().isForcePasswordChange();
    }

    @Test
    @DisplayName("A flagged user has no permissions and cannot pass the access gate")
    void flaggedUserIsRestricted() {
        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();
        assertTrue(AccessControl.requiresPasswordChange(admin));

        for (Permission p : Permission.values()) {
            assertFalse(AccessControl.hasPermission(admin, p), "flagged admin must not hold " + p);
        }
        assertThrows(PasswordChangeRequiredException.class, () -> AccessControl.requireFullAccess(admin));
        PasswordChangeRequiredException denied = assertThrows(PasswordChangeRequiredException.class,
                () -> AccessControl.checkPermission(Permission.MANAGE_USERS));
        assertTrue(denied.getMessage().contains("Access Denied"));

        // Service methods are blocked too, and nothing is created
        assertThrows(SecurityException.class, () -> auth.adminCreateUser("Sneaky Admin", "9811223344",
                "s@test.com", "sneaky", "Sneaky@123", UserRole.ADMIN, null));
        assertTrue(userDao.findByUsername("sneaky").isEmpty());

        // The denial is audited with the reason
        List<AuditLogEntry> denials = new AuditLogDao().findFiltered("ALL", "ACCESS_DENIED", "FAILURE", null, 0, 50);
        assertTrue(denials.stream().anyMatch(e -> "admin".equals(e.getUsername())
                && e.getReason() != null && e.getReason().contains("mandatory password change")));
    }

    @Test
    @DisplayName("Invalid forced-change attempts keep the user restricted")
    void invalidChangesKeepRestriction() {
        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> auth.completeForcedPasswordChange(admin, "Wrong@123", "Rotated#Admin2026"), "wrong current password");
        assertThrows(IllegalArgumentException.class,
                () -> auth.completeForcedPasswordChange(admin, "Admin@123", "Admin@123"), "same password");
        assertThrows(RuntimeException.class,
                () -> auth.completeForcedPasswordChange(admin, "Admin@123", "short"), "weak password");
        assertThrows(IllegalArgumentException.class,
                () -> auth.completeForcedPasswordChange(admin, "Admin@123", ""), "blank password");

        assertTrue(AccessControl.requiresPasswordChange(admin));
        assertTrue(flagInDb("admin"));
        assertThrows(PasswordChangeRequiredException.class, () -> AccessControl.requireFullAccess(admin));
    }

    @Test
    @DisplayName("Completing the change lifts the restriction in memory, in the session and in the database")
    void completingChangeLiftsRestriction() {
        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();
        auth.completeForcedPasswordChange(admin, "Admin@123", "Rotated#Admin2026");

        assertFalse(AccessControl.requiresPasswordChange(admin));
        assertFalse(UserSession.getInstance().getCurrentUser().orElseThrow().isForcePasswordChange());
        assertFalse(flagInDb("admin"));
        assertDoesNotThrow(() -> AccessControl.requireFullAccess(admin));
        assertTrue(AccessControl.hasPermission(admin, Permission.MANAGE_USERS));
        assertNotNull(auth.adminCreateUser("New Attendant", "9811223344", "n@test.com", "attendant9",
                "Attend#2026x", UserRole.ATTENDANT, "Night"));

        // A fresh sign-in with the new password has full access straight away
        UserSession.getInstance().clearSession();
        User again = auth.authenticate("admin", "Rotated#Admin2026").orElseThrow();
        assertFalse(AccessControl.requiresPasswordChange(again));
    }

    @Test
    @DisplayName("Users without the flag sign in with normal access")
    void unflaggedUsersUnaffected() {
        User attendant = auth.authenticate("attendant1", "Attendant@123").orElseThrow();
        assertFalse(AccessControl.requiresPasswordChange(attendant));
        assertDoesNotThrow(() -> AccessControl.requireFullAccess(attendant));
        assertTrue(AccessControl.hasPermission(attendant, Permission.PARK_VEHICLE_ENTRY));
        assertFalse(AccessControl.hasPermission(attendant, Permission.MANAGE_USERS));
        assertThrows(SecurityException.class, () -> AccessControl.requireFullAccess(null));
    }
}

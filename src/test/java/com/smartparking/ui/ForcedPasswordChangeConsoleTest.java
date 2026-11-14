package com.smartparking.ui;

import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.UserDao;
import com.smartparking.security.UserSession;
import com.smartparking.service.AuthService;
import com.smartparking.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

/** The console applies the same forced-password-change rule as the GUI. */
@DisplayName("Console: forced password change must be completed before access")
public class ForcedPasswordChangeConsoleTest {

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_forced_change_console");
        DatabaseInitializer.initializeDatabase();
    }

    @AfterEach
    void clearSession() {
        UserSession.getInstance().clearSession();
    }

    /** Runs the console login with the given input lines and returns everything it printed. */
    private String consoleLogin(String... lines) {
        String input = String.join("\n", lines) + "\n";
        ConsoleApp app = new ConsoleApp(new Scanner(input), new AuthService());
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            app.handleLogin();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private boolean adminFlagInDb() {
        return new UserDao().findByUsername("admin").orElseThrow().isForcePasswordChange();
    }

    @Test
    @DisplayName("Cancelling the change (blank password) signs the user out")
    void blankCancels() {
        String out = consoleLogin("admin", "Admin@123", "");
        assertTrue(out.contains("[PASSWORD CHANGE REQUIRED]"));
        assertTrue(out.contains("[LOGIN CANCELLED]"));
        assertFalse(out.contains("[LOGIN SUCCESSFUL]"));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
        assertTrue(adminFlagInDb());
    }

    @Test
    @DisplayName("Mismatched or rejected passwords sign the user out")
    void mismatchOrRejectedCancels() {
        String out = consoleLogin("admin", "Admin@123", "Rotated#Admin2026", "Different#2026");
        assertTrue(out.contains("do not match"));
        assertFalse(out.contains("[LOGIN SUCCESSFUL]"));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());

        out = consoleLogin("admin", "Admin@123", "Admin@123", "Admin@123");
        assertTrue(out.contains("Password rejected"));
        assertFalse(out.contains("[LOGIN SUCCESSFUL]"));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
        assertTrue(adminFlagInDb());

        // Input ends before the new password is entered: treated as cancel
        out = consoleLogin("admin", "Admin@123");
        assertFalse(out.contains("[LOGIN SUCCESSFUL]"));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
    }

    @Test
    @DisplayName("Completing the change signs the user in with full access")
    void completedChangeSignsIn() {
        String out = consoleLogin("admin", "Admin@123", "Rotated#Admin2026", "Rotated#Admin2026");
        assertTrue(out.contains("[PASSWORD UPDATED]"));
        assertTrue(out.contains("[LOGIN SUCCESSFUL]"));
        assertFalse(UserSession.getInstance().getCurrentUser().orElseThrow().isForcePasswordChange());
        assertFalse(adminFlagInDb());
    }

    @Test
    @DisplayName("Normal and invalid logins behave as before")
    void normalAndInvalidLogins() {
        String out = consoleLogin("attendant1", "Attendant@123");
        assertTrue(out.contains("[LOGIN SUCCESSFUL]"));
        assertFalse(out.contains("[PASSWORD CHANGE REQUIRED]"));
        UserSession.getInstance().clearSession();

        out = consoleLogin("attendant1", "Wrong@Pass1");
        assertTrue(out.contains("[LOGIN FAILED]"));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
    }
}

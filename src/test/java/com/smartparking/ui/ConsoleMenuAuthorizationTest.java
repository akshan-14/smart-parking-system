package com.smartparking.ui;

import com.smartparking.config.DatabaseConnectionManager;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Console menu options 1-7 (and --demo) require an authenticated session that passes the same RBAC checks as the
 * GUI. The full console is driven through its real menu with scripted input.
 */
@DisplayName("Console: menu operations require authentication and role permissions")
public class ConsoleMenuAuthorizationTest {

    private static final String[] DATA_TABLES = {"tickets", "payments", "memberships", "reservations"};

    @BeforeEach
    void freshDatabase() {
        TestDatabase.useFresh("test_console_menu_auth"); // start() initialises the schema
    }

    @AfterEach
    void clearSession() {
        UserSession.getInstance().clearSession();
    }

    private static String run(String[] args, String... lines) {
        String input = String.join("\n", lines) + "\n";
        ConsoleApp app = new ConsoleApp(new Scanner(input), new AuthService());
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            app.start(args);
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static String menu(String... lines) {
        return run(new String[0], lines);
    }

    private static long rows(String table) throws Exception {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static long[] snapshot() throws Exception {
        long[] counts = new long[DATA_TABLES.length];
        for (int i = 0; i < DATA_TABLES.length; i++) counts[i] = rows(DATA_TABLES[i]);
        return counts;
    }

    private static int occurrences(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    @Test
    @DisplayName("Anonymous users cannot run any of options 1-7, and nothing is written")
    void anonymousBlocked() throws Exception {
        menu(); // initialise schema/seed data once so the snapshot is comparable
        long[] before = snapshot();

        String out = menu("1", "2", "3", "4", "5", "6", "7", "9");

        assertEquals(7, occurrences(out, "[LOGIN REQUIRED]"), out);
        assertFalse(out.contains("EXECUTING AUTOMATED"));
        assertFalse(out.contains("--- Vehicle Entry ---"));
        assertEquals(java.util.Arrays.toString(before), java.util.Arrays.toString(snapshot()), "no tickets, payments, passes or reservations without sign-in");
    }

    @Test
    @DisplayName("An attendant can use gate operations but not the admin report")
    void attendantRoleEnforced() throws Exception {
        String out = menu("8", "attendant1", "Attendant@123",
                "2", "UP32ZZ0001", "2",
                "7",
                "1",
                "9");
        assertTrue(out.contains("[SUCCESS] Vehicle parked successfully!"), out);
        assertTrue(out.contains("[ACCESS DENIED] Admin report"));
        assertTrue(out.contains("[ACCESS DENIED] Automated demonstration"));
        assertFalse(out.contains("EXECUTING AUTOMATED"));
        assertEquals(1, rows("tickets WHERE license_plate = 'UP32ZZ0001'"));
    }

    @Test
    @DisplayName("A customer cannot operate the gates")
    void customerCannotOperateGates() throws Exception {
        menu();
        long tickets = rows("tickets");
        String out = menu("8", "customer1", "Customer@123", "2", "3", "9");
        assertTrue(out.contains("[ACCESS DENIED] Vehicle entry"), out);
        assertTrue(out.contains("[ACCESS DENIED] Vehicle exit"));
        assertEquals(tickets, rows("tickets"));
    }

    @Test
    @DisplayName("Logging out (option 0) removes access again")
    void logoutRemovesAccess() {
        String out = menu("8", "attendant1", "Attendant@123", "0", "2", "9");
        assertTrue(out.contains("[LOGGED OUT]"));
        assertTrue(out.contains("[LOGIN REQUIRED] Vehicle entry"), out);
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
    }

    @Test
    @DisplayName("A flagged admin who skips the password change gets no access")
    void flaggedAdminWithoutChangeBlocked() {
        String out = menu("8", "admin", "Admin@123", "", "7", "9");
        assertTrue(out.contains("[LOGIN CANCELLED]"));
        assertTrue(out.contains("[LOGIN REQUIRED] Admin report"), out);
    }

    @Test
    @DisplayName("--demo refuses to run without an administrator sign-in")
    void demoRequiresAdmin() throws Exception {
        menu();
        long[] before = snapshot();

        String anonymous = run(new String[]{"--demo"}, "", "");
        assertFalse(anonymous.contains("EXECUTING AUTOMATED"), anonymous);
        String attendant = run(new String[]{"--demo"}, "attendant1", "Attendant@123");
        assertTrue(attendant.contains("[ACCESS DENIED] Automated demonstration"), attendant);
        assertFalse(attendant.contains("EXECUTING AUTOMATED"));
        assertEquals(java.util.Arrays.toString(before), java.util.Arrays.toString(snapshot()));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty(), "--demo signs out when done");
    }

    @Test
    @DisplayName("An administrator (after the mandatory password change) can run the report and the demo")
    void adminHasAccess() {
        String out = menu("8", "admin", "Admin@123", "Rotated#Admin2026", "Rotated#Admin2026", "7", "9");
        assertTrue(out.contains("[LOGIN SUCCESSFUL]"));
        assertFalse(out.contains("[ACCESS DENIED]"), out);
        assertFalse(out.contains("[LOGIN REQUIRED]"));

        String demo = run(new String[]{"--demo"}, "admin", "Rotated#Admin2026");
        assertTrue(demo.contains("EXECUTING AUTOMATED"), demo);
    }
}

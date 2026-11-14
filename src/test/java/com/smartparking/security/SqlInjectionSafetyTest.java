package com.smartparking.security;

import com.smartparking.support.TestDatabase;
import com.smartparking.exception.AuthException;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.TicketDao;
import com.smartparking.repository.UserDao;
import com.smartparking.service.AuthService;
import com.smartparking.util.InputValidator;
import com.smartparking.util.PlateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: Input Validation, SQL Injection Safety, and XSS Sanitization Tests")
public class SqlInjectionSafetyTest {

    /** Isolated throw-away database: never touch the real smart_parking.db or another class's file. */
    @BeforeAll
    static void isolateDatabase() {
        TestDatabase.useFresh("test_sql_injection");
    }

    /** AuthService.login() starts a real UserSession; clear it so the signed-in role cannot leak into later test classes. */
    @AfterEach
    void clearLoginSession() {
        com.smartparking.security.UserSession.getInstance().clearSession();
    }

    private UserDao userDao;
    private TicketDao ticketDao;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        DatabaseInitializer.initializeDatabase();
        this.userDao = new UserDao();
        this.ticketDao = new TicketDao();
        this.authService = new AuthService();
    }

    @Test
    @DisplayName("SQL Injection payloads in authentication fail safely without leaking data")
    void testAuthSqlInjectionSafety() {
        String[] payloads = {
                "' OR '1'='1",
                "admin' --",
                "' OR 1=1; DROP TABLE users; --",
                "\" OR \"\"=\"",
                "admin' OR 'a'='a"
        };

        for (String payload : payloads) {
            assertThrows(AuthException.class, () -> authService.authenticate(payload, "anyPass"),
                    "SQL injection payload must be safely rejected: " + payload);
            assertFalse(userDao.findByUsername(payload).isPresent());
        }
    }

    @Test
    @DisplayName("SQL injection payloads in plate searches are treated as literals without syntax errors")
    void testPlateSearchSqlInjectionSafety() {
        String payload = "UP32'; DROP TABLE slots; --";
        assertFalse(PlateUtil.isValid(payload));
        assertDoesNotThrow(() -> ticketDao.searchActiveByPlate(payload));
        assertTrue(ticketDao.searchActiveByPlate(payload).isEmpty());
    }

    @Test
    @DisplayName("XSS HTML characters are correctly escaped")
    void testHtmlEscaping() {
        String xss = "<script>alert('pwned');</script>&\"'";
        String escaped = InputValidator.escapeHtml(xss);

        assertFalse(escaped.contains("<script>"));
        assertFalse(escaped.contains("</script>"));
        assertTrue(escaped.contains("&lt;script&gt;"));
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&quot;"));
    }
}

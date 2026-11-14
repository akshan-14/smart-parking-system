package com.smartparking.security;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.exception.AuditLogException;
import com.smartparking.model.user.User;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.service.AuthService;
import com.smartparking.service.TariffService;
import com.smartparking.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Fee overrides require OVERRIDE_FEE, are attributed to the real signed-in user, and fail closed. */
@DisplayName("Security: fee override authorisation and audit attribution")
public class FeeOverrideAuthorizationTest {

    private AuthService auth;
    private TariffService tariffs;

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_fee_override");
        DatabaseInitializer.initializeDatabase();
        auth = new AuthService();
        tariffs = new TariffService(new SystemSettingsDao());
    }

    @AfterEach
    void cleanUp() throws SQLException {
        UserSession.getInstance().clearSession();
        exec("DROP TRIGGER IF EXISTS test_fail_audit;");
    }

    private static void exec(String sql) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static long count(String where) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM audit_log WHERE " + where)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private void signInAdmin() {
        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();
        auth.completeForcedPasswordChange(admin, "Admin@123", "Rotated#Admin2026");
    }

    @Test
    @DisplayName("An attendant cannot override a fee; the attempt is audited as ACCESS_DENIED")
    void attendantDenied() throws SQLException {
        auth.authenticate("attendant1", "Attendant@123").orElseThrow();
        assertThrows(SecurityException.class,
                () -> tariffs.recordFeeOverride("TKT-1", 120.0, 0.0, "Customer complaint", "Duty Manager"));
        assertEquals(0, count("action = 'FEE_OVERRIDE'"));
        assertEquals(1, count("action = 'ACCESS_DENIED' AND entity_id = 'OVERRIDE_FEE' AND username = 'attendant1'"));
    }

    @Test
    @DisplayName("An administrator's override is attributed to that administrator, not a caller-supplied name")
    void adminOverrideAttributed() throws SQLException {
        signInAdmin();
        tariffs.recordFeeOverride("TKT-2", 120.0, 60.0, "Barrier fault refund", "Duty Manager");
        assertEquals(1, count("action = 'FEE_OVERRIDE' AND username = 'admin' AND role = 'ADMIN' AND entity_id = 'TKT-2'"));
        assertEquals(0, count("action = 'FEE_OVERRIDE' AND username = 'Duty Manager'"));
    }

    @Test
    @DisplayName("Invalid overrides are rejected before anything is recorded")
    void invalidOverridesRejected() throws SQLException {
        signInAdmin();
        assertThrows(IllegalArgumentException.class, () -> tariffs.recordFeeOverride("TKT-3", 120.0, 60.0, "abc", null));
        assertThrows(IllegalArgumentException.class, () -> tariffs.recordFeeOverride("TKT-3", 120.0, -1.0, "Valid reason", null));
        assertThrows(IllegalArgumentException.class, () -> tariffs.recordFeeOverride("TKT-3", 120.0, Double.NaN, "Valid reason", null));
        assertEquals(0, count("action = 'FEE_OVERRIDE'"));
    }

    @Test
    @DisplayName("If the override cannot be audited the call fails, so the caller never applies it")
    void auditFailureFailsClosed() throws SQLException {
        signInAdmin();
        exec("CREATE TRIGGER test_fail_audit BEFORE INSERT ON audit_log BEGIN SELECT RAISE(ABORT, 'simulated'); END;");
        assertThrows(AuditLogException.class,
                () -> tariffs.recordFeeOverride("TKT-4", 120.0, 0.0, "Barrier fault refund", null));
    }
}

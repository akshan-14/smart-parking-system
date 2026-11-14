package com.smartparking.security;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.exception.AuditLogException;
import com.smartparking.model.user.User;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.repository.UserDao;
import com.smartparking.service.AuditLogService;
import com.smartparking.service.AuthService;
import com.smartparking.service.DataRetentionService;
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

/**
 * DPDP erasure requests, approvals and the retention job are authorised, commit together with their audit
 * entries, and cannot be replayed.
 */
@DisplayName("DPDP: erasure requests, approvals and retention are authorised and atomic")
public class DpdpRequestAtomicityTest {

    private AuthService auth;
    private DataRetentionService retention;
    private final UserDao userDao = new UserDao();

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_dpdp_requests");
        DatabaseInitializer.initializeDatabase();
        auth = new AuthService();
        retention = new DataRetentionService(new SystemSettingsDao(), AuditLogService.getInstance());
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

    private static void failAudit() throws SQLException {
        exec("CREATE TRIGGER test_fail_audit BEFORE INSERT ON audit_log BEGIN SELECT RAISE(ABORT, 'simulated audit failure'); END;");
    }

    private static long count(String sql) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static String value(String sql) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private User customer() {
        return userDao.findByUsername("customer1").orElseThrow();
    }

    private int submitRequestAsCustomer() throws SQLException {
        User c = auth.authenticate("customer1", "Customer@123").orElseThrow();
        retention.requestDeletion(c.getUserId(), c.getUsername(), c.getPhone());
        UserSession.getInstance().clearSession();
        return Integer.parseInt(value("SELECT id FROM deletion_requests WHERE status = 'PENDING' ORDER BY id DESC LIMIT 1"));
    }

    private void signInAdmin() {
        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();
        auth.completeForcedPasswordChange(admin, "Admin@123", "Rotated#Admin2026");
    }

    @Test
    @DisplayName("A customer can request erasure of their own account only, once at a time")
    void requestOwnAccountOnly() throws SQLException {
        User c = auth.authenticate("customer1", "Customer@123").orElseThrow();
        int otherId = userDao.findByUsername("attendant1").orElseThrow().getUserId();

        assertThrows(SecurityException.class, () -> retention.requestDeletion(otherId, "attendant1", null));
        assertEquals(0, count("SELECT COUNT(*) FROM deletion_requests"));

        retention.requestDeletion(c.getUserId(), c.getUsername(), c.getPhone());
        assertEquals(1, count("SELECT COUNT(*) FROM deletion_requests WHERE status = 'PENDING'"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'DELETION_REQUESTED'"));

        assertThrows(IllegalStateException.class, () -> retention.requestDeletion(c.getUserId(), c.getUsername(), c.getPhone()));
        assertEquals(1, count("SELECT COUNT(*) FROM deletion_requests"));
    }

    @Test
    @DisplayName("Staff cannot file erasure requests, and a request rolls back if it cannot be audited")
    void requestAuthorisationAndRollback() throws SQLException {
        User attendant = auth.authenticate("attendant1", "Attendant@123").orElseThrow();
        assertThrows(SecurityException.class,
                () -> retention.requestDeletion(attendant.getUserId(), attendant.getUsername(), null));
        UserSession.getInstance().clearSession();

        User c = auth.authenticate("customer1", "Customer@123").orElseThrow();
        failAudit();
        assertThrows(AuditLogException.class, () -> retention.requestDeletion(c.getUserId(), c.getUsername(), c.getPhone()));
        assertEquals(0, count("SELECT COUNT(*) FROM deletion_requests"), "no request without its record");
    }

    @Test
    @DisplayName("Approval requires an admin, is atomic with its entry, is attributed, and cannot be replayed")
    void approvalAuthorisedAtomicOnce() throws SQLException {
        int requestId = submitRequestAsCustomer();
        int customerId = customer().getUserId();

        auth.authenticate("attendant1", "Attendant@123").orElseThrow();
        assertThrows(SecurityException.class, () -> retention.approveDeletion(requestId, "admin"));
        UserSession.getInstance().clearSession();

        signInAdmin();
        failAudit();
        assertThrows(AuditLogException.class, () -> retention.approveDeletion(requestId, "admin"));
        assertEquals("Priya Sharma", customer().getFullName(), "anonymisation rolled back");
        assertEquals("PENDING", value("SELECT status FROM deletion_requests WHERE id = " + requestId));
        assertEquals(2, count("SELECT COUNT(*) FROM user_vehicles WHERE user_id = " + customerId));

        exec("DROP TRIGGER test_fail_audit;");
        retention.approveDeletion(requestId, "someone-else");
        assertEquals("[DELETED_USER]", customer().getFullName());
        assertEquals(0, count("SELECT COUNT(*) FROM user_vehicles WHERE user_id = " + customerId));
        assertEquals("admin", value("SELECT reviewed_by FROM deletion_requests WHERE id = " + requestId));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CUSTOMER_DATA_ERASURE_APPROVED' AND username = 'admin'"));

        assertThrows(IllegalStateException.class, () -> retention.approveDeletion(requestId, "admin"), "no replay");
        assertThrows(IllegalArgumentException.class, () -> retention.approveDeletion(99999, "admin"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CUSTOMER_DATA_ERASURE_APPROVED'"));
    }

    @Test
    @DisplayName("The retention job requires its permission and rolls back anonymisation if it cannot be audited")
    void retentionJobAuthorisedAndAtomic() throws SQLException {
        String slot = value("SELECT slot_id FROM slots LIMIT 1");
        exec("INSERT INTO tickets (ticket_number, license_plate, vehicle_type, slot_id, floor_number, entry_time, exit_time, status) "
                + "VALUES ('TKT-OLD-1', 'UP32AB4444', 'CAR', '" + slot + "', 1, '2020-01-01 09:00:00', '2020-01-01 10:00:00', 'EXITED');");

        auth.authenticate("attendant1", "Attendant@123").orElseThrow();
        assertThrows(SecurityException.class, () -> retention.runRetentionJob());
        UserSession.getInstance().clearSession();

        signInAdmin();
        failAudit();
        assertThrows(AuditLogException.class, () -> retention.runRetentionJob());
        assertEquals("UP32AB4444", value("SELECT license_plate FROM tickets WHERE ticket_number = 'TKT-OLD-1'"),
                "anonymisation rolled back");

        exec("DROP TRIGGER test_fail_audit;");
        DataRetentionService.RetentionReport report = retention.runRetentionJob();
        assertEquals(1, report.ticketsAnonymized());
        assertEquals("UP00XX0000", value("SELECT license_plate FROM tickets WHERE ticket_number = 'TKT-OLD-1'"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'RETENTION_JOB_EXECUTED' AND username = 'admin'"));
    }
}

package com.smartparking.security;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.service.AuditLogService;
import com.smartparking.service.ReservationService;
import com.smartparking.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** The automatic no-show purge is an explicitly low-risk audit event: an audit outage must not stop start-up. */
@DisplayName("Audit policy: automatic reservation purge continues visibly during an audit outage")
public class ReservationPurgeAuditPolicyTest {

    private ReservationService service;

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_reservation_purge_policy");
        DatabaseInitializer.initializeDatabase();
    }

    @AfterEach
    void cleanUp() throws SQLException {
        if (service != null) service.stopAutoExpiryScheduler();
        exec("DROP TRIGGER IF EXISTS test_fail_audit;");
    }

    private static void exec(String sql) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    @Test
    @DisplayName("RESERVATIONS_PURGED is classified low-risk and reported, not thrown")
    void purgeIsLowRisk() throws SQLException {
        assertTrue(AuditLogService.isLowRisk("RESERVATIONS_PURGED"));
        assertFalse(AuditLogService.isLowRisk("REFUND_PAYMENT"), "financial events stay strict");
        assertFalse(AuditLogService.isLowRisk("FEE_OVERRIDE"));

        // A confirmed booking whose start time passed long ago: start-up will expire it and audit the purge.
        exec("INSERT INTO reservations (reservation_code, user_id, slot_id, license_plate, vehicle_type, start_time, end_time, status) "
                + "SELECT 'RES-NOSHOW-1', (SELECT user_id FROM users WHERE username = 'customer1'), slot_id, 'UP32AB3333', 'CAR', "
                + "'2020-01-01 09:00:00', '2020-01-01 11:00:00', 'CONFIRMED' FROM slots LIMIT 1;");

        exec("CREATE TRIGGER test_fail_audit BEFORE INSERT ON audit_log BEGIN SELECT RAISE(ABORT, 'simulated'); END;");
        long before = AuditLogService.getInstance().getLowRiskFailureCount();
        assertDoesNotThrow(() -> service = new ReservationService(), "start-up survives an audit outage");
        assertEquals(before + 1, AuditLogService.getInstance().getLowRiskFailureCount(), "the purge's failed entry is reported");
        before = AuditLogService.getInstance().getLowRiskFailureCount();
        assertDoesNotThrow(() -> AuditLogService.getInstance().logWithActor(null, "SYSTEM", "SYSTEM",
                "RESERVATIONS_PURGED", "RESERVATION", "BATCH", null, "count=1", "test", "SUCCESS"));
        assertTrue(AuditLogService.getInstance().getLowRiskFailureCount() > before, "the failure is reported");
    }
}

package com.smartparking.security;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.exception.AuditLogException;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.tariff.TariffRate;
import com.smartparking.model.user.User;
import com.smartparking.repository.AuditLogDao;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.FastagDao;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.repository.TariffDao;
import com.smartparking.service.AuthService;
import com.smartparking.service.FastagService;
import com.smartparking.service.ParkingService;
import com.smartparking.service.SettingsService;
import com.smartparking.service.TariffService;
import com.smartparking.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Refunds, FASTag administration and tariff/settings changes commit together with their audit entries (and history
 * rows), are attributed to the signed-in user, and roll back completely when the audit entry cannot be written.
 */
@DisplayName("Audit: refunds, FASTag and tariff/settings changes are atomic and attributed")
public class FinancialAdminAuditAtomicityTest {

    private AuthService auth;

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_financial_admin_audit");
        DatabaseInitializer.initializeDatabase();
        auth = new AuthService();
    }

    @AfterEach
    void cleanUp() throws SQLException {
        UserSession.getInstance().clearSession();
        allowAudit();
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private static void exec(String sql) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static void failAudit() throws SQLException {
        exec("CREATE TRIGGER IF NOT EXISTS test_fail_audit BEFORE INSERT ON audit_log BEGIN SELECT RAISE(ABORT, 'simulated audit failure'); END;");
    }

    private static void allowAudit() throws SQLException {
        exec("DROP TRIGGER IF EXISTS test_fail_audit;");
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

    private void signInAdmin() {
        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();
        auth.completeForcedPasswordChange(admin, "Admin@123", "Rotated#Admin2026");
    }

    private static int completedPayment() throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO payments (amount, payment_method, transaction_ref, "
                     + "status, payment_time) VALUES (100.0, 'CASH', ?, 'COMPLETED', '2026-11-14 10:00:00');",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "TXN-FIN-" + System.nanoTime());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static ParkingService parkingService() {
        ParkingLot lot = new ParkingLot("LOT-T", "Test", "Test");
        lot.addFloor(new ParkingFloor(1, "Floor 1"));
        return ParkingService.createDefault(lot);
    }

    private static String paymentStatus(int id) throws SQLException {
        return value("SELECT status FROM payments WHERE payment_id = " + id);
    }

    // ---- refunds ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ParkingService refunds commit with one REFUND_PAYMENT entry attributed to the signed-in admin")
    void refundAtomicAndAttributed() throws SQLException {
        ParkingService parking = parkingService();
        signInAdmin();
        int payment = completedPayment();

        failAudit();
        assertThrows(AuditLogException.class, () -> parking.refundPayment(payment, "Duplicate charge", "admin"));
        assertEquals("COMPLETED", paymentStatus(payment), "refund rolled back");

        allowAudit();
        assertTrue(parking.refundPayment(payment, "Duplicate charge", "someone-else"));
        assertEquals("REFUNDED", paymentStatus(payment));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'REFUND_PAYMENT' AND username = 'admin' AND role = 'ADMIN'"));
        assertEquals(0, count("SELECT COUNT(*) FROM audit_log WHERE username = 'someone-else'"));
        assertFalse(parking.refundPayment(payment, "Duplicate charge", "admin"), "already refunded");
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'REFUND_PAYMENT'"), "no entry for a no-op");
        assertTrue(new AuditLogDao().verifyIntegrity().isValid());
    }

    @Test
    @DisplayName("Attendants cannot refund and invalid reasons are rejected")
    void refundAuthorisation() throws SQLException {
        ParkingService parking = parkingService();
        int payment = completedPayment();
        auth.authenticate("attendant1", "Attendant@123").orElseThrow();
        assertThrows(SecurityException.class, () -> parking.refundPayment(payment, "Duplicate charge", "admin"));
        UserSession.getInstance().clearSession();
        signInAdmin();
        assertThrows(IllegalArgumentException.class, () -> parking.refundPayment(payment, "no", "admin"));
        assertEquals("COMPLETED", paymentStatus(payment));
    }

    // ---- FASTag -------------------------------------------------------------------------------------------

    private static double balance(String tag) {
        return new FastagDao().findByTagId(tag).orElseThrow().getBalance();
    }

    @Test
    @DisplayName("FASTag top-up, link and status change roll back on audit failure and are attributed")
    void fastagAtomicAndAttributed() throws SQLException {
        FastagService fastag = new FastagService();
        signInAdmin();
        fastag.linkTag("TAG-T1", "UP32AB4321", 100.0, "admin");
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'FASTAG_LINK' AND username = 'admin'"));

        failAudit();
        assertThrows(AuditLogException.class, () -> fastag.topUp("TAG-T1", 50.0, "admin", "Recharge"));
        assertEquals(100.0, balance("TAG-T1"), 0.001, "top-up rolled back");
        assertThrows(AuditLogException.class, () -> fastag.setStatus("TAG-T1", "BLOCKED", "admin", "Lost tag"));
        assertEquals("ACTIVE", new FastagDao().findByTagId("TAG-T1").orElseThrow().getStatus(), "status change rolled back");
        assertThrows(AuditLogException.class, () -> fastag.linkTag("TAG-T2", "UP32AB5432", 10.0, "admin"));
        assertTrue(new FastagDao().findByTagId("TAG-T2").isEmpty(), "link rolled back");

        allowAudit();
        fastag.topUp("TAG-T1", 50.0, "not-the-user", "Recharge");
        assertEquals(150.0, balance("TAG-T1"), 0.001);
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'FASTAG_TOPUP' AND username = 'admin'"));
        assertEquals(0, count("SELECT COUNT(*) FROM audit_log WHERE username = 'not-the-user'"));

        assertThrows(IllegalArgumentException.class, () -> fastag.setStatus("TAG-T1", "DELETED", "admin", "x"));
        assertThrows(IllegalArgumentException.class, () -> fastag.topUp("TAG-T1", Double.NaN, "admin", "x"));
        assertThrows(IllegalArgumentException.class, () -> fastag.linkTag("TAG-T3", "UP32AB6543", -5.0, "admin"));
    }

    @Test
    @DisplayName("Re-linking an existing tag records the plate and balance it replaced")
    void relinkRecordsPreviousState() throws SQLException {
        FastagService fastag = new FastagService();
        signInAdmin();
        fastag.linkTag("TAG-R1", "UP32AB1111", 500.0, "admin");
        fastag.linkTag("TAG-R1", "UP32AB2222", 0.0, "admin");
        String old = value("SELECT old_value FROM audit_log WHERE action = 'FASTAG_LINK' ORDER BY rowid DESC LIMIT 1");
        assertNotNull(old);
        assertTrue(old.contains("UP32AB1111") && old.contains("500"), old);
    }

    @Test
    @DisplayName("Concurrent top-ups never lose an update")
    void concurrentTopUpsAreExact() throws Exception {
        FastagService fastag = new FastagService();
        fastag.linkTag("TAG-C1", "UP32AB7777", 0.0, "admin");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 6; t++) {
            futures.add(pool.submit(() -> {
                FastagService own = new FastagService();
                for (int i = 0; i < 10; i++) own.topUp("TAG-C1", 10.0, "system", "load test");
                return null;
            }));
        }
        for (Future<?> f : futures) f.get(120, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(600.0, balance("TAG-C1"), 0.001);
        assertEquals(60, count("SELECT COUNT(*) FROM audit_log WHERE action = 'FASTAG_TOPUP'"));
        assertTrue(new AuditLogDao().verifyIntegrity().isValid());
    }

    // ---- tariffs and settings ------------------------------------------------------------------------------

    @Test
    @DisplayName("Setting, tariff and peak-setting changes roll back entirely (incl. history) on audit failure")
    void settingsAndTariffsAtomic() throws SQLException {
        SettingsService settings = new SettingsService();
        TariffService tariffs = new TariffService(new SystemSettingsDao());
        signInAdmin();
        String graceBefore = new SystemSettingsDao().getString("grace_period_minutes", "");
        String carBefore = new TariffDao().findByVehicleType(VehicleType.CAR).map(TariffRate::toString).orElse("NONE");
        String peakBefore = new SystemSettingsDao().getString("peak_multiplier", "");
        long historyBefore = count("SELECT COUNT(*) FROM settings_history");

        failAudit();
        assertThrows(AuditLogException.class, () -> settings.updateSetting("grace_period_minutes", "77", "Grace period"));
        assertThrows(AuditLogException.class, () -> settings.updateTariff(new TariffRate(VehicleType.CAR, 55, 35, 400, 120, 2500)));
        assertThrows(AuditLogException.class, () -> tariffs.setPeakSettings(3.5, true));

        assertEquals(graceBefore, new SystemSettingsDao().getString("grace_period_minutes", ""));
        assertEquals(carBefore, new TariffDao().findByVehicleType(VehicleType.CAR).map(TariffRate::toString).orElse("NONE"));
        assertEquals(peakBefore, new SystemSettingsDao().getString("peak_multiplier", ""));
        assertEquals(historyBefore, count("SELECT COUNT(*) FROM settings_history"), "no orphan history rows");

        allowAudit();
        settings.updateSetting("grace_period_minutes", "77", "Grace period");
        settings.updateTariff(new TariffRate(VehicleType.CAR, 55, 35, 400, 120, 2500));
        tariffs.setPeakSettings(3.5, true);
        assertEquals("77", new SystemSettingsDao().getString("grace_period_minutes", ""));
        assertEquals("3.5", new SystemSettingsDao().getString("peak_multiplier", ""));
        assertEquals(historyBefore + 2, count("SELECT COUNT(*) FROM settings_history"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'SETTING_UPDATED' AND username = 'admin'"));
        assertEquals(2, count("SELECT COUNT(*) FROM audit_log WHERE action = 'TARIFF_UPDATED' AND username = 'admin'"));

        // Unchanged value: nothing written
        settings.updateSetting("grace_period_minutes", "77", "Grace period");
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'SETTING_UPDATED'"));
    }
}

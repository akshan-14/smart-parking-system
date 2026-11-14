package com.smartparking.security;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.exception.AccountLockedException;
import com.smartparking.exception.AuditLogException;
import com.smartparking.exception.AuthException;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.User;
import com.smartparking.repository.AuditLogDao;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.PaymentDao;
import com.smartparking.repository.SqliteTransaction;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.repository.UserDao;
import com.smartparking.service.AuditLogService;
import com.smartparking.service.AuthService;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit logging for security-critical operations: failures are never swallowed, the operation and its entry are
 * committed or rolled back together, a chain-head read failure aborts the write, and concurrent writers cannot fork
 * the hash chain.
 */
@DisplayName("Security: reliable audit logging for security-critical operations")
public class AuditLogReliabilityTest {

    private AuditLogService auditService;
    private AuthService auth;
    private UserDao userDao;

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_audit_reliability");
        DatabaseInitializer.initializeDatabase();
        userDao = new UserDao();
        auditService = new AuditLogService(new AuditLogDao());
        auth = new AuthService(userDao, new SystemSettingsDao(), auditService);
    }

    @AfterEach
    void cleanUp() throws SQLException {
        UserSession.getInstance().clearSession();
        allowAuditWrites();
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private static void exec(String sql) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** Simulates an audit storage failure: every INSERT into audit_log aborts (reads keep working). */
    private static void failAuditWrites() throws SQLException {
        exec("CREATE TRIGGER IF NOT EXISTS test_fail_audit BEFORE INSERT ON audit_log "
                + "BEGIN SELECT RAISE(ABORT, 'simulated audit storage failure'); END;");
    }

    private static void allowAuditWrites() throws SQLException {
        exec("DROP TRIGGER IF EXISTS test_fail_audit;");
    }

    private static long count(String sql) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static long auditRows() throws SQLException {
        return count("SELECT COUNT(*) FROM audit_log;");
    }

    private User user(String name) {
        return userDao.findByUsername(name).orElseThrow();
    }

    /** Signs in as admin with full access (completing the forced password change). */
    private void signInAsAdmin() {
        User admin = auth.authenticate("admin", "Admin@123").orElseThrow();
        auth.completeForcedPasswordChange(admin, "Admin@123", "Rotated#Admin2026");
    }

    private static boolean hasSuppressed(Throwable t, Class<? extends Throwable> type) {
        return Arrays.stream(t.getSuppressed()).anyMatch(type::isInstance);
    }

    // ---- audit-write failure + transaction rollback ---------------------------------------------------------

    @Test
    @DisplayName("Password change is rolled back when its audit entry cannot be written")
    void passwordChangeRollsBack() throws SQLException {
        int customerId = user("customer1").getUserId();
        long rowsBefore = auditRows();
        failAuditWrites();

        assertThrows(AuditLogException.class, () -> auth.changePassword(customerId, "Customer@123", "Changed#2026x"));

        User after = user("customer1");
        assertTrue(PasswordHasher.verify("Customer@123", after.getSalt(), after.getPasswordHash()),
                "the password must be unchanged");
        assertEquals(rowsBefore, auditRows());
        assertEquals(1, auditService.getStrictFailureCount(), "the failure is reported");

        allowAuditWrites();
        auth.changePassword(customerId, "Customer@123", "Changed#2026x");
        User changed = user("customer1");
        assertTrue(PasswordHasher.verify("Changed#2026x", changed.getSalt(), changed.getPasswordHash()));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'PASSWORD_CHANGE';"));
    }

    @Test
    @DisplayName("Admin user-management actions are rolled back when the audit entry fails")
    void userManagementRollsBack() throws SQLException {
        signInAsAdmin();
        int attendantId = user("attendant1").getUserId();
        failAuditWrites();

        assertThrows(AuditLogException.class, () -> auth.adminCreateUser("New Attendant", "9811223344",
                "n@test.com", "attendant9", "Attend#2026x", UserRole.ATTENDANT, "Night"));
        assertTrue(userDao.findByUsername("attendant9").isEmpty(), "no user without a USER_CREATED record");

        assertThrows(AuditLogException.class, () -> auth.adminDeactivateUser(attendantId));
        assertTrue(user("attendant1").isActive(), "deactivation rolled back");

        assertThrows(AuditLogException.class, () -> auth.adminResetPassword(attendantId, "Reset#2026xx"));
        User attendant = user("attendant1");
        assertTrue(PasswordHasher.verify("Attendant@123", attendant.getSalt(), attendant.getPasswordHash()),
                "reset rolled back");

        assertThrows(AuditLogException.class, () -> auth.registerCustomer("New Customer", "9811223355",
                "newcust", "NewCust#2026", true));
        assertTrue(userDao.findByUsername("newcust").isEmpty(), "registration rolled back");

        allowAuditWrites();
        auth.adminDeactivateUser(attendantId);
        assertFalse(user("attendant1").isActive());
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'USER_DEACTIVATED';"));
    }

    @Test
    @DisplayName("Sign-in is refused, with no session, when LOGIN_SUCCESS cannot be recorded")
    void loginSuccessFailsClosed() throws SQLException {
        String lastLoginBefore = user("attendant1").getLastLoginAt() == null ? null : user("attendant1").getLastLoginAt().toString();
        failAuditWrites();

        assertThrows(AuditLogException.class, () -> auth.authenticate("attendant1", "Attendant@123"));
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
        User after = user("attendant1");
        assertEquals(lastLoginBefore, after.getLastLoginAt() == null ? null : after.getLastLoginAt().toString(),
                "last-login update rolled back");

        allowAuditWrites();
        assertTrue(auth.authenticate("attendant1", "Attendant@123").isPresent());
    }

    @Test
    @DisplayName("An audit outage never weakens brute-force lockout")
    void lockoutStillEnforcedWhenAuditFails() throws SQLException {
        failAuditWrites();
        for (int i = 1; i <= 4; i++) {
            AuthException e = assertThrows(AuthException.class, () -> auth.authenticate("attendant1", "Wrong@Pass1"));
            assertTrue(hasSuppressed(e, AuditLogException.class), "the audit failure is attached, not dropped");
        }
        assertEquals(4, user("attendant1").getFailedAttempts(), "failed attempts still counted");
        assertThrows(AccountLockedException.class, () -> auth.authenticate("attendant1", "Wrong@Pass1"));
        assertNotNull(user("attendant1").getLockedUntil(), "account still locked");
        assertThrows(AccountLockedException.class, () -> auth.authenticate("attendant1", "Attendant@123"));
        assertTrue(auditService.getStrictFailureCount() >= 5);
    }

    @Test
    @DisplayName("Failed and unknown-user logins are atomic with their audit entries when the log works")
    void failedLoginAuditedAtomically() throws SQLException {
        assertThrows(AuthException.class, () -> auth.authenticate("attendant1", "Wrong@Pass1"));
        assertThrows(AuthException.class, () -> auth.authenticate("nobody", "Wrong@Pass1"));
        assertEquals(1, user("attendant1").getFailedAttempts());
        assertEquals(2, count("SELECT COUNT(*) FROM audit_log WHERE action = 'LOGIN_FAILURE';"));
    }

    @Test
    @DisplayName("Access is still denied when the ACCESS_DENIED entry cannot be written, and the failure is surfaced")
    void accessDeniedFailsClosed() throws SQLException {
        User attendant = auth.authenticate("attendant1", "Attendant@123").orElseThrow();
        failAuditWrites();
        long before = AuditLogService.getInstance().getStrictFailureCount();

        SecurityException denied = assertThrows(SecurityException.class,
                () -> AccessControl.checkPermission(attendant, Permission.MANAGE_USERS));
        assertTrue(denied.getMessage().contains("Access Denied"));
        assertTrue(hasSuppressed(denied, AuditLogException.class));
        assertEquals(before + 1, AuditLogService.getInstance().getStrictFailureCount());
    }

    @Test
    @DisplayName("Only explicitly low-risk events continue after a failed write, and they are reported visibly")
    void lowRiskContinuesVisiblyOthersThrow() throws SQLException {
        List<AuditLogService.AuditFailure> reported = new ArrayList<>();
        auditService.addFailureListener(reported::add);
        failAuditWrites();

        assertDoesNotThrow(() -> auditService.logWithActor(null, "SYSTEM", "SYSTEM", "NOTIFICATION_SENT",
                "NOTIFICATION", "1", null, null, "test", "SUCCESS"));
        assertEquals(1, auditService.getLowRiskFailureCount());
        assertEquals(1, reported.size());
        assertTrue(reported.get(0).continued());

        // Security-critical and unclassified actions are strict
        assertThrows(AuditLogException.class, () -> auditService.logWithActor(null, "SYSTEM", "SYSTEM",
                "ACCOUNT_LOCKOUT", "USER", "1", null, null, "test", "FAILURE"));
        assertThrows(AuditLogException.class, () -> auditService.logWithActor(null, "SYSTEM", "SYSTEM",
                "TARIFF_UPDATED", "TARIFF", "1", null, null, "test", "SUCCESS"));
        assertEquals(2, auditService.getStrictFailureCount());
        assertEquals(3, reported.size());
        assertFalse(reported.get(2).continued());

        // Signing out always works, even while audit writes fail
        allowAuditWrites();
        auth.authenticate("customer1", "Customer@123");
        failAuditWrites();
        assertDoesNotThrow(() -> auth.logout());
        assertTrue(UserSession.getInstance().getCurrentUser().isEmpty());
    }

    // ---- previous-hash read failure ---------------------------------------------------------------------------

    /** A DAO whose chain-head read fails, as with a corrupt page or I/O error on audit_log. */
    private static final class UnreadableChainHeadDao extends AuditLogDao {
        @Override
        protected String readLatestRowHash(Connection conn) throws SQLException {
            throw new SQLException("simulated I/O error reading audit chain head");
        }
    }

    @Test
    @DisplayName("A chain-head read failure aborts the write instead of restarting the chain at GENESIS")
    void chainHeadReadFailureIsLoud() throws SQLException {
        long rowsBefore = auditRows();
        AuditLogDao broken = new UnreadableChainHeadDao();

        AuditLogException e = assertThrows(AuditLogException.class, () -> broken.append(1, "admin", "ADMIN",
                "PASSWORD_CHANGE", "USER", "1", null, null, "test", "LOCAL", "SUCCESS"));
        assertTrue(e.getMessage().contains("hash chain head"));
        assertThrows(AuditLogException.class, broken::getLatestRowHash);
        assertEquals(rowsBefore, auditRows(), "nothing written with a guessed previous hash");

        // ...and the security operation using it is rolled back
        AuthService authWithBrokenLog = new AuthService(userDao, new SystemSettingsDao(), new AuditLogService(broken));
        int customerId = user("customer1").getUserId();
        assertThrows(AuditLogException.class,
                () -> authWithBrokenLog.changePassword(customerId, "Customer@123", "Changed#2026x"));
        User after = user("customer1");
        assertTrue(PasswordHasher.verify("Customer@123", after.getSalt(), after.getPasswordHash()));
        assertTrue(new AuditLogDao().verifyIntegrity().isValid());
    }

    @Test
    @DisplayName("A legacy unhashed newest row is reported and the chain continues from the newest hashed row")
    void legacyUnhashedHeadRow() throws SQLException {
        AuditLogDao dao = new AuditLogDao();
        dao.append(null, "SYSTEM", "SYSTEM", "LOGIN_SUCCESS", "USER", "1", null, null, "seed", "LOCAL", "SUCCESS");
        String hashedHead = dao.getLatestRowHash();
        exec("INSERT INTO audit_log (action, performed_by, timestamp) VALUES ('LEGACY_EVENT', 'old', '2020-01-01 00:00:00');");

        assertEquals(hashedHead, dao.getLatestRowHash(), "no silent restart at GENESIS");
        assertFalse(dao.verifyIntegrity().isValid(), "the unhashed row is still reported by Verify Integrity");
    }

    // ---- concurrency ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Concurrent writers (separate DAO instances, standalone and in-transaction) never fork the chain")
    void concurrentWritesKeepOneChain() throws Exception {
        long rowsBefore = auditRows();
        int threads = 8;
        int perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int thread = t;
            futures.add(pool.submit(() -> {
                AuditLogDao dao = new AuditLogDao(); // a separate instance per thread, as in the application
                start.await();
                for (int i = 0; i < perThread; i++) {
                    if (thread % 2 == 0) {
                        dao.append(thread, "t" + thread, "SYSTEM", "CONCURRENCY_TEST", "TEST", thread + "-" + i,
                                null, null, "standalone", "LOCAL", "SUCCESS");
                    } else {
                        final int n = i;
                        SqliteTransaction.immediate(conn -> {
                            dao.append(conn, thread, "t" + thread, "SYSTEM", "CONCURRENCY_TEST", "TEST",
                                    thread + "-" + n, null, null, "in-transaction", "LOCAL", "SUCCESS");
                            return null;
                        });
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS); // rethrows any writer failure
        }
        pool.shutdown();

        assertEquals(rowsBefore + (long) threads * perThread, auditRows(), "every entry written exactly once");
        Set<String> prevHashes = new HashSet<>();
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT prev_hash FROM audit_log;")) {
            while (rs.next()) {
                assertTrue(prevHashes.add(rs.getString(1)), "two entries chained onto the same previous hash (fork)");
            }
        }
        AuditLogDao.IntegrityResult integrity = new AuditLogDao().verifyIntegrity();
        assertTrue(integrity.isValid(), integrity.message());
    }

    // ---- refunds ----------------------------------------------------------------------------------------------

    private static int insertCompletedPayment() throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO payments (amount, payment_method, transaction_ref, "
                     + "status, payment_time) VALUES (100.0, 'CASH', ?, 'COMPLETED', '2026-11-14 10:00:00');",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "TXN-TEST-" + System.nanoTime());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("Refunds are hash-chained and roll back when the audit entry fails")
    void refundAuditIsChainedAndAtomic() throws SQLException {
        PaymentDao payments = new PaymentDao();
        int paymentId = insertCompletedPayment();

        failAuditWrites();
        assertThrows(AuditLogException.class, () -> payments.refund(paymentId, "Customer overcharged", "admin"));
        assertEquals(1, count("SELECT COUNT(*) FROM payments WHERE payment_id = " + paymentId + " AND status = 'COMPLETED';"),
                "refund rolled back");

        allowAuditWrites();
        assertTrue(payments.refund(paymentId, "Customer overcharged", "admin"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'PAYMENT_REFUND' AND row_hash IS NOT NULL;"));
        AuditLogDao.IntegrityResult integrity = new AuditLogDao().verifyIntegrity();
        assertTrue(integrity.isValid(), "a refund must not break the chain: " + integrity.message());
        assertFalse(payments.refund(paymentId, "Customer overcharged", "admin"), "already refunded");
    }
}

package com.smartparking.security;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.exception.AuditLogException;
import com.smartparking.repository.AuditLogDao;
import com.smartparking.repository.ConsentDao;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.UserDao;
import com.smartparking.service.AuditLogService;
import com.smartparking.service.AuthService;
import com.smartparking.service.ConsentService;
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
 * A self-registered account and its mandatory DPDP registration consent (plus their audit entries) are committed
 * together; consent changes are committed together with their audit entries.
 */
@DisplayName("DPDP: registration consent and consent changes are atomic")
public class RegistrationConsentAtomicityTest {

    private AuthService auth;
    private ConsentService consents;
    private final UserDao userDao = new UserDao();

    @BeforeEach
    void freshInstall() {
        TestDatabase.useFresh("test_registration_consent");
        DatabaseInitializer.initializeDatabase();
        auth = new AuthService();
        consents = new ConsentService(new ConsentDao(), AuditLogService.getInstance());
    }

    @AfterEach
    void cleanUp() throws SQLException {
        UserSession.getInstance().clearSession();
        exec("DROP TRIGGER IF EXISTS test_fail_consent;");
        exec("DROP TRIGGER IF EXISTS test_fail_audit;");
    }

    private static void exec(String sql) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static long count(String sql) throws SQLException {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static void failConsentWrites() throws SQLException {
        exec("CREATE TRIGGER test_fail_consent BEFORE INSERT ON consents BEGIN SELECT RAISE(ABORT, 'simulated consent write failure'); END;");
    }

    private static void failAuditWrites() throws SQLException {
        exec("CREATE TRIGGER test_fail_audit BEFORE INSERT ON audit_log BEGIN SELECT RAISE(ABORT, 'simulated audit failure'); END;");
    }

    private void register(String username) {
        auth.registerCustomer("Test Customer", "9811223366", username, "Mine#2026xx", true);
    }

    @Test
    @DisplayName("Successful registration stores the account, the consent and both audit entries")
    void registrationStoresConsent() throws SQLException {
        register("newcust");
        assertTrue(userDao.findByUsername("newcust").isPresent());
        assertTrue(consents.hasValidConsent("newcust", "REGISTRATION"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'USER_REGISTERED' AND entity_id IS NOT NULL;"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CONSENT_RECORDED' AND entity_id = 'newcust';"));
        assertTrue(new AuditLogDao().verifyIntegrity().isValid());
    }

    @Test
    @DisplayName("If the consent cannot be stored, no account is created")
    void consentFailureRollsBackAccount() throws SQLException {
        long auditBefore = count("SELECT COUNT(*) FROM audit_log;");
        failConsentWrites();

        assertThrows(RuntimeException.class, () -> register("newcust"));

        assertTrue(userDao.findByUsername("newcust").isEmpty(), "no account without its consent record");
        assertEquals(auditBefore, count("SELECT COUNT(*) FROM audit_log;"));
    }

    @Test
    @DisplayName("If the audit entries cannot be written, neither account nor consent is stored")
    void auditFailureRollsBackAccountAndConsent() throws SQLException {
        failAuditWrites();
        assertThrows(AuditLogException.class, () -> register("newcust"));
        assertTrue(userDao.findByUsername("newcust").isEmpty());
        assertEquals(0, count("SELECT COUNT(*) FROM consents WHERE user_id_or_plate = 'newcust';"));
    }

    @Test
    @DisplayName("Registration without consent is refused and stores nothing")
    void noConsentNoAccount() {
        assertThrows(IllegalArgumentException.class,
                () -> auth.registerCustomer("Test Customer", "9811223366", "newcust", "Mine#2026xx", false));
        assertTrue(userDao.findByUsername("newcust").isEmpty());
    }

    @Test
    @DisplayName("Consent recording and withdrawal roll back if their audit entry cannot be written")
    void consentChangesAreAtomic() throws SQLException {
        consents.recordConsent("UP32AB9999", "MARKETING", "v1.0");
        assertTrue(consents.hasValidConsent("UP32AB9999", "MARKETING"));

        failAuditWrites();
        assertThrows(AuditLogException.class, () -> consents.withdrawConsent("UP32AB9999", "MARKETING"));
        assertTrue(consents.hasValidConsent("UP32AB9999", "MARKETING"), "withdrawal rolled back");
        assertThrows(AuditLogException.class, () -> consents.recordConsent("UP32AB8888", "MARKETING", "v1.0"));
        assertFalse(consents.hasValidConsent("UP32AB8888", "MARKETING"), "consent rolled back");

        exec("DROP TRIGGER test_fail_audit;");
        consents.withdrawConsent("UP32AB9999", "MARKETING");
        assertFalse(consents.hasValidConsent("UP32AB9999", "MARKETING"));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CONSENT_WITHDRAWN';"));
    }
}

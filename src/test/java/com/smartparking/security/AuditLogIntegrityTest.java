package com.smartparking.security;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.repository.AuditLogDao;
import com.smartparking.repository.DatabaseInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: Immutable Append-Only Audit Log & Tamper Verification Tests")
public class AuditLogIntegrityTest {

    private AuditLogDao auditLogDao;

    @BeforeEach
    void setUp() {
        DatabaseInitializer.initializeDatabase();
        this.auditLogDao = new AuditLogDao();
    }

    @Test
    @DisplayName("Verify that AuditLogDao is strictly append-only (no update or delete methods exist)")
    void testStrictlyAppendOnly() {
        for (Method m : AuditLogDao.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            assertFalse(name.contains("update"), "AuditLogDao must not contain update methods: " + m.getName());
            assertFalse(name.contains("delete"), "AuditLogDao must not contain delete methods: " + m.getName());
            assertFalse(name.contains("remove"), "AuditLogDao must not contain remove methods: " + m.getName());
        }
    }

    @Test
    @DisplayName("Cryptographic SHA-256 hash chaining detects manual database tampering")
    void testTamperDetection() throws Exception {
        // Append 3 valid entries
        auditLogDao.append(1, "admin", "ADMIN", "LOGIN_SUCCESS", "USER", "1", null, null, "Normal Login", "127.0.0.1", "SUCCESS");
        auditLogDao.append(1, "admin", "ADMIN", "TARIFF_UPDATE", "SETTINGS", "PEAK", "1.0", "1.25", "Surge adjust", "127.0.0.1", "SUCCESS");
        auditLogDao.append(1, "admin", "ADMIN", "LOGOUT", "USER", "1", null, null, "Normal Logout", "127.0.0.1", "SUCCESS");

        // Integrity should be valid
        AuditLogDao.IntegrityResult initialCheck = auditLogDao.verifyIntegrity();
        assertTrue(initialCheck.isValid(), "Initial log chain must be valid: " + initialCheck.message());

        // Directly tamper with a record in SQLite behind the DAO's back
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE audit_log SET reason = 'Malicious alteration' WHERE rowid = (SELECT MAX(rowid) FROM audit_log);");
        }

        // Integrity check must detect the tampering
        AuditLogDao.IntegrityResult tamperedCheck = auditLogDao.verifyIntegrity();
        assertFalse(tamperedCheck.isValid(), "Integrity verification must fail when data is tampered");
        assertTrue(tamperedCheck.message().contains("tampering") || tamperedCheck.message().contains("broken"));
    }
}

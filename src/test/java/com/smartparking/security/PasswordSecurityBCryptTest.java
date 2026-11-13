package com.smartparking.security;

import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.Admin;
import com.smartparking.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: BCrypt Password Security Tests")
public class PasswordSecurityBCryptTest {

    @Test
    @DisplayName("BCrypt salt generates unique hashes for the same plain password")
    void testUniqueHashesForSamePassword() {
        String password = "SecurePassword@123";
        String hash1 = PasswordHasher.hash(password);
        String hash2 = PasswordHasher.hash(password);

        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2, "BCrypt must generate different salted hashes for identical passwords");
        assertTrue(hash1.startsWith("$2a$") || hash1.startsWith("$2b$"));
        assertTrue(hash2.startsWith("$2a$") || hash2.startsWith("$2b$"));
    }

    @Test
    @DisplayName("BCrypt verification succeeds for correct password and fails for incorrect password")
    void testVerifyPassword() {
        String password = "AdminPassword@2026";
        String hash = PasswordHasher.hash(password);

        assertTrue(PasswordHasher.verify(password, hash), "Correct password must verify successfully");
        assertFalse(PasswordHasher.verify("WrongPassword", hash), "Incorrect password must be rejected");
        assertFalse(PasswordHasher.verify("", hash), "Empty password must be rejected");
        assertFalse(PasswordHasher.verify(null, hash), "Null password must be rejected");
    }

    @Test
    @DisplayName("Plain text password is never stored or returned by User domain model")
    void testPlainTextNeverStored() {
        String plain = "SecretAdmin@789";
        String hash = PasswordHasher.hash(plain);
        User admin = new Admin(1, "admin_test", hash, "", "Test Admin", "admin@test.gov.in", "9876543210");

        assertNotEquals(plain, admin.getPasswordHash(), "Stored hash must never match raw plain text");
        assertEquals(hash, admin.getPasswordHash());
        assertFalse(admin.toString().contains(plain), "toString() must never leak plain text passwords");
    }
}

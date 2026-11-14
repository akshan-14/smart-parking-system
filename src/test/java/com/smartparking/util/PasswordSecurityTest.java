package com.smartparking.util;

import com.smartparking.support.TestDatabase;
import com.smartparking.model.user.User;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.service.AuthService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Password Security & Salted Hashing Tests")
class PasswordSecurityTest {

    /** AuthService.login() starts a real UserSession; clear it so the signed-in role cannot leak into later test classes. */
    @AfterEach
    void clearLoginSession() {
        com.smartparking.security.UserSession.getInstance().clearSession();
    }

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_security");
        DatabaseInitializer.initializeDatabase();
    }

    @Test
    @DisplayName("Salt generation produces unique cryptographic salts")
    void testSaltGeneration() {
        String salt1 = PasswordUtils.generateSalt();
        String salt2 = PasswordUtils.generateSalt();

        assertNotNull(salt1);
        assertNotNull(salt2);
        assertFalse(salt1.isEmpty());
        assertFalse(salt2.isEmpty());
        assertNotEquals(salt1, salt2, "Subsequent salt generations must be unique");
    }

    @Test
    @DisplayName("Hash password produces deterministic output for same salt and different for different salts")
    void testSaltedHashing() {
        String password = "SecretPassword123!";
        String saltA = PasswordUtils.generateSalt();
        String saltB = PasswordUtils.generateSalt();

        String hashA1 = PasswordUtils.hashPassword(password, saltA);
        String hashA2 = PasswordUtils.hashPassword(password, saltA);
        String hashB = PasswordUtils.hashPassword(password, saltB);

        assertEquals(hashA1, hashA2, "Same password and salt must produce identical hash");
        assertNotEquals(hashA1, hashB, "Different salts must produce distinct hashes");
    }

    @Test
    @DisplayName("Password verification succeeds for matching password and fails for invalid password")
    void testPasswordVerification() {
        String password = "MySecurePassword";
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt);

        assertTrue(PasswordUtils.verifyPassword("MySecurePassword", salt, hash));
        assertFalse(PasswordUtils.verifyPassword("WrongPassword", salt, hash));
        assertFalse(PasswordUtils.verifyPassword("MySecurePassword", "differentsalt", hash));
    }

    @Test
    @DisplayName("AuthService authenticates seeded users using salted credentials")
    void testAuthServiceWithSaltedUsers() {
        AuthService authService = new AuthService();

        // Admin authentication with salted hash
        Optional<User> adminOpt = authService.authenticate("admin", "admin123");
        assertTrue(adminOpt.isPresent(), "Admin must authenticate successfully with seeded salted hash");
        assertEquals("admin", adminOpt.get().getUsername());
        assertNotNull(adminOpt.get().getSalt());
        assertFalse(adminOpt.get().getSalt().isEmpty());

        // Attendant authentication
        Optional<User> attendantOpt = authService.authenticate("attendant1", "attendant123");
        assertTrue(attendantOpt.isPresent());

        // Incorrect password fails with AuthException
        assertThrows(com.smartparking.exception.AuthException.class, () -> authService.authenticate("admin", "wrongpassword"));
    }
}

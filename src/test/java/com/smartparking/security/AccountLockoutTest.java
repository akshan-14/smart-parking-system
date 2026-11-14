package com.smartparking.security;

import com.smartparking.support.TestDatabase;
import com.smartparking.exception.AccountLockedException;
import com.smartparking.exception.AuthException;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.User;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.UserDao;
import com.smartparking.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: Account Lockout & Inactivity Security Tests")
public class AccountLockoutTest {

    /** Isolated throw-away database: never touch the real smart_parking.db or another class's file. */
    @BeforeAll
    static void isolateDatabase() {
        TestDatabase.useFresh("test_account_lockout");
    }

    /** AuthService.login() starts a real UserSession; clear it so the signed-in role cannot leak into later test classes. */
    @AfterEach
    void clearLoginSession() {
        com.smartparking.security.UserSession.getInstance().clearSession();
    }

    private AuthService authService;
    private UserDao userDao;

    @BeforeEach
    void setUp() {
        DatabaseInitializer.initializeDatabase();
        this.userDao = new UserDao();
        this.authService = new AuthService();
    }

    @Test
    @DisplayName("Account locks after 5 consecutive failed attempts and unlocks after timeout")
    void testAccountLockoutFlow() {
        String testUser = "lockout_tester";
        // Ensure clean user
        userDao.findByUsername(testUser).ifPresent(u -> {
            userDao.updateFailedAttempts(u.getUserId(), 0, null);
            userDao.setActiveStatus(u.getUserId(), true);
        });

        // Register user
        User user = userDao.findByUsername(testUser).orElseGet(() ->
                authService.registerCustomer("Lockout Tester", "9812345678", testUser, "ValidPass@123", true));

        // Reset state
        userDao.updateFailedAttempts(user.getUserId(), 0, null);

        // 4 consecutive bad attempts -> AuthException
        for (int i = 1; i <= 4; i++) {
            assertThrows(AuthException.class, () -> authService.authenticate(testUser, "BadPassword!"));
            User current = userDao.findByUsername(testUser).orElseThrow();
            assertEquals(i, current.getFailedAttempts(), "Failed attempt count must increment");
            assertNull(current.getLockedUntil(), "User should not be locked until 5th failure");
        }

        // 5th bad attempt -> AccountLockedException
        AccountLockedException ale = assertThrows(AccountLockedException.class,
                () -> authService.authenticate(testUser, "BadPassword!"));
        assertTrue(ale.getMessage().contains("Account locked"));
        assertNotNull(ale.getLockedUntil());

        User lockedUser = userDao.findByUsername(testUser).orElseThrow();
        assertNotNull(lockedUser.getLockedUntil());
        assertTrue(lockedUser.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(10)));

        // Further attempt while locked -> AccountLockedException
        assertThrows(AccountLockedException.class, () -> authService.authenticate(testUser, "ValidPass@123"));

        // Simulate lockout expiration (set lockedUntil into the past)
        userDao.updateFailedAttempts(user.getUserId(), 5, LocalDateTime.now().minusMinutes(1));

        // Login with correct password now succeeds and resets lockout counter
        User unlocked = authService.authenticate(testUser, "ValidPass@123").orElseThrow();
        assertEquals(0, unlocked.getFailedAttempts(), "Successful login must reset failed attempts to 0");
        assertNull(unlocked.getLockedUntil(), "Lockout timestamp must be cleared on success");
    }
}

package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.user.User;
import com.smartparking.security.PasswordHasher;
import com.smartparking.security.UserSession;
import com.smartparking.service.AuthService;
import com.smartparking.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Startup seeding must create the default demo accounts on a fresh installation only, and must never reset an
 * existing user's password or other account data when the application restarts.
 */
@DisplayName("Demo account seeding is create-only and never overwrites existing users")
public class DemoAccountSeedingTest {

    private final UserDao userDao = new UserDao();

    @BeforeEach
    void freshDatabase() {
        // A new, empty database per test: every test starts from a genuinely fresh installation.
        TestDatabase.useFresh("test_demo_account_seeding");
        DatabaseInitializer.initializeDatabase();
    }

    @AfterEach
    void clearSession() {
        UserSession.getInstance().clearSession();
    }

    private User user(String username) {
        return userDao.findByUsername(username).orElseThrow(() -> new AssertionError(username + " missing"));
    }

    @Test
    @DisplayName("Fresh installation gets the documented default credentials")
    void freshInstallCreatesDefaults() {
        User admin = user("admin");
        User attendant = user("attendant1");
        User customer = user("customer1");

        assertTrue(PasswordHasher.verify("Admin@123", admin.getSalt(), admin.getPasswordHash()));
        assertTrue(PasswordHasher.verify("Attendant@123", attendant.getSalt(), attendant.getPasswordHash()));
        assertTrue(PasswordHasher.verify("Customer@123", customer.getSalt(), customer.getPasswordHash()));
        assertTrue(admin.isForcePasswordChange(), "admin must change the default password on first login");
        assertFalse(attendant.isForcePasswordChange());
        assertEquals("9876501003", customer.getPhone());
        assertTrue(new AuthService().authenticate("customer1", "Customer@123").isPresent());
    }

    @Test
    @DisplayName("Changed demo passwords survive reinitialization")
    void changedPasswordsSurviveRestart() {
        AuthService auth = new AuthService();
        int adminId = user("admin").getUserId();
        int attendantId = user("attendant1").getUserId();
        int customerId = user("customer1").getUserId();

        auth.changePassword(adminId, "Admin@123", "NewAdmin#2026");
        auth.changePassword(attendantId, "Attendant@123", "NewAttend#2026");
        auth.changePassword(customerId, "Customer@123", "NewCust#2026");

        DatabaseInitializer.initializeDatabase(); // simulate an application restart

        User admin = user("admin");
        User attendant = user("attendant1");
        User customer = user("customer1");
        assertTrue(PasswordHasher.verify("NewAdmin#2026", admin.getSalt(), admin.getPasswordHash()));
        assertTrue(PasswordHasher.verify("NewAttend#2026", attendant.getSalt(), attendant.getPasswordHash()));
        assertTrue(PasswordHasher.verify("NewCust#2026", customer.getSalt(), customer.getPasswordHash()));
        assertFalse(PasswordHasher.verify("Admin@123", admin.getSalt(), admin.getPasswordHash()),
                "restart must not restore the default admin password");
        assertFalse(PasswordHasher.verify("Attendant@123", attendant.getSalt(), attendant.getPasswordHash()));
        assertFalse(PasswordHasher.verify("Customer@123", customer.getSalt(), customer.getPasswordHash()));
        assertFalse(admin.isForcePasswordChange(),
                "restart must not re-impose the forced password change after the admin already changed it");

        assertTrue(auth.authenticate("customer1", "NewCust#2026").isPresent(), "new password signs in after restart");
        UserSession.getInstance().clearSession();
        assertTrue(auth.authenticate("attendant1", "NewAttend#2026").isPresent());
    }

    @Test
    @DisplayName("Other user-modified account data survives reinitialization")
    void otherAccountDataSurvivesRestart() throws Exception {
        User customer = user("customer1");
        customer.setPhone("9000000001");
        customer.setFullName("Priya S. (edited)");
        userDao.update(customer);

        // Customer removes one of the seeded demo vehicles
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM user_vehicles WHERE user_id = ? AND license_plate = 'UP14XY5678';")) {
            ps.setInt(1, customer.getUserId());
            assertEquals(1, ps.executeUpdate());
        }

        DatabaseInitializer.initializeDatabase();

        User after = user("customer1");
        assertEquals(customer.getUserId(), after.getUserId(), "the account must not be recreated");
        assertEquals("9000000001", after.getPhone());
        assertEquals("Priya S. (edited)", after.getFullName());
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM user_vehicles WHERE user_id = ? AND license_plate = 'UP14XY5678';")) {
            ps.setInt(1, after.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "a vehicle the user removed must not be re-added on restart");
            }
        }
    }

    @Test
    @DisplayName("A demo account removed by an administrator is not recreated with its known default password")
    void removedDemoAccountIsNotRecreated() throws Exception {
        int adminId = user("admin").getUserId();
        new AuthService().changePassword(adminId, "Admin@123", "Kept#Admin2026");
        String adminHashBefore = user("admin").getPasswordHash();

        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE username = 'attendant1';")) {
            assertEquals(1, ps.executeUpdate());
        }

        DatabaseInitializer.initializeDatabase();

        assertTrue(userDao.findByUsername("attendant1").isEmpty(),
                "restart must not resurrect a deleted account with the public default password");
        assertEquals(adminHashBefore, user("admin").getPasswordHash(), "existing admin hash must be byte-for-byte unchanged");
    }

    @Test
    @DisplayName("An existing admin whose username case changed is neither duplicated nor reset")
    void caseInsensitiveExistingAccountNotDuplicated() throws Exception {
        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET username = 'Admin', password_hash = ? WHERE username = 'admin';")) {
            ps.setString(1, PasswordHasher.hash("Custom#Admin2026"));
            assertEquals(1, ps.executeUpdate());
        }

        DatabaseInitializer.initializeDatabase();

        try (Connection c = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE LOWER(username) = 'admin';");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
        User admin = user("Admin");
        assertTrue(PasswordHasher.verify("Custom#Admin2026", admin.getSalt(), admin.getPasswordHash()));
    }
}

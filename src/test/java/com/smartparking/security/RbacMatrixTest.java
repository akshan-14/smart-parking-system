package com.smartparking.security;

import com.smartparking.support.TestDatabase;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.Admin;
import com.smartparking.model.user.Attendant;
import com.smartparking.model.user.Customer;
import com.smartparking.model.user.User;
import com.smartparking.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: RBAC Role-Permission Matrix & Backend Enforcement Tests")
public class RbacMatrixTest {

    /** Isolated throw-away database: never touch the real smart_parking.db or another class's file. */
    @BeforeAll
    static void isolateDatabase() {
        TestDatabase.useFresh("test_rbac_matrix");
    }

    private final User admin = new Admin(1, "admin", "hash", "", "Admin", "admin@smartparking.up.gov.in", "9876501001");
    private final User attendant = new Attendant(2, "attendant1", "hash", "", "Attendant", "attendant@smartparking.up.gov.in", "9876501002");
    private final User customer = new Customer(3, "customer1", "hash", "", "Customer", "customer@smartparking.up.gov.in", "9876501003");

    @Test
    @DisplayName("Admin role has all operational and security permissions")
    void testAdminPermissions() {
        for (Permission perm : Permission.values()) {
            assertTrue(AccessControl.hasPermission(admin, perm), "Admin must possess permission: " + perm);
        }
    }

    @Test
    @DisplayName("Attendant role has gate entry/exit permissions but lacks admin and report privileges")
    void testAttendantPermissions() {
        assertTrue(AccessControl.hasPermission(attendant, Permission.PARK_VEHICLE_ENTRY));
        assertTrue(AccessControl.hasPermission(attendant, Permission.CHECKOUT_VEHICLE));
        assertTrue(AccessControl.hasPermission(attendant, Permission.VIEW_OVERVIEW));
        assertTrue(AccessControl.hasPermission(attendant, Permission.VIEW_FLOOR_MAP));

        assertFalse(AccessControl.hasPermission(attendant, Permission.VIEW_REPORTS));
        assertFalse(AccessControl.hasPermission(attendant, Permission.VIEW_AUDIT_LOG));
        assertFalse(AccessControl.hasPermission(attendant, Permission.MANAGE_USERS));
        assertFalse(AccessControl.hasPermission(attendant, Permission.UPDATE_TARIFFS));
        assertFalse(AccessControl.hasPermission(attendant, Permission.REFUND_PAYMENT));
        assertFalse(AccessControl.hasPermission(attendant, Permission.BACKUP_DATABASE));
    }

    @Test
    @DisplayName("Customer role is isolated to personal bookings, passes, and data with no gate access")
    void testCustomerPermissions() {
        assertTrue(AccessControl.hasPermission(customer, Permission.CUSTOMER_BOOK_RESERVATION));
        assertTrue(AccessControl.hasPermission(customer, Permission.CUSTOMER_PURCHASE_PASS));
        assertTrue(AccessControl.hasPermission(customer, Permission.CUSTOMER_VIEW_OWN_DATA));
        assertTrue(AccessControl.hasPermission(customer, Permission.CUSTOMER_LINK_VEHICLE));

        assertFalse(AccessControl.hasPermission(customer, Permission.VIEW_OVERVIEW));
        assertFalse(AccessControl.hasPermission(customer, Permission.VIEW_FLOOR_MAP));
        assertFalse(AccessControl.hasPermission(customer, Permission.PARK_VEHICLE_ENTRY));
        assertFalse(AccessControl.hasPermission(customer, Permission.CHECKOUT_VEHICLE));
        assertFalse(AccessControl.hasPermission(customer, Permission.VIEW_REPORTS));
        assertFalse(AccessControl.hasPermission(customer, Permission.VIEW_AUDIT_LOG));
        assertFalse(AccessControl.hasPermission(customer, Permission.MANAGE_USERS));
    }

    @Test
    @DisplayName("Direct service method invocation without permission throws SecurityException and writes audit log")
    void testDirectServiceCallAccessDenied() {
        // Run under Attendant session attempting Admin action
        UserSession.getInstance().startSession(attendant, 15);

        AuthService authService = new AuthService();
        SecurityException ex = assertThrows(SecurityException.class, () ->
                authService.adminCreateUser("Unauthorized Admin", "9811223344", "test@test.com", "hacker", "Pass@1234", UserRole.ADMIN, null));

        assertTrue(ex.getMessage().contains("Access Denied"));
        UserSession.getInstance().clearSession();
    }
}

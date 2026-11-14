package com.smartparking.security;

import com.smartparking.exception.AuditLogException;
import com.smartparking.exception.PasswordChangeRequiredException;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.User;
import com.smartparking.service.AuditLogService;

import java.util.*;

/**
 * Centralized Role-Based Access Control (RBAC) engine.
 * Maps UserRoles to permitted actions and UI screens.
 * Enforces permissions at both service and presentation tiers.
 * Any unauthorized invocation generates a security exception and writes an ACCESS_DENIED audit log entry.
 */
public final class AccessControl {

    private static final Map<UserRole, Set<Permission>> ROLE_PERMISSIONS = new EnumMap<>(UserRole.class);
    private static final Map<UserRole, Set<String>> ROLE_SCREENS = new EnumMap<>(UserRole.class);

    static {
        // 1. ADMIN - Unrestricted system-wide privileges
        Set<Permission> adminPerms = EnumSet.allOf(Permission.class);
        ROLE_PERMISSIONS.put(UserRole.ADMIN, Collections.unmodifiableSet(adminPerms));

        Set<String> adminScreens = new LinkedHashSet<>(Arrays.asList(
                "DASHBOARD", "FLOORS", "ENTRY", "EXIT", "RESERVATIONS", "MEMBERSHIP",
                "SEARCH", "ALERTS", "REPORTS", "AUDIT_LOG", "USERS", "SETTINGS", "PRIVACY"
        ));
        ROLE_SCREENS.put(UserRole.ADMIN, Collections.unmodifiableSet(adminScreens));

        // 2. ATTENDANT - Gate operations, slot management, and customer assistance
        Set<Permission> attendantPerms = EnumSet.of(
                Permission.VIEW_OVERVIEW,
                Permission.VIEW_FLOOR_MAP,
                Permission.VIEW_ENTRY_GATE,
                Permission.VIEW_EXIT_GATE,
                Permission.VIEW_FIND_VEHICLE,
                Permission.VIEW_BOOKING,
                Permission.VIEW_PRIVACY_NOTICE,
                Permission.PARK_VEHICLE_ENTRY,
                Permission.CHECKOUT_VEHICLE,
                Permission.RELEASE_BAY
        );
        ROLE_PERMISSIONS.put(UserRole.ATTENDANT, Collections.unmodifiableSet(attendantPerms));

        Set<String> attendantScreens = new LinkedHashSet<>(Arrays.asList(
                "DASHBOARD", "FLOORS", "ENTRY", "EXIT", "RESERVATIONS", "SEARCH", "ALERTS", "PRIVACY"
        ));
        ROLE_SCREENS.put(UserRole.ATTENDANT, Collections.unmodifiableSet(attendantScreens));

        // 3. CUSTOMER - Self-service bookings, passes, own vehicle tracking, DPDP "My Data"
        Set<Permission> customerPerms = EnumSet.of(
                Permission.VIEW_BOOKING,
                Permission.VIEW_PASSES,
                Permission.VIEW_FIND_VEHICLE,
                Permission.VIEW_MY_DATA,
                Permission.VIEW_PRIVACY_NOTICE,
                Permission.CUSTOMER_VIEW_OWN_DATA,
                Permission.CUSTOMER_BOOK_RESERVATION,
                Permission.CUSTOMER_PURCHASE_PASS,
                Permission.CUSTOMER_LINK_VEHICLE,
                Permission.CUSTOMER_REQUEST_DELETION
        );
        ROLE_PERMISSIONS.put(UserRole.CUSTOMER, Collections.unmodifiableSet(customerPerms));

        Set<String> customerScreens = new LinkedHashSet<>(Arrays.asList(
                "RESERVATIONS", "MEMBERSHIP", "SEARCH", "MY_DATA", "PRIVACY"
        ));
        ROLE_SCREENS.put(UserRole.CUSTOMER, Collections.unmodifiableSet(customerScreens));
    }

    private AccessControl() {
    }

    /**
     * Checks if a user has a specific permission without throwing an exception.
     */
    public static boolean hasPermission(User user, Permission permission) {
        if (user == null || permission == null) return false;
        // A user who must change their password has no permissions until they have done so.
        if (requiresPasswordChange(user)) return false;
        Set<Permission> perms = ROLE_PERMISSIONS.get(user.getRole());
        return perms != null && perms.contains(permission);
    }

    /**
     * True when the user is flagged force_password_change and so may not use normal application functionality.
     */
    public static boolean requiresPasswordChange(User user) {
        return user != null && user.isForcePasswordChange();
    }

    /**
     * Gate for entering the application (main window, console session). Throws unless the user is signed in and does
     * not have a pending forced password change. GUI and console both go through this rule.
     */
    public static void requireFullAccess(User user) {
        if (user == null) {
            throw new SecurityException("Access Denied: no authenticated user.");
        }
        if (requiresPasswordChange(user)) {
            throw new PasswordChangeRequiredException(
                    "Access Denied: user [" + user.getUsername() + "] must change the password before continuing.");
        }
    }

    /**
     * Checks if the active session user has the requested permission.
     */
    public static boolean hasPermission(Permission permission) {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        return hasPermission(user, permission);
    }

    /**
     * Enforces permission on active session user.
     * Throws SecurityException and writes ACCESS_DENIED audit log if unauthorized.
     */
    public static void checkPermission(Permission permission) {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        checkPermission(user, permission);
    }

    /**
     * Enforces permission on specified user.
     * Throws SecurityException and writes ACCESS_DENIED audit log if unauthorized.
     */
    public static void checkPermission(User user, Permission permission) {
        if (!hasPermission(user, permission)) {
            String username = user != null ? user.getUsername() : "ANONYMOUS";
            String role = user != null ? user.getRole().name() : "NONE";
            Integer userId = user != null ? user.getUserId() : null;

            SecurityException denial = requiresPasswordChange(user)
                    ? new PasswordChangeRequiredException("Access Denied: password change required before ["
                            + permission.name() + "].")
                    : new SecurityException("Access Denied: Role [" + role + "] is not authorized for ["
                            + permission.name() + "].");

            // Log access denial to immutable audit log. The action is denied whether or not the entry can be
            // written; a failed write is reported by AuditLogService and attached to the exception.
            try {
                AuditLogService.getInstance().logWithActor(
                        userId,
                        username,
                        role,
                        "ACCESS_DENIED",
                        "SECURITY_RULE",
                        permission.name(),
                        null,
                        null,
                        requiresPasswordChange(user)
                                ? "Attempt to perform action before mandatory password change: " + permission.name()
                                : "Unauthorized attempt to perform action: " + permission.name(),
                        "FAILURE"
                );
            } catch (AuditLogException auditFailure) {
                denial.addSuppressed(auditFailure);
            }
            throw denial;
        }
    }

    /**
     * Checks if a screen/card ID is permitted for the given role.
     */
    public static boolean isScreenAllowed(UserRole role, String screenCardKey) {
        if (role == null || screenCardKey == null) return false;
        Set<String> screens = ROLE_SCREENS.get(role);
        return screens != null && screens.contains(screenCardKey.toUpperCase());
    }

    /**
     * Returns the set of all allowed UI screen card keys for the given role.
     */
    public static Set<String> getAllowedScreens(UserRole role) {
        if (role == null) return Collections.emptySet();
        return ROLE_SCREENS.getOrDefault(role, Collections.emptySet());
    }
}

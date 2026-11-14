package com.smartparking.service;

import com.smartparking.exception.AccountLockedException;
import com.smartparking.exception.AuditLogException;
import com.smartparking.exception.AuthException;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.Admin;
import com.smartparking.model.user.Attendant;
import com.smartparking.model.user.Customer;
import com.smartparking.model.user.User;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.repository.SqliteTransaction;
import com.smartparking.repository.UserDao;
import com.smartparking.security.AccessControl;
import com.smartparking.security.PasswordHasher;
import com.smartparking.security.Permission;
import com.smartparking.security.UserSession;
import com.smartparking.util.DateFormat;
import com.smartparking.util.InputValidator;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service managing user authentication, role authorization, account lockout,
 * and user lifecycle administration in compliance with Phase 6 security standards.
 */
public class AuthService {

    private final UserDao userDao;
    private final SystemSettingsDao settingsDao;
    private final AuditLogService auditLogService;

    public AuthService(UserDao userDao, SystemSettingsDao settingsDao, AuditLogService auditLogService) {
        this.userDao = userDao;
        this.settingsDao = settingsDao;
        this.auditLogService = auditLogService;
    }

    public AuthService() {
        this(new UserDao(), new SystemSettingsDao(), AuditLogService.getInstance());
    }

    /**
     * Authenticates a user against database credentials.
     * Enforces account lockout after 5 consecutive failed attempts.
     * Emits generic error messages to prevent username enumeration.
     */
    public Optional<User> authenticate(String username, String plainPassword) {
        if (username == null || username.trim().isEmpty() || plainPassword == null || plainPassword.isEmpty()) {
            throw new AuthException("Invalid username or password.");
        }

        String trimmedUser = username.trim();
        Optional<User> userOpt = userDao.findByUsername(trimmedUser);

        if (userOpt.isEmpty()) {
            throw auditedRejection(new AuthException("Invalid username or password."),
                    () -> auditLogService.logWithActor(null, trimmedUser, "ANONYMOUS", "LOGIN_FAILURE",
                            "USER", trimmedUser, null, null, "User not found", "FAILURE"));
        }

        User user = userOpt.get();

        if (!user.isActive()) {
            throw auditedRejection(new AuthException("Account is disabled. Please contact administrator."),
                    () -> auditLogService.logWithActor(user.getUserId(), user.getUsername(), user.getRole().name(),
                            "LOGIN_BLOCKED", "USER", String.valueOf(user.getUserId()), null, null,
                            "Inactive account login attempted", "FAILURE"));
        }

        // Check Lockout
        if (user.getLockedUntil() != null) {
            if (LocalDateTime.now().isBefore(user.getLockedUntil())) {
                String timeIST = DateFormat.formatISTTime(user.getLockedUntil());
                throw auditedRejection(
                        new AccountLockedException("Account locked. Try again after " + timeIST + " IST", user.getLockedUntil()),
                        () -> auditLogService.logWithActor(user.getUserId(), user.getUsername(), user.getRole().name(),
                                "LOGIN_BLOCKED_LOCKED", "USER", String.valueOf(user.getUserId()), null, null,
                                "Login attempt during lockout until " + timeIST, "FAILURE"));
            } else {
                // Lockout expired: reset failed attempts
                userDao.updateFailedAttempts(user.getUserId(), 0, null);
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
            }
        }

        // Verify the password against the stored hash only (BCrypt, or legacy salted SHA-256 for old rows).
        // There are deliberately no hard-coded alternative passwords: any bypass here would keep working after the
        // user changed their password.
        boolean passwordMatch = PasswordHasher.verify(plainPassword, user.getSalt(), user.getPasswordHash());

        if (passwordMatch) {
            // Reset failed counter, update last login and record LOGIN_SUCCESS atomically. If the audit entry
            // cannot be written, the whole sign-in is rolled back and refused (fail closed): no session starts.
            LocalDateTime now = LocalDateTime.now();
            SqliteTransaction.immediate(conn -> {
                userDao.updateLastLogin(conn, user.getUserId(), now);
                auditLogService.recordInTransaction(conn, user.getUserId(), user.getUsername(), user.getRole().name(),
                        "LOGIN_SUCCESS", "USER", String.valueOf(user.getUserId()), null, null,
                        "User logged in successfully", "SUCCESS");
                return null;
            });
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(now);

            int sessionTimeout = settingsDao.getInt("auth.session_timeout_minutes", 15);
            UserSession.getInstance().startSession(user, sessionTimeout);

            return Optional.of(user);
        } else {
            // Failed attempt: increment failed attempts
            int maxAttempts = settingsDao.getInt("auth.max_failed_attempts", 5);
            int lockoutMins = settingsDao.getInt("auth.lockout_duration_minutes", 15);
            int currentFailures = user.getFailedAttempts() + 1;
            LocalDateTime lockedUntil = null;

            String action;
            String reason;
            RuntimeException rejection;
            if (currentFailures >= maxAttempts) {
                lockedUntil = LocalDateTime.now().plusMinutes(lockoutMins);
                String timeIST = DateFormat.formatISTTime(lockedUntil);
                action = "ACCOUNT_LOCKOUT";
                reason = "Account locked after " + currentFailures + " failures until " + timeIST;
                rejection = new AccountLockedException("Account locked. Try again after " + timeIST + " IST", lockedUntil);
            } else {
                action = "LOGIN_FAILURE";
                reason = "Failed attempt #" + currentFailures;
                rejection = new AuthException("Invalid username or password.");
            }

            // Counter/lockout update and its audit entry are committed together.
            final LocalDateTime lockValue = lockedUntil;
            try {
                SqliteTransaction.immediate(conn -> {
                    userDao.updateFailedAttempts(conn, user.getUserId(), currentFailures, lockValue);
                    auditLogService.recordInTransaction(conn, user.getUserId(), user.getUsername(), user.getRole().name(),
                            action, "USER", String.valueOf(user.getUserId()), null, null, reason, "FAILURE");
                    return null;
                });
            } catch (AuditLogException auditFailure) {
                // The audit entry could not be written (already reported). An audit outage must never weaken
                // brute-force protection, so the counter/lockout is still applied on its own; the sign-in is
                // rejected either way.
                userDao.updateFailedAttempts(user.getUserId(), currentFailures, lockValue);
                rejection.addSuppressed(auditFailure);
            }
            throw rejection;
        }
    }

    /**
     * Writes the audit entry for a rejected sign-in and returns the rejection to throw. The sign-in is rejected
     * whether or not the entry could be written; a failed write is reported by AuditLogService and attached to the
     * rejection, never silently dropped.
     */
    private RuntimeException auditedRejection(RuntimeException rejection, Runnable audit) {
        try {
            audit.run();
        } catch (AuditLogException auditFailure) {
            rejection.addSuppressed(auditFailure);
        }
        return rejection;
    }

    /**
     * Signs out the currently authenticated user and clears the session.
     */
    public void logout() {
        UserSession session = UserSession.getInstance();
        User user = session.getCurrentUser().orElse(null);
        if (user != null) {
            auditLogService.logWithActor(user.getUserId(), user.getUsername(), user.getRole().name(),
                    "LOGOUT", "USER", String.valueOf(user.getUserId()), null, null,
                    "User signed out", "SUCCESS");
        }
        session.clearSession();
    }

    /**
     * Changes user's own password.
     */
    public void changePassword(int userId, String oldPassword, String newPassword) {
        User user = userDao.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!PasswordHasher.verify(oldPassword, user.getSalt(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password does not match.");
        }

        InputValidator.validatePassword(newPassword);
        String newHash = PasswordHasher.hash(newPassword);

        // Password update and PASSWORD_CHANGE entry commit together, or neither does.
        SqliteTransaction.immediate(conn -> {
            userDao.updatePassword(conn, userId, newHash);
            auditLogService.recordInTransaction(conn, userId, user.getUsername(), user.getRole().name(),
                    "PASSWORD_CHANGE", "USER", String.valueOf(userId), null, null,
                    "Password changed successfully", "SUCCESS");
            return null;
        });
    }

    /**
     * Completes a mandatory (force_password_change) password change for the signed-in user. This is the only way
     * out of the restricted state: the current password is re-verified, the new one must be valid and different,
     * and the flag is cleared in the database, on the given user object and on the session user.
     */
    public User completeForcedPasswordChange(User user, String currentPassword, String newPassword) {
        if (user == null) {
            throw new IllegalArgumentException("No user to update.");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("Enter a new password.");
        }
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("The new password must be different from the current password.");
        }
        changePassword(user.getUserId(), currentPassword, newPassword); // verifies, validates, clears flag in DB
        user.setForcePasswordChange(false);
        UserSession.getInstance().getCurrentUser()
                .filter(u -> u.getUserId() == user.getUserId())
                .ifPresent(u -> u.setForcePasswordChange(false));
        return user;
    }

    /**
     * Administrator resets a user's password.
     */
    public void adminResetPassword(int targetUserId, String newPassword) {
        AccessControl.checkPermission(Permission.MANAGE_USERS);
        User target = userDao.findById(targetUserId).orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        InputValidator.validatePassword(newPassword);
        String newHash = PasswordHasher.hash(newPassword);

        // The administrator knows this password, so it is temporary: the user must replace it at next sign-in.
        SqliteTransaction.immediate(conn -> {
            userDao.setTemporaryPassword(conn, targetUserId, newHash);
            auditLogService.recordInTransactionAsSessionUser(conn, "PASSWORD_RESET", "USER", String.valueOf(targetUserId),
                    null, "forced_change_at_next_sign_in=1",
                    "Admin reset password for user: " + target.getUsername() + " (must change at next sign-in)", "SUCCESS");
            return null;
        });
    }

    /**
     * Customer self-registration with DPDP Act, 2023 mandatory consent.
     */
    public User registerCustomer(String fullName, String mobile, String username,
                                 String plainPassword, boolean consentGiven) {
        if (!consentGiven) {
            throw new IllegalArgumentException("Consent to data collection is mandatory under the DPDP Act, 2023.");
        }

        InputValidator.validateFullName(fullName);
        InputValidator.validateMobile(mobile);
        InputValidator.validateUsername(username);
        InputValidator.validatePassword(plainPassword);

        if (userDao.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }

        String hash = PasswordHasher.hash(plainPassword);
        Customer customer = new Customer(0, username.toLowerCase(), hash, "", fullName.trim(),
                null, mobile.trim(), null, true, 0, null, LocalDateTime.now(), null, false);

        // Account and USER_REGISTERED entry commit together, or neither does.
        int newId = SqliteTransaction.immediate(conn -> {
            int id = userDao.save(conn, customer);
            auditLogService.recordInTransaction(conn, id, username.toLowerCase(), "CUSTOMER",
                    "USER_REGISTERED", "USER", String.valueOf(id),
                    null, null, "Customer self-registered with DPDP consent", "SUCCESS");
            return id;
        });

        // Record DPDP Consent (separate service/connection, so it runs after the commit above)
        ConsentService.getInstance().recordConsent(username.toLowerCase(), "REGISTRATION", "v1.0 (DPDP 2023)");

        return userDao.findById(newId).orElse(customer);
    }

    /**
     * Admin creates a system user (Admin or Attendant).
     */
    public User adminCreateUser(String fullName, String mobile, String email, String username,
                                String plainPassword, UserRole role, String extra) {
        AccessControl.checkPermission(Permission.MANAGE_USERS);

        InputValidator.validateFullName(fullName);
        InputValidator.validateMobile(mobile);
        InputValidator.validateUsername(username);
        InputValidator.validatePassword(plainPassword);

        if (userDao.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }

        String hash = PasswordHasher.hash(plainPassword);
        User user = switch (role) {
            // The last argument (force_password_change) is true: the administrator chose this initial password, so
            // the new user must replace it at first sign-in.
            case ADMIN -> new Admin(0, username.toLowerCase(), hash, "", fullName.trim(),
                    email != null ? email.trim() : "", mobile.trim(), true, 0, null, LocalDateTime.now(), null, true);
            case ATTENDANT -> new Attendant(0, username.toLowerCase(), hash, "", fullName.trim(),
                    email != null ? email.trim() : "", mobile.trim(), extra != null ? extra.trim() : "General",
                    true, 0, null, LocalDateTime.now(), null, true);
            case CUSTOMER -> new Customer(0, username.toLowerCase(), hash, "", fullName.trim(),
                    email != null ? email.trim() : "", mobile.trim(), extra != null ? extra.trim() : null,
                    true, 0, null, LocalDateTime.now(), null, true);
        };

        int id = SqliteTransaction.immediate(conn -> {
            int newId = userDao.save(conn, user);
            auditLogService.recordInTransactionAsSessionUser(conn, "USER_CREATED", "USER", String.valueOf(newId),
                    null, null, "Admin created user: " + username + " with role: " + role, "SUCCESS");
            return newId;
        });

        return userDao.findById(id).orElse(user);
    }

    /**
     * Deactivates a user account.
     */
    public void adminDeactivateUser(int userId) {
        AccessControl.checkPermission(Permission.MANAGE_USERS);
        User target = userDao.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        SqliteTransaction.immediate(conn -> {
            userDao.setActiveStatus(conn, userId, false);
            auditLogService.recordInTransactionAsSessionUser(conn, "USER_DEACTIVATED", "USER", String.valueOf(userId),
                    "active=1", "active=0", "Admin deactivated user: " + target.getUsername(), "SUCCESS");
            return null;
        });
    }

    /**
     * Re-activates a user account.
     */
    public void adminActivateUser(int userId) {
        AccessControl.checkPermission(Permission.MANAGE_USERS);
        User target = userDao.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        SqliteTransaction.immediate(conn -> {
            userDao.setActiveStatus(conn, userId, true);
            auditLogService.recordInTransactionAsSessionUser(conn, "USER_ACTIVATED", "USER", String.valueOf(userId),
                    "active=0", "active=1", "Admin reactivated user: " + target.getUsername(), "SUCCESS");
            return null;
        });
    }
}

package com.smartparking.service;

import com.smartparking.exception.AuditLogException;
import com.smartparking.model.user.User;
import com.smartparking.repository.AuditLogDao;
import com.smartparking.security.UserSession;
import com.smartparking.util.MaskingUtil;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Service managing system-wide audit logging, sensitive data sanitization,
 * and immutable record persistence.
 */
public class AuditLogService {

    private static volatile AuditLogService instance;
    private final AuditLogDao auditLogDao;

    public AuditLogService(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public AuditLogService() {
        this(new AuditLogDao());
    }

    public static AuditLogService getInstance() {
        if (instance == null) {
            synchronized (AuditLogService.class) {
                if (instance == null) {
                    instance = new AuditLogService();
                }
            }
        }
        return instance;
    }

    /**
     * Security-critical events. A failure to record one is never swallowed: it is reported and thrown, and where
     * the event accompanies a state change, the change and the entry are written in one transaction
     * ({@link #recordInTransaction}).
     */
    public static final Set<String> SECURITY_CRITICAL_ACTIONS = Set.of(
            "LOGIN_SUCCESS", "LOGIN_FAILURE", "LOGIN_BLOCKED", "LOGIN_BLOCKED_LOCKED", "ACCOUNT_LOCKOUT",
            "PASSWORD_CHANGE", "PASSWORD_RESET",
            "USER_CREATED", "USER_REGISTERED", "USER_ACTIVATED", "USER_DEACTIVATED", "USER_ROLE_CHANGED",
            "ACCESS_DENIED");

    /**
     * Explicitly low-risk operational events: a failed write is reported visibly (stderr, failure counter,
     * listeners) and the operation continues. Any action not listed here is treated strictly (failure thrown).
     */
    public static final Set<String> LOW_RISK_ACTIONS = Set.of(
            "LOGOUT",
            "NOTIFICATION_SENT", "NOTIFICATION_FAILED", "NOTIFICATION_SUPPRESSED", "PREFERENCE_UPDATED",
            "ALERT_ACKNOWLEDGED", "ALERT_RESOLVED",
            "EV_CHARGING_STARTED", "EV_CHARGING_STOPPED", "CHARGER_STATUS_UPDATED",
            "RESERVATION_CREATED", "RESERVATION_CANCELLED",
            "PASS_ISSUED", "PASS_RENEWED", "PASS_CANCELLED",
            "DATABASE_BACKUP_CREATED", "DAILY_BACKUP_CREATED");

    /** A failed audit write, as delivered to failure listeners. */
    public record AuditFailure(String action, boolean continued, String message, Instant at) {}

    private final AtomicLong strictFailures = new AtomicLong();
    private final AtomicLong lowRiskFailures = new AtomicLong();
    private final List<Consumer<AuditFailure>> failureListeners = new CopyOnWriteArrayList<>();

    public static boolean isLowRisk(String action) {
        return action != null && LOW_RISK_ACTIONS.contains(action);
    }

    public static boolean isSecurityCritical(String action) {
        return action != null && SECURITY_CRITICAL_ACTIONS.contains(action);
    }

    /**
     * Records an audit event using the active session user (or SYSTEM if unauthenticated).
     *
     * @throws AuditLogException if the write fails and the action is not explicitly low-risk
     */
    public void log(String action, String entityType, String entityId,
                    String oldValue, String newValue, String reason, String result) {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        Integer userId = user != null ? user.getUserId() : null;
        String username = user != null ? user.getUsername() : "SYSTEM";
        String role = user != null ? user.getRole().name() : "SYSTEM";
        write(userId, username, role, action, entityType, entityId, oldValue, newValue, reason, result);
    }

    /**
     * Explicit logger with specified actor information (e.g. during authentication or system tasks).
     *
     * @throws AuditLogException if the write fails and the action is not explicitly low-risk
     */
    public void logWithActor(Integer userId, String username, String role, String action,
                             String entityType, String entityId, String oldValue,
                             String newValue, String reason, String result) {
        write(userId, username, role, action, entityType, entityId, oldValue, newValue, reason, result);
    }

    private void write(Integer userId, String username, String role, String action, String entityType,
                       String entityId, String oldValue, String newValue, String reason, String result) {
        try {
            auditLogDao.append(userId, username, role, action, entityType, entityId,
                    sanitize(oldValue), sanitize(newValue), reason, "LOCAL_CLIENT", result);
        } catch (AuditLogException e) {
            boolean continueAnyway = isLowRisk(action);
            reportFailure(action, e, continueAnyway);
            if (!continueAnyway) {
                throw e;
            }
        }
    }

    /**
     * Writes an audit entry as part of the caller's {@link com.smartparking.repository.SqliteTransaction}: the
     * entry commits or rolls back together with the caller's changes. Failures are reported and thrown so the
     * caller's transaction rolls back.
     */
    public void recordInTransaction(Connection conn, Integer userId, String username, String role, String action,
                                    String entityType, String entityId, String oldValue, String newValue,
                                    String reason, String result) {
        try {
            auditLogDao.append(conn, userId, username, role, action, entityType, entityId,
                    sanitize(oldValue), sanitize(newValue), reason, "LOCAL_CLIENT", result);
        } catch (AuditLogException e) {
            reportFailure(action, e, false);
            throw e;
        }
    }

    /** As {@link #recordInTransaction} with the active session user as the actor (SYSTEM if none). */
    public void recordInTransactionAsSessionUser(Connection conn, String action, String entityType, String entityId,
                                                 String oldValue, String newValue, String reason, String result) {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        recordInTransaction(conn, user != null ? user.getUserId() : null,
                user != null ? user.getUsername() : "SYSTEM", user != null ? user.getRole().name() : "SYSTEM",
                action, entityType, entityId, oldValue, newValue, reason, result);
    }

    /**
     * As {@link #recordInTransaction}, attributed to the signed-in user. Only for in-process calls without a session
     * (system jobs, tests) is the given fallback name used, with role SYSTEM, so a caller-supplied name can never
     * replace the real user.
     */
    public void recordInTransactionAsActor(Connection conn, String fallbackUsername, String action, String entityType,
                                           String entityId, String oldValue, String newValue, String reason, String result) {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        if (user != null) {
            recordInTransaction(conn, user.getUserId(), user.getUsername(), user.getRole().name(), action, entityType,
                    entityId, oldValue, newValue, reason, result);
        } else {
            recordInTransaction(conn, null, fallbackUsername != null ? fallbackUsername : "SYSTEM", "SYSTEM", action,
                    entityType, entityId, oldValue, newValue, reason, result);
        }
    }

    /** Makes a failed audit write visible: stderr, counters and registered listeners (e.g. an admin alert). */
    public void reportFailure(String action, Throwable error, boolean continued) {
        String message = error.getMessage();
        if (continued) {
            lowRiskFailures.incrementAndGet();
            System.err.println("AUDIT WARNING: low-risk audit event " + action
                    + " was NOT recorded; operation continued. Cause: " + message);
        } else {
            strictFailures.incrementAndGet();
            System.err.println("CRITICAL AUDIT FAILURE: audit event " + action
                    + " could not be recorded; operation aborted or denied. Cause: " + message);
        }
        AuditFailure failure = new AuditFailure(action, continued, message, Instant.now());
        for (Consumer<AuditFailure> listener : failureListeners) {
            try {
                listener.accept(failure);
            } catch (RuntimeException listenerError) {
                System.err.println("Audit failure listener error: " + listenerError.getMessage());
            }
        }
    }

    public void addFailureListener(Consumer<AuditFailure> listener) {
        failureListeners.add(listener);
    }

    public void removeFailureListener(Consumer<AuditFailure> listener) {
        failureListeners.remove(listener);
    }

    /** Audit writes that failed and aborted/denied their operation. */
    public long getStrictFailureCount() {
        return strictFailures.get();
    }

    /** Low-risk audit writes that failed while the operation continued. */
    public long getLowRiskFailureCount() {
        return lowRiskFailures.get();
    }

    public AuditLogDao getDao() {
        return auditLogDao;
    }

    private String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        // Never log passwords or secret tokens
        if (input.toLowerCase().contains("password") || input.toLowerCase().contains("secret") || input.toLowerCase().contains("cvv")) {
            return "[REDACTED_CONFIDENTIAL]";
        }

        // Mask phone/mobile numbers if recognized (10 digits)
        if (input.matches(".*[6-9][0-9]{9}.*")) {
            return input.replaceAll("([6-9][0-9]{2})[0-9]{5}([0-9]{2})", "$1XXXXX$2");
        }

        return input;
    }
}

package com.smartparking.service;

import com.smartparking.model.user.User;
import com.smartparking.repository.AuditLogDao;
import com.smartparking.security.UserSession;
import com.smartparking.util.MaskingUtil;

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
     * Records an audit event using the active session user (or SYSTEM if unauthenticated).
     */
    public void log(String action, String entityType, String entityId,
                    String oldValue, String newValue, String reason, String result) {
        User user = UserSession.getInstance().getCurrentUser().orElse(null);
        Integer userId = user != null ? user.getUserId() : null;
        String username = user != null ? user.getUsername() : "SYSTEM";
        String role = user != null ? user.getRole().name() : "SYSTEM";

        String safeOld = sanitize(oldValue);
        String safeNew = sanitize(newValue);

        auditLogDao.append(userId, username, role, action, entityType, entityId, safeOld, safeNew, reason, "LOCAL_CLIENT", result);
    }

    /**
     * Explicit logger with specified actor information (e.g. during authentication or system tasks).
     */
    public void logWithActor(Integer userId, String username, String role, String action,
                             String entityType, String entityId, String oldValue,
                             String newValue, String reason, String result) {
        String safeOld = sanitize(oldValue);
        String safeNew = sanitize(newValue);
        auditLogDao.append(userId, username, role, action, entityType, entityId, safeOld, safeNew, reason, "LOCAL_CLIENT", result);
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

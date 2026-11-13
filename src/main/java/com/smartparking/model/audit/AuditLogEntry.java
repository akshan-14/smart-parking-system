package com.smartparking.model.audit;

import java.time.LocalDateTime;

/**
 * Domain entity representing an immutable, tamper-checked Audit Log Entry.
 */
public class AuditLogEntry {
    private final int id;
    private final LocalDateTime timestamp;
    private final Integer userId;
    private final String username;
    private final String role;
    private final String action;
    private final String entityType;
    private final String entityId;
    private final String oldValue;
    private final String newValue;
    private final String reason;
    private final String ipOrTerminal;
    private final String result;
    private final String prevHash;
    private final String rowHash;

    public AuditLogEntry(int id, LocalDateTime timestamp, Integer userId, String username, String role,
                         String action, String entityType, String entityId, String oldValue, String newValue,
                         String reason, String ipOrTerminal, String result, String prevHash, String rowHash) {
        this.id = id;
        this.timestamp = timestamp;
        this.userId = userId;
        this.username = username != null ? username : "SYSTEM";
        this.role = role != null ? role : "SYSTEM";
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        this.ipOrTerminal = ipOrTerminal != null ? ipOrTerminal : "LOCAL";
        this.result = result != null ? result : "SUCCESS";
        this.prevHash = prevHash != null ? prevHash : "GENESIS";
        this.rowHash = rowHash;
    }

    public int getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public String getIpOrTerminal() {
        return ipOrTerminal;
    }

    public String getResult() {
        return result;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getRowHash() {
        return rowHash;
    }
}

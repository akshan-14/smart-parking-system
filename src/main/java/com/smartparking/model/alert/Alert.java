package com.smartparking.model.alert;

import java.time.LocalDateTime;

/**
 * Model representing an Operational or System Alert.
 * Sourced from the SQLite alerts table.
 */
public class Alert {

    private final int id;
    private final String type;
    private final String severity; // INFO, WARNING, CRITICAL
    private final String entityType; // TICKET, SLOT, CHARGER, PASS, FACILITY, RESERVATION
    private final String entityId;
    private final String message;
    private final LocalDateTime createdAt;
    private String status; // OPEN, ACKNOWLEDGED, RESOLVED
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNote;

    public Alert(int id, String type, String severity, String entityType, String entityId,
                 String message, LocalDateTime createdAt, String status,
                 String acknowledgedBy, LocalDateTime acknowledgedAt,
                 String resolvedBy, LocalDateTime resolvedAt, String resolutionNote) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.entityType = entityType;
        this.entityId = entityId;
        this.message = message;
        this.createdAt = createdAt;
        this.status = status;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.resolutionNote = resolutionNote;
    }

    public int getId() { return id; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public boolean isOpen() { return "OPEN".equalsIgnoreCase(status); }
    public boolean isAcknowledged() { return "ACKNOWLEDGED".equalsIgnoreCase(status); }
    public boolean isResolved() { return "RESOLVED".equalsIgnoreCase(status); }

    @Override
    public String toString() {
        return "Alert{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", severity='" + severity + '\'' +
                ", entityId='" + entityId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}

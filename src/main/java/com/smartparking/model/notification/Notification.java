package com.smartparking.model.notification;

import java.time.LocalDateTime;

/**
 * Model representing a generated notification record.
 * Sourced from the SQLite notifications table.
 */
public class Notification {

    private final int id;
    private final int userId;
    private final String mobileMasked;
    private final String channel; // SMS, WHATSAPP, EMAIL
    private final String template;
    private final String message;
    private String status; // PENDING, SENT, FAILED
    private final LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private final String relatedEntity;

    public Notification(int id, int userId, String mobileMasked, String channel,
                        String template, String message, String status,
                        LocalDateTime createdAt, LocalDateTime sentAt, String relatedEntity) {
        this.id = id;
        this.userId = userId;
        this.mobileMasked = mobileMasked;
        this.channel = channel;
        this.template = template;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.relatedEntity = relatedEntity;
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getMobileMasked() { return mobileMasked; }
    public String getChannel() { return channel; }
    public String getTemplate() { return template; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public String getRelatedEntity() { return relatedEntity; }

    public boolean isSent() { return "SENT".equalsIgnoreCase(status); }
    public boolean isFailed() { return "FAILED".equalsIgnoreCase(status); }

    @Override
    public String toString() {
        return "Notification{" +
                "id=" + id +
                ", channel='" + channel + '\'' +
                ", template='" + template + '\'' +
                ", mobileMasked='" + mobileMasked + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}

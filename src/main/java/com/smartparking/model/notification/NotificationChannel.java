package com.smartparking.model.notification;

/**
 * Supported simulated delivery channels.
 */
public enum NotificationChannel {
    SMS("SMS (Simulated)"),
    WHATSAPP("WhatsApp (Simulated)"),
    EMAIL("Email (Simulated)");

    private final String displayName;

    NotificationChannel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

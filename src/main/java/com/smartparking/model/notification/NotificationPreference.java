package com.smartparking.model.notification;

/**
 * Model representing a user's notification channel opt-in/opt-out preference.
 * Sourced from the SQLite notification_preferences table.
 */
public record NotificationPreference(
        int userId,
        String channel,
        boolean enabled
) {}

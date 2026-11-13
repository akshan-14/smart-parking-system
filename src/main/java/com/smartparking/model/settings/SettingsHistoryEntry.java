package com.smartparking.model.settings;

import java.time.LocalDateTime;

/**
 * Immutable model representing an audit record of a settings change.
 * Sourced from the SQLite settings_history table.
 */
public record SettingsHistoryEntry(
        int id,
        String settingKey,
        String oldValue,
        String newValue,
        String changedBy,
        LocalDateTime changedAt
) {}

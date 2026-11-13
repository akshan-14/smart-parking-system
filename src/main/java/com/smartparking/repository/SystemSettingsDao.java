package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object for System Settings and Tariff Configuration.
 * Enforces single source of truth for tariffs, GST %, grace periods, and multipliers.
 */
public class SystemSettingsDao {

    public Optional<String> get(String key) {
        if (key == null) return Optional.empty();
        String sql = "SELECT setting_value FROM settings WHERE setting_key = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("setting_value"));
                }
            }
        } catch (SQLException e) {
            // Return empty if table has not been initialized yet
            return Optional.empty();
        }
        return Optional.empty();
    }

    public String getString(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        try {
            return get(key).map(Double::parseDouble).orElse(defaultValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        try {
            return get(key).map(Integer::parseInt).orElse(defaultValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    public void set(String key, String value, String description) {
        String sql = "INSERT INTO settings (setting_key, setting_value, description) VALUES (?, ?, ?) " +
                "ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value, description = excluded.description;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.setString(3, description);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating setting " + key + ": " + e.getMessage(), e);
        }
    }

    public void saveOrUpdate(String key, String value, String description) {
        set(key, value, description);
    }

    public Map<String, String> getAll() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT setting_key, setting_value FROM settings;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all settings: " + e.getMessage(), e);
        }
        return map;
    }
}

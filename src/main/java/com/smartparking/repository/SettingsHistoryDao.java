package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.settings.SettingsHistoryEntry;
import com.smartparking.util.DateFormat;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for recording and querying changes in the settings_history table.
 */
public class SettingsHistoryDao {

    public void recordChange(String key, String oldValue, String newValue, String changedBy) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            recordChange(conn, key, oldValue, newValue, changedBy);
        } catch (SQLException e) {
            throw new RuntimeException("Error recording settings history for " + key + ": " + e.getMessage(), e);
        }
    }

    public void recordChange(Connection conn, String key, String oldValue, String newValue, String changedBy) throws SQLException {
        String sql = "INSERT INTO settings_history (setting_key, old_value, new_value, changed_by, changed_at) VALUES (?, ?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, oldValue != null ? oldValue : "");
            ps.setString(3, newValue);
            ps.setString(4, changedBy != null ? changedBy : "SYSTEM");
            ps.setString(5, DateFormat.toDbString(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    public List<SettingsHistoryEntry> findRecent(int limit) {
        List<SettingsHistoryEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM settings_history ORDER BY id DESC LIMIT ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String key = rs.getString("setting_key");
                    String oldVal = rs.getString("old_value");
                    String newVal = rs.getString("new_value");
                    String by = rs.getString("changed_by");
                    String tsStr = rs.getString("changed_at");
                    LocalDateTime ts;
                    try {
                        ts = DateFormat.parseDbDateTime(tsStr);
                    } catch (Exception e) {
                        ts = LocalDateTime.now();
                    }
                    list.add(new SettingsHistoryEntry(id, key, oldVal, newVal, by, ts));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading settings history: " + e.getMessage(), e);
        }
        return list;
    }

    public List<SettingsHistoryEntry> findByKey(String targetKey) {
        List<SettingsHistoryEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM settings_history WHERE setting_key = ? ORDER BY id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String key = rs.getString("setting_key");
                    String oldVal = rs.getString("old_value");
                    String newVal = rs.getString("new_value");
                    String by = rs.getString("changed_by");
                    String tsStr = rs.getString("changed_at");
                    LocalDateTime ts;
                    try {
                        ts = DateFormat.parseDbDateTime(tsStr);
                    } catch (Exception e) {
                        ts = LocalDateTime.now();
                    }
                    list.add(new SettingsHistoryEntry(id, key, oldVal, newVal, by, ts));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading settings history for " + targetKey + ": " + e.getMessage(), e);
        }
        return list;
    }
}

package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.notification.NotificationPreference;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for User Notification Channel Preferences.
 */
public class NotificationPreferenceDao {

    public boolean isChannelEnabled(int userId, String channel) {
        String sql = "SELECT enabled FROM notification_preferences WHERE user_id = ? AND channel = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, channel);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 1;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking preference: " + e.getMessage(), e);
        }
        // Enabled by default if no explicit preference set
        return true;
    }

    public void setPreference(int userId, String channel, boolean enabled) {
        String sql = "INSERT INTO notification_preferences (user_id, channel, enabled) VALUES (?, ?, ?) " +
                "ON CONFLICT(user_id, channel) DO UPDATE SET enabled = excluded.enabled;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, channel);
            ps.setInt(3, enabled ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error setting notification preference: " + e.getMessage(), e);
        }
    }

    public List<NotificationPreference> findByUserId(int userId) {
        List<NotificationPreference> list = new ArrayList<>();
        String sql = "SELECT user_id, channel, enabled FROM notification_preferences WHERE user_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new NotificationPreference(
                            rs.getInt("user_id"),
                            rs.getString("channel"),
                            rs.getInt("enabled") == 1
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading notification preferences: " + e.getMessage(), e);
        }
        return list;
    }
}

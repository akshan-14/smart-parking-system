package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.notification.Notification;
import com.smartparking.util.DateFormat;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Notifications.
 */
public class NotificationDao {

    public Notification createNotification(int userId, String mobileMasked, String channel,
                                          String template, String message, String relatedEntity) {
        String sql = "INSERT INTO notifications (user_id, mobile_masked, channel, template, message, status, created_at, related_entity) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?);";
        LocalDateTime now = LocalDateTime.now();
        String createdStr = DateFormat.toDbString(now);

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (userId > 0) {
                ps.setInt(1, userId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, mobileMasked);
            ps.setString(3, channel);
            ps.setString(4, template);
            ps.setString(5, message);
            ps.setString(6, createdStr);
            ps.setString(7, relatedEntity);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    return new Notification(id, userId, mobileMasked, channel, template, message, "PENDING", now, null, relatedEntity);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating notification: " + e.getMessage(), e);
        }
        throw new RuntimeException("Failed to retrieve generated ID for notification.");
    }

    public void updateStatus(int id, String status, LocalDateTime sentAt) {
        String sql = "UPDATE notifications SET status = ?, sent_at = ? WHERE id = ?;";
        String sentStr = sentAt != null ? DateFormat.toDbString(sentAt) : null;
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, sentStr);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating notification status: " + e.getMessage(), e);
        }
    }

    public Optional<Notification> findById(int id) {
        String sql = "SELECT * FROM notifications WHERE id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading notification by ID " + id + ": " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Notification> findByUserId(int userId, int limit) {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT * FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit > 0 ? limit : 50);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading notifications for user " + userId + ": " + e.getMessage(), e);
        }
        return list;
    }

    public List<Notification> findRecent(int limit) {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT * FROM notifications ORDER BY id DESC LIMIT ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading recent notifications: " + e.getMessage(), e);
        }
        return list;
    }

    private Notification mapResultSet(ResultSet rs) throws SQLException {
        LocalDateTime created = DateFormat.parseDbDateTime(rs.getString("created_at"));
        LocalDateTime sent = null;
        String sentStr = rs.getString("sent_at");
        if (sentStr != null && !sentStr.isBlank()) {
            sent = DateFormat.parseDbDateTime(sentStr);
        }
        return new Notification(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getString("mobile_masked"),
                rs.getString("channel"),
                rs.getString("template"),
                rs.getString("message"),
                rs.getString("status"),
                created,
                sent,
                rs.getString("related_entity")
        );
    }
}

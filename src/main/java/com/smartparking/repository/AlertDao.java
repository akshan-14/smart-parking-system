package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.alert.Alert;
import com.smartparking.util.DateFormat;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Operational & System Alerts.
 */
public class AlertDao {

    public Alert createAlert(String type, String severity, String entityType, String entityId, String message) {
        String sql = "INSERT INTO alerts (type, severity, entity_type, entity_id, message, created_at, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'OPEN');";
        LocalDateTime now = LocalDateTime.now();
        String createdStr = DateFormat.toDbString(now);

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, severity);
            ps.setString(3, entityType);
            ps.setString(4, entityId);
            ps.setString(5, message);
            ps.setString(6, createdStr);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    return new Alert(id, type, severity, entityType, entityId, message, now,
                            "OPEN", null, null, null, null, null);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating alert: " + e.getMessage(), e);
        }
        throw new RuntimeException("Failed to retrieve generated ID for alert.");
    }

    public Optional<Alert> findOpenByTypeAndEntity(String type, String entityId) {
        String sql = "SELECT * FROM alerts WHERE type = ? AND entity_id = ? AND status != 'RESOLVED' LIMIT 1;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking existing open alert: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<Alert> findById(int id) {
        String sql = "SELECT * FROM alerts WHERE id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading alert by id " + id + ": " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public void acknowledgeAlert(int id, String acknowledgedBy, LocalDateTime acknowledgedAt) {
        String sql = "UPDATE alerts SET status = 'ACKNOWLEDGED', acknowledged_by = ?, acknowledged_at = ? WHERE id = ?;";
        String timeStr = DateFormat.toDbString(acknowledgedAt);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acknowledgedBy);
            ps.setString(2, timeStr);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error acknowledging alert #" + id + ": " + e.getMessage(), e);
        }
    }

    public void resolveAlert(int id, String resolvedBy, LocalDateTime resolvedAt, String resolutionNote) {
        String sql = "UPDATE alerts SET status = 'RESOLVED', resolved_by = ?, resolved_at = ?, resolution_note = ? WHERE id = ?;";
        String timeStr = DateFormat.toDbString(resolvedAt);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resolvedBy);
            ps.setString(2, timeStr);
            ps.setString(3, resolutionNote != null ? resolutionNote : "Resolved");
            ps.setInt(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error resolving alert #" + id + ": " + e.getMessage(), e);
        }
    }

    public void resolveOpenAlertsForEntity(String type, String entityId, String resolvedBy, String note) {
        String sql = "UPDATE alerts SET status = 'RESOLVED', resolved_by = ?, resolved_at = ?, resolution_note = ? " +
                "WHERE type = ? AND entity_id = ? AND status != 'RESOLVED';";
        String timeStr = DateFormat.toDbString(LocalDateTime.now());
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resolvedBy);
            ps.setString(2, timeStr);
            ps.setString(3, note != null ? note : "Auto-resolved when underlying condition cleared");
            ps.setString(4, type);
            ps.setString(5, entityId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error auto-resolving alerts: " + e.getMessage(), e);
        }
    }

    public List<Alert> findFiltered(String severity, String type, String status, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM alerts WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (severity != null && !severity.isBlank() && !"ALL".equalsIgnoreCase(severity)) {
            sql.append("AND severity = ? ");
            params.add(severity);
        }
        if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
            sql.append("AND type = ? ");
            params.add(type);
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append("AND status = ? ");
            params.add(status);
        }
        sql.append("ORDER BY id DESC LIMIT ?;");
        params.add(limit > 0 ? limit : 100);

        List<Alert> list = new ArrayList<>();
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error filtering alerts: " + e.getMessage(), e);
        }
        return list;
    }

    public int countOpenAlerts() {
        String sql = "SELECT COUNT(*) FROM alerts WHERE status != 'RESOLVED';";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting open alerts: " + e.getMessage(), e);
        }
        return 0;
    }

    private Alert mapResultSet(ResultSet rs) throws SQLException {
        LocalDateTime created = DateFormat.parseDbDateTime(rs.getString("created_at"));
        LocalDateTime ackAt = null;
        String ackStr = rs.getString("acknowledged_at");
        if (ackStr != null && !ackStr.isBlank()) {
            ackAt = DateFormat.parseDbDateTime(ackStr);
        }
        LocalDateTime resAt = null;
        String resStr = rs.getString("resolved_at");
        if (resStr != null && !resStr.isBlank()) {
            resAt = DateFormat.parseDbDateTime(resStr);
        }
        return new Alert(
                rs.getInt("id"),
                rs.getString("type"),
                rs.getString("severity"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("message"),
                created,
                rs.getString("status"),
                rs.getString("acknowledged_by"),
                ackAt,
                rs.getString("resolved_by"),
                resAt,
                rs.getString("resolution_note")
        );
    }
}

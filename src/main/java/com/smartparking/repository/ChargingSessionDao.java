package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.ev.ChargingSession;
import com.smartparking.util.DateFormat;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for EV Charging Sessions.
 */
public class ChargingSessionDao {

    public ChargingSession createSession(int ticketId, int chargerId, LocalDateTime startTime, double unitPrice) {
        String sql = "INSERT INTO charging_sessions (ticket_id, charger_id, start_time, unit_price, status) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE');";
        String startStr = DateFormat.toDbString(startTime);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, chargerId);
            ps.setString(3, startStr);
            ps.setDouble(4, unitPrice);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    return new ChargingSession(id, ticketId, chargerId, startTime, null, 0.0, unitPrice, 0.0, 0.0, "ACTIVE");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating charging session: " + e.getMessage(), e);
        }
        throw new RuntimeException("Failed to retrieve generated ID for charging session.");
    }

    public Optional<ChargingSession> findActiveByTicketId(int ticketId) {
        String sql = "SELECT * FROM charging_sessions WHERE ticket_id = ? AND status = 'ACTIVE' LIMIT 1;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding active session for ticket " + ticketId + ": " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<ChargingSession> findActiveByChargerId(int chargerId) {
        String sql = "SELECT * FROM charging_sessions WHERE charger_id = ? AND status = 'ACTIVE' LIMIT 1;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, chargerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding active session for charger " + chargerId + ": " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<ChargingSession> findLatestByTicketId(int ticketId) {
        String sql = "SELECT * FROM charging_sessions WHERE ticket_id = ? ORDER BY id DESC LIMIT 1;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding session for ticket " + ticketId + ": " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<ChargingSession> findById(int id) {
        String sql = "SELECT * FROM charging_sessions WHERE id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding session by ID " + id + ": " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public void completeSession(int sessionId, LocalDateTime endTime, double kwh, double energyCharge, double idleFee) {
        String sql = "UPDATE charging_sessions SET end_time = ?, kwh = ?, energy_charge = ?, idle_fee = ?, status = 'COMPLETED' WHERE id = ?;";
        String endStr = DateFormat.toDbString(endTime);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, endStr);
            ps.setDouble(2, kwh);
            ps.setDouble(3, energyCharge);
            ps.setDouble(4, idleFee);
            ps.setInt(5, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error completing charging session " + sessionId + ": " + e.getMessage(), e);
        }
    }

    public void updateIdleFee(int sessionId, double idleFee) {
        String sql = "UPDATE charging_sessions SET idle_fee = ? WHERE id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, idleFee);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating idle fee for session " + sessionId + ": " + e.getMessage(), e);
        }
    }

    public List<ChargingSession> findAll() {
        List<ChargingSession> list = new ArrayList<>();
        String sql = "SELECT * FROM charging_sessions ORDER BY id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading all charging sessions: " + e.getMessage(), e);
        }
        return list;
    }

    private ChargingSession mapResultSet(ResultSet rs) throws SQLException {
        LocalDateTime start = DateFormat.parseDbDateTime(rs.getString("start_time"));
        LocalDateTime end = null;
        String endStr = rs.getString("end_time");
        if (endStr != null && !endStr.isBlank()) {
            end = DateFormat.parseDbDateTime(endStr);
        }
        return new ChargingSession(
                rs.getInt("id"),
                rs.getInt("ticket_id"),
                rs.getInt("charger_id"),
                start,
                end,
                rs.getDouble("kwh"),
                rs.getDouble("unit_price"),
                rs.getDouble("energy_charge"),
                rs.getDouble("idle_fee"),
                rs.getString("status")
        );
    }
}

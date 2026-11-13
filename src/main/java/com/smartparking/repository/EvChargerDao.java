package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.ev.EvCharger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for EV Chargers installed on parking bays.
 */
public class EvChargerDao {

    public Optional<EvCharger> findById(int id) {
        String sql = "SELECT * FROM ev_chargers WHERE id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading EV charger by ID " + id + ": " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<EvCharger> findBySlotId(String slotId) {
        String sql = "SELECT * FROM ev_chargers WHERE slot_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading EV charger for slot " + slotId + ": " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<EvCharger> findAll() {
        List<EvCharger> list = new ArrayList<>();
        String sql = "SELECT * FROM ev_chargers ORDER BY id ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading all EV chargers: " + e.getMessage(), e);
        }
        return list;
    }

    public void updateStatus(int id, String status) {
        String sql = "UPDATE ev_chargers SET status = ? WHERE id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating charger status: " + e.getMessage(), e);
        }
    }

    private EvCharger mapResultSet(ResultSet rs) throws SQLException {
        return new EvCharger(
                rs.getInt("id"),
                rs.getString("slot_id"),
                rs.getString("connector_type"),
                rs.getDouble("power_kw"),
                rs.getString("status")
        );
    }
}

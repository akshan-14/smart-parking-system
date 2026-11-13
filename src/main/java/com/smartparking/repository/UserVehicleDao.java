package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.util.DateFormat;
import com.smartparking.util.PlateUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Customer vehicle linkages (user_vehicles table).
 */
public class UserVehicleDao {

    public void linkVehicle(int userId, String rawPlate) {
        String plate = PlateUtil.normalize(rawPlate);
        if (!PlateUtil.isValid(plate)) {
            throw new IllegalArgumentException("Invalid vehicle plate format: " + rawPlate);
        }

        String sql = "INSERT OR IGNORE INTO user_vehicles (user_id, license_plate, created_at) VALUES (?, ?, ?);";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, plate);
            ps.setString(3, DateFormat.toDbString(LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error linking vehicle: " + e.getMessage(), e);
        }
    }

    public void unlinkVehicle(int userId, String rawPlate) {
        String plate = PlateUtil.normalize(rawPlate);
        String sql = "DELETE FROM user_vehicles WHERE user_id = ? AND UPPER(license_plate) = UPPER(?);";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, plate);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error unlinking vehicle: " + e.getMessage(), e);
        }
    }

    public List<String> findPlatesByUserId(int userId) {
        List<String> plates = new ArrayList<>();
        String sql = "SELECT license_plate FROM user_vehicles WHERE user_id = ? ORDER BY id ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    plates.add(rs.getString("license_plate"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving user vehicles: " + e.getMessage(), e);
        }
        return plates;
    }

    public boolean isVehicleOwnedByUser(int userId, String rawPlate) {
        String plate = PlateUtil.normalize(rawPlate);
        String sql = "SELECT COUNT(*) FROM user_vehicles WHERE user_id = ? AND UPPER(license_plate) = UPPER(?);";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, plate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }
}

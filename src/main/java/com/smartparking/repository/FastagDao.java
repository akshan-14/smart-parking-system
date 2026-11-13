package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.fastag.FastagAccount;
import com.smartparking.util.PlateUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for FASTag accounts in SQLite.
 */
public class FastagDao {

    public Optional<FastagAccount> findByPlate(String plate) {
        if (plate == null || plate.isBlank()) return Optional.empty();
        String norm = PlateUtil.normalize(plate);
        String sql = "SELECT * FROM fastag_accounts WHERE plate = ? COLLATE NOCASE;";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, norm);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying FASTag by plate: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<FastagAccount> findByTagId(String tagId) {
        if (tagId == null || tagId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM fastag_accounts WHERE tag_id = ?;";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tagId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying FASTag by ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<FastagAccount> findAll() {
        List<FastagAccount> list = new ArrayList<>();
        String sql = "SELECT * FROM fastag_accounts ORDER BY tag_id ASC;";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all FASTag accounts: " + e.getMessage(), e);
        }
        return list;
    }

    public void save(FastagAccount acc) {
        String sql = "INSERT INTO fastag_accounts (tag_id, plate, balance, status) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(tag_id) DO UPDATE SET plate = excluded.plate, balance = excluded.balance, " +
                "status = excluded.status, updated_at = CURRENT_TIMESTAMP;";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acc.getTagId());
            ps.setString(2, acc.getLicensePlate());
            ps.setDouble(3, acc.getBalance());
            ps.setString(4, acc.getStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving FASTag account: " + e.getMessage(), e);
        }
    }

    public void updateBalance(Connection conn, String tagId, double newBalance) throws SQLException {
        String sql = "UPDATE fastag_accounts SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE tag_id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, Math.max(0.0, newBalance));
            ps.setString(2, tagId);
            ps.executeUpdate();
        }
    }

    public void updateStatus(String tagId, String status) {
        String sql = "UPDATE fastag_accounts SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE tag_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.toUpperCase());
            ps.setString(2, tagId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating FASTag status: " + e.getMessage(), e);
        }
    }

    private FastagAccount mapRow(ResultSet rs) throws SQLException {
        return new FastagAccount(
                rs.getString("tag_id"),
                rs.getString("plate"),
                rs.getDouble("balance"),
                rs.getString("status"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }
}

package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.util.DateFormat;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for DPDP Act, 2023 statutory consent logs.
 */
public class ConsentDao {

    public void saveConsent(String userIdOrPlate, String purpose, String textVersion) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            saveConsent(conn, userIdOrPlate, purpose, textVersion);
        } catch (SQLException e) {
            throw new RuntimeException("Error recording DPDP consent: " + e.getMessage(), e);
        }
    }

    /** Inserts the consent on the caller's connection (and transaction). */
    public void saveConsent(Connection conn, String userIdOrPlate, String purpose, String textVersion) throws SQLException {
        String sql = "INSERT INTO consents (user_id_or_plate, purpose, text_version, given_at, withdrawn_at) " +
                "VALUES (?, ?, ?, ?, NULL);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userIdOrPlate != null ? userIdOrPlate.trim().toLowerCase() : "ANONYMOUS");
            ps.setString(2, purpose);
            ps.setString(3, textVersion);
            ps.setString(4, DateFormat.toDbString(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    public void withdrawConsent(String userIdOrPlate, String purpose) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            withdrawConsent(conn, userIdOrPlate, purpose);
        } catch (SQLException e) {
            throw new RuntimeException("Error withdrawing DPDP consent: " + e.getMessage(), e);
        }
    }

    /** Withdraws active consents on the caller's connection; returns the number of consents withdrawn. */
    public int withdrawConsent(Connection conn, String userIdOrPlate, String purpose) throws SQLException {
        String sql = "UPDATE consents SET withdrawn_at = ? " +
                "WHERE LOWER(user_id_or_plate) = LOWER(?) AND purpose = ? AND withdrawn_at IS NULL;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DateFormat.toDbString(LocalDateTime.now()));
            ps.setString(2, userIdOrPlate.trim());
            ps.setString(3, purpose);
            return ps.executeUpdate();
        }
    }

    public boolean hasActiveConsent(String userIdOrPlate, String purpose) {
        if (userIdOrPlate == null) return false;
        String sql = "SELECT COUNT(*) FROM consents WHERE LOWER(user_id_or_plate) = LOWER(?) " +
                "AND purpose = ? AND withdrawn_at IS NULL;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userIdOrPlate.trim());
            ps.setString(2, purpose);
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

    public List<ConsentRecord> findByUserOrPlate(String userIdOrPlate) {
        List<ConsentRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM consents WHERE LOWER(user_id_or_plate) = LOWER(?) ORDER BY id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userIdOrPlate.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ConsentRecord(
                            rs.getInt("id"),
                            rs.getString("user_id_or_plate"),
                            rs.getString("purpose"),
                            rs.getString("text_version"),
                            rs.getString("given_at"),
                            rs.getString("withdrawn_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving consent records: " + e.getMessage(), e);
        }
        return list;
    }

    public record ConsentRecord(int id, String userIdOrPlate, String purpose, String textVersion, String givenAt, String withdrawnAt) {}
}

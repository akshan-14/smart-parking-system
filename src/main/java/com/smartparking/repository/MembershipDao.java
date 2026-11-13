package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.MembershipType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.membership.MembershipPass;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for MembershipPass entities in SQLite.
 */
public class MembershipDao {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public int save(MembershipPass pass) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            return saveWithConnection(conn, pass);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving membership pass: " + e.getMessage(), e);
        }
    }

    public int saveWithConnection(Connection conn, MembershipPass pass) throws SQLException {
        String sql = "INSERT INTO memberships (user_id, license_plate, vehicle_type, pass_type, " +
                "duration_months, monthly_fee, total_price, discount_percentage, start_date, expiry_date, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, pass.getUserId());
            pstmt.setString(2, pass.getLicensePlate());
            pstmt.setString(3, pass.getVehicleType().name());
            pstmt.setString(4, pass.getPassType() != null ? pass.getPassType().name() : "FLAT_MONTHLY");
            pstmt.setInt(5, pass.getDurationMonths());
            pstmt.setDouble(6, pass.getMonthlyFee());
            pstmt.setDouble(7, pass.getTotalPrice());
            pstmt.setDouble(8, pass.getDiscountPercentage());
            pstmt.setString(9, pass.getStartDate().format(FORMATTER));
            pstmt.setString(10, pass.getExpiryDate().format(FORMATTER));
            pstmt.setInt(11, pass.isActive() ? 1 : 0);

            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public Optional<MembershipPass> findActiveByPlate(String licensePlate) {
        if (licensePlate == null) return Optional.empty();
        String sql = "SELECT * FROM memberships WHERE LOWER(REPLACE(REPLACE(license_plate, ' ', ''), '-', '')) = LOWER(?) " +
                "AND is_active = 1 AND date(expiry_date) >= date('now') ORDER BY membership_id DESC LIMIT 1;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String norm = licensePlate.replaceAll("[\\s\\-]+", "").toLowerCase();
            pstmt.setString(1, norm);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPass(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying active membership by plate: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<MembershipPass> findLatestByPlate(String licensePlate) {
        if (licensePlate == null) return Optional.empty();
        String sql = "SELECT * FROM memberships WHERE LOWER(REPLACE(REPLACE(license_plate, ' ', ''), '-', '')) = LOWER(?) " +
                "ORDER BY membership_id DESC LIMIT 1;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String norm = licensePlate.replaceAll("[\\s\\-]+", "").toLowerCase();
            pstmt.setString(1, norm);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPass(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying latest membership by plate: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<MembershipPass> findByUserId(int userId) {
        List<MembershipPass> list = new ArrayList<>();
        String sql = "SELECT * FROM memberships WHERE user_id = ? ORDER BY membership_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToPass(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying user memberships: " + e.getMessage(), e);
        }
        return list;
    }

    public List<MembershipPass> findAll() {
        List<MembershipPass> list = new ArrayList<>();
        String sql = "SELECT * FROM memberships ORDER BY membership_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRowToPass(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all memberships: " + e.getMessage(), e);
        }
        return list;
    }

    public void renew(int membershipId, LocalDate newExpiry, int additionalMonths, double additionalFee) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            renewWithConnection(conn, membershipId, newExpiry, additionalMonths, additionalFee);
        } catch (SQLException e) {
            throw new RuntimeException("Error renewing membership: " + e.getMessage(), e);
        }
    }

    public void renewWithConnection(Connection conn, int membershipId, LocalDate newExpiry, int additionalMonths, double additionalFee) throws SQLException {
        String sql = "UPDATE memberships SET expiry_date = ?, duration_months = duration_months + ?, total_price = total_price + ?, is_active = 1 WHERE membership_id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newExpiry.format(FORMATTER));
            ps.setInt(2, additionalMonths);
            ps.setDouble(3, additionalFee);
            ps.setInt(4, membershipId);
            ps.executeUpdate();
        }
    }

    public void renew(int membershipId, LocalDate newExpiry) {
        renew(membershipId, newExpiry, 1, 0.0);
    }

    public void cancel(int membershipId) {
        updateActiveStatus(membershipId, false);
    }

    public void updateActiveStatus(int membershipId, boolean active) {
        String sql = "UPDATE memberships SET is_active = ? WHERE membership_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, membershipId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating membership status: " + e.getMessage(), e);
        }
    }

    private MembershipPass mapRowToPass(ResultSet rs) throws SQLException {
        int id = rs.getInt("membership_id");
        int userId = rs.getInt("user_id");
        String plate = rs.getString("license_plate");

        VehicleType vType = VehicleType.CAR;
        try {
            String vStr = rs.getString("vehicle_type");
            if (vStr != null) vType = VehicleType.fromString(vStr);
        } catch (Exception ignored) {}

        int durationMonths = 1;
        try {
            durationMonths = rs.getInt("duration_months");
            if (durationMonths <= 0) durationMonths = 1;
        } catch (Exception ignored) {}

        double monthlyFee = 1500.0;
        try {
            monthlyFee = rs.getDouble("monthly_fee");
        } catch (Exception ignored) {}

        double totalPrice = monthlyFee * durationMonths;
        try {
            double tp = rs.getDouble("total_price");
            if (tp > 0) totalPrice = tp;
        } catch (Exception ignored) {}

        LocalDate start = LocalDate.parse(rs.getString("start_date"), FORMATTER);
        LocalDate expiry = LocalDate.parse(rs.getString("expiry_date"), FORMATTER);
        boolean active = rs.getInt("is_active") == 1;

        return new MembershipPass(id, userId, plate, vType, durationMonths, monthlyFee, totalPrice, start, expiry, active);
    }
}

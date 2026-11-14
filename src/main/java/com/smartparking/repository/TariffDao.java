package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.tariff.TariffRate;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Vehicle Tariffs in SQLite.
 */
public class TariffDao {

    public Optional<TariffRate> findByVehicleType(VehicleType type) {
        if (type == null) return Optional.empty();
        String sql = "SELECT * FROM tariffs WHERE vehicle_type = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, type.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToTariff(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying tariff for " + type + ": " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    /** Reads the tariff on the caller's connection (inside its transaction). */
    public Optional<TariffRate> findByVehicleType(Connection conn, VehicleType type) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM tariffs WHERE vehicle_type = ?;")) {
            pstmt.setString(1, type.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? Optional.of(mapRowToTariff(rs)) : Optional.empty();
            }
        }
    }

    public List<TariffRate> findAll() {
        List<TariffRate> list = new ArrayList<>();
        String sql = "SELECT * FROM tariffs ORDER BY daily_cap ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRowToTariff(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading tariffs: " + e.getMessage(), e);
        }
        return list;
    }

    public void updateTariff(TariffRate rate) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            updateTariff(conn, rate);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating tariff: " + e.getMessage(), e);
        }
    }

    public void updateTariff(Connection conn, TariffRate rate) throws SQLException {
        String sql = "INSERT OR REPLACE INTO tariffs (vehicle_type, first_hour, extra_hour, daily_cap, overnight_rate, monthly_pass_rate) " +
                "VALUES (?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rate.getVehicleType().name());
            ps.setDouble(2, rate.getFirstHourRate());
            ps.setDouble(3, rate.getExtraHourRate());
            ps.setDouble(4, rate.getDailyCap());
            ps.setDouble(5, rate.getOvernightRate());
            ps.setDouble(6, rate.getMonthlyPassRate());
            ps.executeUpdate();
        }
    }

    private TariffRate mapRowToTariff(ResultSet rs) throws SQLException {
        VehicleType type = VehicleType.fromString(rs.getString("vehicle_type"));
        double firstHour = rs.getDouble("first_hour");
        double extraHour = rs.getDouble("extra_hour");
        double dailyCap = rs.getDouble("daily_cap");
        double overnight = rs.getDouble("overnight_rate");
        double monthlyPass = rs.getDouble("monthly_pass_rate");
        return new TariffRate(type, firstHour, extraHour, dailyCap, overnight, monthlyPass);
    }
}

package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.ReservationStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.reservation.Reservation;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Reservation entities.
 */
public class ReservationDao {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public int save(Reservation reservation) {
        String sql = "INSERT INTO reservations (reservation_code, user_id, slot_id, license_plate, vehicle_type, " +
                "start_time, end_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, reservation.getReservationCode());
            pstmt.setInt(2, reservation.getUserId());
            pstmt.setString(3, reservation.getSlotId());
            pstmt.setString(4, reservation.getLicensePlate());
            pstmt.setString(5, reservation.getVehicleType().name());
            pstmt.setString(6, reservation.getStartTime().format(FORMATTER));
            pstmt.setString(7, reservation.getEndTime().format(FORMATTER));
            pstmt.setString(8, reservation.getStatus().name());

            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error saving reservation: " + e.getMessage(), e);
        }
        return 0;
    }

    public void updateStatus(int reservationId, ReservationStatus newStatus) {
        String sql = "UPDATE reservations SET status = ? WHERE reservation_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus.name());
            pstmt.setInt(2, reservationId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating reservation status: " + e.getMessage(), e);
        }
    }

    public List<Reservation> findConfirmedReservationsPastStartTime(LocalDateTime cutoff) {
        List<Reservation> list = new ArrayList<>();
        String sql = "SELECT * FROM reservations WHERE status = 'CONFIRMED' AND datetime(start_time) <= datetime(?) ORDER BY reservation_id ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cutoff.format(FORMATTER));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToReservation(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying expired reservations: " + e.getMessage(), e);
        }
        return list;
    }

    public Optional<Reservation> findByCode(String code) {
        if (code == null) return Optional.empty();
        String sql = "SELECT * FROM reservations WHERE reservation_code = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToReservation(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding reservation by code: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<Reservation> findActiveByPlate(String licensePlate) {
        if (licensePlate == null) return Optional.empty();
        String sql = "SELECT * FROM reservations WHERE LOWER(license_plate) = LOWER(?) AND status = 'CONFIRMED' " +
                "ORDER BY reservation_id DESC LIMIT 1;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, licensePlate.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToReservation(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding active reservation by plate: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Reservation> findByUserId(int userId) {
        List<Reservation> list = new ArrayList<>();
        String sql = "SELECT * FROM reservations WHERE user_id = ? ORDER BY reservation_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToReservation(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying reservations by user ID: " + e.getMessage(), e);
        }
        return list;
    }

    public boolean hasOverlap(String slotId, LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT COUNT(*) FROM reservations WHERE slot_id = ? AND status = 'CONFIRMED' " +
                "AND datetime(start_time) < datetime(?) AND datetime(end_time) > datetime(?);";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, slotId);
            pstmt.setString(2, end.format(FORMATTER));
            pstmt.setString(3, start.format(FORMATTER));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking reservation overlap: " + e.getMessage(), e);
        }
        return false;
    }

    public void extendEndTime(int reservationId, LocalDateTime newEnd) {
        String sql = "UPDATE reservations SET end_time = ? WHERE reservation_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newEnd.format(FORMATTER));
            pstmt.setInt(2, reservationId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error extending reservation: " + e.getMessage(), e);
        }
    }

    public List<Reservation> findAll() {
        List<Reservation> list = new ArrayList<>();
        String sql = "SELECT * FROM reservations ORDER BY reservation_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRowToReservation(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all reservations: " + e.getMessage(), e);
        }
        return list;
    }

    private Reservation mapRowToReservation(ResultSet rs) throws SQLException {
        int id = rs.getInt("reservation_id");
        String code = rs.getString("reservation_code");
        int userId = rs.getInt("user_id");
        String slotId = rs.getString("slot_id");
        String plate = rs.getString("license_plate");
        VehicleType vType = VehicleType.fromString(rs.getString("vehicle_type"));
        LocalDateTime start = LocalDateTime.parse(rs.getString("start_time"), FORMATTER);
        LocalDateTime end = LocalDateTime.parse(rs.getString("end_time"), FORMATTER);
        ReservationStatus status = ReservationStatus.valueOf(rs.getString("status"));

        return new Reservation(id, code, userId, slotId, plate, vType, start, end, status);
    }
}

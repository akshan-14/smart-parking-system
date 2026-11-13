package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.SlotType;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.slot.state.*;
import com.smartparking.model.vehicle.Car;
import com.smartparking.patterns.factory.SlotFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for ParkingSlot entities.
 */
public class ParkingSlotDao {

    public List<ParkingSlot> findAll() {
        List<ParkingSlot> slots = new ArrayList<>();
        String sql = "SELECT * FROM slots ORDER BY floor_number ASC, slot_number ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                slots.add(mapRowToSlot(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all parking slots: " + e.getMessage(), e);
        }
        return slots;
    }

    public Optional<ParkingSlot> findById(String slotId) {
        if (slotId == null) return Optional.empty();
        String sql = "SELECT * FROM slots WHERE slot_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, slotId.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToSlot(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying slot by ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<ParkingSlot> findByFloor(int floorNumber) {
        List<ParkingSlot> slots = new ArrayList<>();
        String sql = "SELECT * FROM slots WHERE floor_number = ? ORDER BY slot_number ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, floorNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    slots.add(mapRowToSlot(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying slots by floor: " + e.getMessage(), e);
        }
        return slots;
    }

    public void updateStatus(String slotId, String status, String vehiclePlate) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            updateStatusWithConnection(conn, slotId, status, vehiclePlate);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating slot status: " + e.getMessage(), e);
        }
    }

    public void updateStatusWithConnection(Connection conn, String slotId, String status, String vehiclePlate) throws SQLException {
        String sql = "UPDATE slots SET status = ?, current_vehicle_plate = ? WHERE slot_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.toUpperCase());
            pstmt.setString(2, vehiclePlate);
            pstmt.setString(3, slotId);
            pstmt.executeUpdate();
        }
    }

    public void updateHourlyRate(String slotId, double newRate) {
        String sql = "UPDATE slots SET hourly_rate = ? WHERE slot_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, newRate);
            pstmt.setString(2, slotId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating slot rate: " + e.getMessage(), e);
        }
    }

    public void save(ParkingSlot slot) {
        String sql = "INSERT INTO slots (slot_id, floor_number, slot_number, slot_type, hourly_rate, status, current_vehicle_plate) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, slot.getSlotId());
            pstmt.setInt(2, slot.getFloorNumber());
            pstmt.setString(3, slot.getSlotNumber());
            pstmt.setString(4, slot.getSlotType().name());
            pstmt.setDouble(5, slot.getHourlyRate());
            pstmt.setString(6, slot.getStatus());
            pstmt.setString(7, (slot.getCurrentVehicle() != null ? slot.getCurrentVehicle().getLicensePlate() : null));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving slot: " + e.getMessage(), e);
        }
    }

    public int countAvailableSlots() {
        return countByStatus("AVAILABLE");
    }

    public int countByStatus(String status) {
        if (status == null) return 0;
        String upper = status.toUpperCase();
        String sql;
        if ("MAINTENANCE".equals(upper) || "OUT_OF_SERVICE".equals(upper)) {
            sql = "SELECT COUNT(*) FROM slots WHERE status IN ('MAINTENANCE', 'OUT_OF_SERVICE');";
        } else {
            sql = "SELECT COUNT(*) FROM slots WHERE UPPER(status) = ?;";
        }
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (!("MAINTENANCE".equals(upper) || "OUT_OF_SERVICE".equals(upper))) {
                pstmt.setString(1, upper);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting slots with status " + status + ": " + e.getMessage(), e);
        }
        return 0;
    }

    public List<ParkingSlot> findAllByStatus(String status) {
        List<ParkingSlot> slots = new ArrayList<>();
        if (status == null) return slots;
        String upper = status.toUpperCase();
        String sql;
        if ("MAINTENANCE".equals(upper) || "OUT_OF_SERVICE".equals(upper)) {
            sql = "SELECT * FROM slots WHERE status IN ('MAINTENANCE', 'OUT_OF_SERVICE') ORDER BY floor_number, slot_number;";
        } else {
            sql = "SELECT * FROM slots WHERE UPPER(status) = ? ORDER BY floor_number, slot_number;";
        }
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (!("MAINTENANCE".equals(upper) || "OUT_OF_SERVICE".equals(upper))) {
                pstmt.setString(1, upper);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    slots.add(mapRowToSlot(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying slots with status " + status + ": " + e.getMessage(), e);
        }
        return slots;
    }

    public int countTotalSlots() {
        String sql = "SELECT COUNT(*) FROM slots;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting total slots: " + e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Atomically allocates an available slot using a conditional SQL update:
     * UPDATE slots SET status = 'OCCUPIED', current_vehicle_plate = ? WHERE slot_id = ? AND status = 'AVAILABLE';
     *
     * In SQLite WAL mode with immediate transactions, this guarantees zero race conditions:
     * returns true if and only if exactly 1 row was updated.
     */
    public boolean tryAllocateSlotAtomic(String slotId, String vehiclePlate) {
        String sql = "UPDATE slots SET status = 'OCCUPIED', current_vehicle_plate = ? " +
                "WHERE slot_id = ? AND status = 'AVAILABLE';";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vehiclePlate);
            ps.setString(2, slotId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error executing atomic slot allocation: " + e.getMessage(), e);
        }
    }

    private ParkingSlot mapRowToSlot(ResultSet rs) throws SQLException {
        String slotId = rs.getString("slot_id");
        int floorNum = rs.getInt("floor_number");
        String slotNum = rs.getString("slot_number");
        SlotType type = SlotType.fromString(rs.getString("slot_type"));
        double rate = rs.getDouble("hourly_rate");
        String status = rs.getString("status");
        String vehiclePlate = rs.getString("current_vehicle_plate");

        ParkingSlot slot = SlotFactory.createSlot(slotId, floorNum, slotNum, type, rate);

        // Apply State Pattern based on persistent status
        SlotState state = switch (status.toUpperCase()) {
            case "OCCUPIED" -> new OccupiedState();
            case "RESERVED" -> new ReservedState();
            case "MAINTENANCE", "OUT_OF_SERVICE" -> new MaintenanceState();
            default -> new AvailableState();
        };
        slot.setCurrentState(state);

        if (vehiclePlate != null && !vehiclePlate.trim().isEmpty()) {
            slot.setCurrentVehicle(new Car(vehiclePlate));
        }

        return slot;
    }
}

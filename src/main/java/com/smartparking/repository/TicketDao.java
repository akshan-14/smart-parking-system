package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.PenaltyType;
import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.ticket.Penalty;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Data Access Object for ParkingTicket and associated Penalty entities.
 */
public class TicketDao {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public int save(ParkingTicket ticket) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            return saveWithConnection(conn, ticket);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving ticket: " + e.getMessage(), e);
        }
    }

    public int saveWithConnection(Connection conn, ParkingTicket ticket) throws SQLException {
        String sql = "INSERT INTO tickets (ticket_number, license_plate, vehicle_type, slot_id, floor_number, " +
                "entry_time, exit_time, status, base_fee, penalty_fee, ev_charging_fee, total_amount) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, ticket.getTicketNumber());
            pstmt.setString(2, ticket.getLicensePlate());
            pstmt.setString(3, ticket.getVehicleType().name());
            pstmt.setString(4, ticket.getSlotId());
            pstmt.setInt(5, ticket.getFloorNumber());
            pstmt.setString(6, ticket.getEntryTime().format(FORMATTER));
            pstmt.setString(7, ticket.getExitTime() != null ? ticket.getExitTime().format(FORMATTER) : null);
            pstmt.setString(8, ticket.getStatus().name());
            pstmt.setDouble(9, ticket.getBaseFee());
            pstmt.setDouble(10, ticket.getPenaltyFee());
            pstmt.setDouble(11, ticket.getEvChargingFee());
            pstmt.setDouble(12, ticket.getTotalAmount());

            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public void update(ParkingTicket ticket) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            updateWithConnection(conn, ticket);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating ticket: " + e.getMessage(), e);
        }
    }

    public void updateWithConnection(Connection conn, ParkingTicket ticket) throws SQLException {
        String sql = "UPDATE tickets SET exit_time = ?, status = ?, base_fee = ?, penalty_fee = ?, " +
                "ev_charging_fee = ?, total_amount = ? WHERE ticket_id = ? OR ticket_number = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticket.getExitTime() != null ? ticket.getExitTime().format(FORMATTER) : null);
            pstmt.setString(2, ticket.getStatus().name());
            pstmt.setDouble(3, ticket.getBaseFee());
            pstmt.setDouble(4, ticket.getPenaltyFee());
            pstmt.setDouble(5, ticket.getEvChargingFee());
            pstmt.setDouble(6, ticket.getTotalAmount());
            pstmt.setInt(7, ticket.getTicketId());
            pstmt.setString(8, ticket.getTicketNumber());
            pstmt.executeUpdate();
        }
    }

    public Optional<ParkingTicket> findById(int ticketId) {
        String sql = "SELECT * FROM tickets WHERE ticket_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ticketId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToTicket(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding ticket by ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<ParkingTicket> findByTicketNumber(String ticketNumber) {
        if (ticketNumber == null) return Optional.empty();
        String sql = "SELECT * FROM tickets WHERE ticket_number = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketNumber.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToTicket(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding ticket by number: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<ParkingTicket> findActiveByLicensePlate(String licensePlate) {
        if (licensePlate == null) return Optional.empty();
        String norm = licensePlate.replaceAll("[\\s\\-]+", "").toUpperCase(Locale.ENGLISH).trim();
        String sql = "SELECT * FROM tickets WHERE (REPLACE(REPLACE(UPPER(license_plate), ' ', ''), '-', '') = ? " +
                "OR UPPER(license_plate) = ?) AND status IN ('ACTIVE', 'PAID') ORDER BY ticket_id DESC LIMIT 1;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, norm);
            pstmt.setString(2, norm);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToTicket(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding active ticket by plate: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<ParkingTicket> searchActiveByPlate(String query) {
        List<ParkingTicket> list = new ArrayList<>();
        if (query == null || query.isBlank()) return list;
        String norm = query.replaceAll("[\\s\\-]+", "").toUpperCase(Locale.ENGLISH).trim();
        String sql = "SELECT * FROM tickets WHERE REPLACE(REPLACE(UPPER(license_plate), ' ', ''), '-', '') LIKE ? " +
                "AND status IN ('ACTIVE', 'PAID') ORDER BY ticket_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + norm + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTicket(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching active tickets by plate: " + e.getMessage(), e);
        }
        return list;
    }

    public List<ParkingTicket> findAll() {
        List<ParkingTicket> tickets = new ArrayList<>();
        String sql = "SELECT * FROM tickets ORDER BY ticket_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tickets.add(mapRowToTicket(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all tickets: " + e.getMessage(), e);
        }
        return tickets;
    }

    public List<ParkingTicket> findAllActive() {
        List<ParkingTicket> tickets = new ArrayList<>();
        String sql = "SELECT * FROM tickets WHERE status = 'ACTIVE' ORDER BY ticket_id ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tickets.add(mapRowToTicket(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying active tickets: " + e.getMessage(), e);
        }
        return tickets;
    }

    public List<ParkingTicket> findRecent(int limit) {
        List<ParkingTicket> tickets = new ArrayList<>();
        String sql = "SELECT * FROM tickets ORDER BY ticket_id DESC LIMIT ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    tickets.add(mapRowToTicket(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying recent tickets: " + e.getMessage(), e);
        }
        return tickets;
    }

    public java.util.Map<VehicleType, Integer> getOccupiedVehicleTypeCounts() {
        java.util.Map<VehicleType, Integer> map = new java.util.EnumMap<>(VehicleType.class);
        for (VehicleType vt : VehicleType.values()) {
            map.put(vt, 0);
        }
        String sql = "SELECT vehicle_type, COUNT(*) FROM tickets WHERE status = 'ACTIVE' GROUP BY vehicle_type;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    VehicleType vt = VehicleType.fromString(rs.getString(1));
                    map.put(vt, map.getOrDefault(vt, 0) + rs.getInt(2));
                } catch (Exception ignored) {
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting occupied vehicle types: " + e.getMessage(), e);
        }
        return map;
    }

    public java.util.Map<java.time.LocalDate, int[]> getTurnoverMetrics(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        java.util.Map<java.time.LocalDate, int[]> result = new java.util.LinkedHashMap<>();
        java.time.LocalDate curr = startDate;
        while (!curr.isAfter(endDate)) {
            result.put(curr, new int[]{0, 0});
            curr = curr.plusDays(1);
        }

        String sqlEntry = "SELECT substr(entry_time, 1, 10) as dt, count(*) FROM tickets " +
                "WHERE substr(entry_time, 1, 10) >= ? AND substr(entry_time, 1, 10) <= ? GROUP BY dt;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlEntry)) {
            ps.setString(1, startDate.toString());
            ps.setString(2, endDate.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(rs.getString(1));
                        int[] counts = result.get(d);
                        if (counts != null) counts[0] = rs.getInt(2);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying entry turnover: " + e.getMessage(), e);
        }

        String sqlExit = "SELECT substr(exit_time, 1, 10) as dt, count(*) FROM tickets " +
                "WHERE exit_time IS NOT NULL AND substr(exit_time, 1, 10) >= ? AND substr(exit_time, 1, 10) <= ? GROUP BY dt;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlExit)) {
            ps.setString(1, startDate.toString());
            ps.setString(2, endDate.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(rs.getString(1));
                        int[] counts = result.get(d);
                        if (counts != null) counts[1] = rs.getInt(2);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying exit turnover: " + e.getMessage(), e);
        }

        return result;
    }

    public List<ParkingTicket> findOverstayTickets(int maxPermittedHours) {
        List<ParkingTicket> list = new ArrayList<>();
        LocalDateTime cutoff = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusHours(maxPermittedHours);
        String sql = "SELECT * FROM tickets WHERE status = 'ACTIVE' AND datetime(entry_time) <= datetime(?) ORDER BY entry_time ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cutoff.format(FORMATTER));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTicket(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying overstay tickets: " + e.getMessage(), e);
        }
        return list;
    }

    public void savePenalty(Penalty penalty) {
        String sql = "INSERT INTO penalties (ticket_id, penalty_type, amount, reason, issued_at) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, penalty.getTicketId());
            pstmt.setString(2, penalty.getType().name());
            pstmt.setDouble(3, penalty.getAmount());
            pstmt.setString(4, penalty.getReason());
            pstmt.setString(5, penalty.getIssuedAt().format(FORMATTER));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving penalty: " + e.getMessage(), e);
        }
    }

    public List<Penalty> findPenaltiesByTicketId(int ticketId) {
        List<Penalty> penalties = new ArrayList<>();
        String sql = "SELECT * FROM penalties WHERE ticket_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ticketId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int pId = rs.getInt("penalty_id");
                    PenaltyType type = PenaltyType.valueOf(rs.getString("penalty_type"));
                    double amt = rs.getDouble("amount");
                    String reason = rs.getString("reason");
                    LocalDateTime issuedAt = parseDateTime(rs.getString("issued_at"));
                    penalties.add(new Penalty(pId, ticketId, type, amt, reason, issuedAt));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying penalties by ticket ID: " + e.getMessage(), e);
        }
        return penalties;
    }

    private ParkingTicket mapRowToTicket(ResultSet rs) throws SQLException {
        int id = rs.getInt("ticket_id");
        String number = rs.getString("ticket_number");
        String plate = rs.getString("license_plate");
        VehicleType vType = VehicleType.fromString(rs.getString("vehicle_type"));
        String slotId = rs.getString("slot_id");
        int floor = rs.getInt("floor_number");
        LocalDateTime entryTime = parseDateTime(rs.getString("entry_time"));
        String exitStr = rs.getString("exit_time");
        LocalDateTime exitTime = (exitStr != null && !exitStr.isEmpty()) ? parseDateTime(exitStr) : null;
        TicketStatus status = TicketStatus.valueOf(rs.getString("status"));
        double base = rs.getDouble("base_fee");
        double penalty = rs.getDouble("penalty_fee");
        double ev = rs.getDouble("ev_charging_fee");
        double total = rs.getDouble("total_amount");

        List<Penalty> penalties = findPenaltiesByTicketId(id);

        return new ParkingTicket(id, number, plate, vType, slotId, floor, entryTime, exitTime, status, base, penalty, ev, total, penalties);
    }

    private LocalDateTime parseDateTime(String text) {
        try {
            return LocalDateTime.parse(text, FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}

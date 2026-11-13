package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.PaymentStatus;
import com.smartparking.model.payment.PaymentTransaction;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for PaymentTransaction entities.
 */
public class PaymentDao {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public int save(PaymentTransaction payment) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            return saveWithConnection(conn, payment);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving payment: " + e.getMessage(), e);
        }
    }

    public int saveWithConnection(Connection conn, PaymentTransaction payment) throws SQLException {
        String sql = "INSERT INTO payments (ticket_id, reservation_id, membership_id, amount, " +
                "taxable_value, cgst, sgst, round_off, payment_method, " +
                "transaction_ref, invoice_number, status, payment_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (payment.getTicketId() != null) {
                pstmt.setInt(1, payment.getTicketId());
            } else {
                pstmt.setNull(1, Types.INTEGER);
            }

            if (payment.getReservationId() != null) {
                pstmt.setInt(2, payment.getReservationId());
            } else {
                pstmt.setNull(2, Types.INTEGER);
            }

            if (payment.getMembershipId() != null) {
                pstmt.setInt(3, payment.getMembershipId());
            } else {
                pstmt.setNull(3, Types.INTEGER);
            }

            pstmt.setDouble(4, payment.getAmount());
            pstmt.setDouble(5, payment.getTaxableValue());
            pstmt.setDouble(6, payment.getCgst());
            pstmt.setDouble(7, payment.getSgst());
            pstmt.setDouble(8, payment.getRoundOff());
            pstmt.setString(9, payment.getPaymentMethod().name());
            pstmt.setString(10, payment.getTransactionRef());
            if (payment.getInvoiceNumber() != null) {
                pstmt.setString(11, payment.getInvoiceNumber());
            } else {
                pstmt.setNull(11, Types.VARCHAR);
            }
            pstmt.setString(12, payment.getStatus().name());
            pstmt.setString(13, payment.getPaymentTime().format(FORMATTER));

            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public Optional<PaymentTransaction> findByRef(String ref) {
        if (ref == null) return Optional.empty();
        String sql = "SELECT * FROM payments WHERE transaction_ref = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ref.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPayment(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding payment by ref: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<PaymentTransaction> findByTicketId(int ticketId) {
        List<PaymentTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM payments WHERE ticket_id = ? ORDER BY payment_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ticketId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToPayment(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying payments by ticket ID: " + e.getMessage(), e);
        }
        return list;
    }

    public List<PaymentTransaction> findAll() {
        List<PaymentTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM payments ORDER BY payment_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRowToPayment(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all payments: " + e.getMessage(), e);
        }
        return list;
    }

    public double getTotalRevenue() {
        String sql = "SELECT SUM(amount) FROM payments WHERE status = 'COMPLETED';";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error calculating total revenue: " + e.getMessage(), e);
        }
        return 0.0;
    }

    public double getTodayRevenue() {
        java.time.LocalDate todayIST = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        return getRevenueForDate(todayIST);
    }

    public double getRevenueForDate(java.time.LocalDate date) {
        if (date == null) date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        String prefix = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "%";
        String sql = "SELECT SUM(amount) FROM payments WHERE status = 'COMPLETED' AND payment_time LIKE ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prefix);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error calculating date revenue: " + e.getMessage(), e);
        }
        return 0.0;
    }

    public double getMonthRevenue(int year, int month) {
        String prefix = String.format("%04d-%02d%%", year, month);
        String sql = "SELECT SUM(amount) FROM payments WHERE status = 'COMPLETED' AND payment_time LIKE ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prefix);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error calculating month revenue: " + e.getMessage(), e);
        }
        return 0.0;
    }

    public void updateStatus(int paymentId, PaymentStatus status) {
        String sql = "UPDATE payments SET status = ? WHERE payment_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setInt(2, paymentId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating payment status: " + e.getMessage(), e);
        }
    }

    public Optional<PaymentTransaction> findByInvoiceNumber(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM payments WHERE invoice_number = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, invoiceNumber.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPayment(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding payment by invoice number: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public boolean refund(int paymentId, String reason, String adminUser) {
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("A valid refund reason (minimum 5 characters) is required.");
        }
        String updateSql = "UPDATE payments SET status = 'REFUNDED' WHERE payment_id = ? AND status = 'COMPLETED';";
        String auditSql = "INSERT INTO audit_log (action, performed_by, ticket_id, old_value, new_value, reason, timestamp) " +
                "VALUES ('PAYMENT_REFUND', ?, ?, 'COMPLETED', 'REFUNDED', ?, ?);";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pu = conn.prepareStatement(updateSql);
                 PreparedStatement pa = conn.prepareStatement(auditSql)) {

                pu.setInt(1, paymentId);
                int rows = pu.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }

                pa.setString(1, adminUser != null ? adminUser : "admin");
                pa.setString(2, "Payment #" + paymentId);
                pa.setString(3, reason.trim());
                pa.setString(4, LocalDateTime.now().format(FORMATTER));
                pa.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error refunding payment: " + e.getMessage(), e);
        }
    }

    private PaymentTransaction mapRowToPayment(ResultSet rs) throws SQLException {
        int id = rs.getInt("payment_id");
        int ticketId = rs.getInt("ticket_id");
        Integer tId = rs.wasNull() ? null : ticketId;
        int resId = rs.getInt("reservation_id");
        Integer rId = rs.wasNull() ? null : resId;

        Integer mId = null;
        try {
            int memId = rs.getInt("membership_id");
            if (!rs.wasNull()) mId = memId;
        } catch (SQLException ignored) {}

        double amount = rs.getDouble("amount");

        double taxableValue = 0.0;
        try {
            taxableValue = rs.getDouble("taxable_value");
        } catch (SQLException ignored) {}

        double cgst = 0.0;
        try {
            cgst = rs.getDouble("cgst");
        } catch (SQLException ignored) {}

        double sgst = 0.0;
        try {
            sgst = rs.getDouble("sgst");
        } catch (SQLException ignored) {}

        double roundOff = 0.0;
        try {
            roundOff = rs.getDouble("round_off");
        } catch (SQLException ignored) {}

        PaymentMethodType method = PaymentMethodType.valueOf(rs.getString("payment_method"));
        String ref = rs.getString("transaction_ref");

        String invoiceNumber = null;
        try {
            invoiceNumber = rs.getString("invoice_number");
        } catch (SQLException ignored) {}

        PaymentStatus status = PaymentStatus.valueOf(rs.getString("status"));
        LocalDateTime time = LocalDateTime.parse(rs.getString("payment_time"), FORMATTER);

        return new PaymentTransaction(id, tId, rId, mId, amount, taxableValue, cgst, sgst, roundOff,
                method, ref, invoiceNumber, status, time);
    }
}

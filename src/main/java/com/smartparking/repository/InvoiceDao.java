package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.invoice.Invoice;
import com.smartparking.util.PlateUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for GST Tax Invoices in SQLite.
 */
public class InvoiceDao {

    public void save(Connection conn, Invoice invoice) throws SQLException {
        String sql = "INSERT INTO invoices (invoice_number, financial_year, invoice_date, ticket_id, " +
                "membership_id, license_plate, vehicle_type, taxable_value, cgst_rate, cgst_amount, " +
                "sgst_rate, sgst_amount, round_off, total_amount, amount_in_words, payment_method, " +
                "transaction_ref, qr_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, invoice.getInvoiceNumber());
            ps.setString(2, invoice.getFinancialYear());
            ps.setString(3, invoice.getInvoiceDate());
            if (invoice.getTicketId() != null) ps.setInt(4, invoice.getTicketId()); else ps.setNull(4, Types.INTEGER);
            if (invoice.getMembershipId() != null) ps.setInt(5, invoice.getMembershipId()); else ps.setNull(5, Types.INTEGER);
            ps.setString(6, invoice.getLicensePlate());
            ps.setString(7, invoice.getVehicleType().name());
            ps.setDouble(8, invoice.getTaxableValue());
            ps.setDouble(9, invoice.getCgstRate());
            ps.setDouble(10, invoice.getCgstAmount());
            ps.setDouble(11, invoice.getSgstRate());
            ps.setDouble(12, invoice.getSgstAmount());
            ps.setDouble(13, invoice.getRoundOff());
            ps.setDouble(14, invoice.getTotalAmount());
            ps.setString(15, invoice.getAmountInWords());
            ps.setString(16, invoice.getPaymentMethod());
            ps.setString(17, invoice.getTransactionRef());
            ps.setString(18, invoice.getQrData());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    invoice.setInvoiceId(rs.getInt(1));
                }
            }
        }
    }

    public Optional<Invoice> findByInvoiceNumber(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM invoices WHERE invoice_number = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, invoiceNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying invoice by number: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Invoice> findByPlate(String plate) {
        List<Invoice> list = new ArrayList<>();
        if (plate == null || plate.isBlank()) return list;
        String norm = PlateUtil.normalize(plate);
        String sql = "SELECT * FROM invoices WHERE license_plate = ? COLLATE NOCASE ORDER BY invoice_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, norm);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying invoices by plate: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Invoice> findRecent(int limit) {
        List<Invoice> list = new ArrayList<>();
        String sql = "SELECT * FROM invoices ORDER BY invoice_id DESC LIMIT ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying recent invoices: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Invoice> findAll() {
        List<Invoice> list = new ArrayList<>();
        String sql = "SELECT * FROM invoices ORDER BY invoice_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all invoices: " + e.getMessage(), e);
        }
        return list;
    }

    public List<Invoice> search(String query) {
        List<Invoice> list = new ArrayList<>();
        if (query == null || query.isBlank()) return findAll();
        String pattern = "%" + query.trim() + "%";
        String sql = "SELECT * FROM invoices WHERE invoice_number LIKE ? OR license_plate LIKE ? ORDER BY invoice_id DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching invoices: " + e.getMessage(), e);
        }
        return list;
    }

    private Invoice mapRow(ResultSet rs) throws SQLException {
        Integer tId = rs.getObject("ticket_id") != null ? rs.getInt("ticket_id") : null;
        Integer mId = rs.getObject("membership_id") != null ? rs.getInt("membership_id") : null;

        return new Invoice(
                rs.getInt("invoice_id"),
                rs.getString("invoice_number"),
                rs.getString("financial_year"),
                rs.getString("invoice_date"),
                tId,
                mId,
                rs.getString("license_plate"),
                VehicleType.fromString(rs.getString("vehicle_type")),
                null, null, 0,
                rs.getDouble("taxable_value"),
                rs.getDouble("cgst_rate"),
                rs.getDouble("cgst_amount"),
                rs.getDouble("sgst_rate"),
                rs.getDouble("sgst_amount"),
                rs.getDouble("round_off"),
                rs.getDouble("total_amount"),
                rs.getString("amount_in_words"),
                rs.getString("payment_method"),
                rs.getString("transaction_ref"),
                rs.getString("qr_data")
        );
    }
}

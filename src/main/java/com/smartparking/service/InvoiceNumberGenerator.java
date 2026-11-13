package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Service for generating sequential, tamper-proof GST Tax Invoice numbers.
 * Format specified: PK/<financial year e.g. 2026-27>/<6-digit running number>
 * Example: PK/2026-27/000001
 * Financial year resets every April 1st according to Indian Government rules.
 */
public class InvoiceNumberGenerator {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * Determines current Indian Financial Year from date.
     * Indian FY starts on 1 April and ends on 31 March.
     * Example: Nov 2026 -> "2026-27", Jan 2027 -> "2026-27", May 2027 -> "2027-28"
     */
    public static String getFinancialYear(LocalDate date) {
        if (date == null) date = LocalDate.now(IST_ZONE);
        int year = date.getYear();
        int month = date.getMonthValue();

        int startYear = (month >= 4) ? year : (year - 1);
        int endYearShort = (startYear + 1) % 100;
        return String.format("%d-%02d", startYear, endYearShort);
    }

    public static String getFinancialYear() {
        return getFinancialYear(LocalDate.now(IST_ZONE));
    }

    /**
     * Atomically increments and returns the next sequential invoice number for the financial year.
     * Guaranteed sequential and gapless per financial year inside a transaction.
     */
    public synchronized String nextInvoiceNumber(Connection conn, LocalDate date) throws SQLException {
        String fy = getFinancialYear(date);
        return nextInvoiceNumberForFy(conn, fy);
    }

    public synchronized String nextInvoiceNumber(Connection conn) throws SQLException {
        return nextInvoiceNumber(conn, LocalDate.now(IST_ZONE));
    }

    public synchronized String nextInvoiceNumber() {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            return nextInvoiceNumber(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Error generating next invoice number: " + e.getMessage(), e);
        }
    }

    public synchronized String nextInvoiceNumberForFy(Connection conn, String fy) throws SQLException {
        // Upsert sequence entry for this financial year
        String upsertSql = "INSERT INTO invoice_sequences (financial_year, current_seq) VALUES (?, 1) " +
                "ON CONFLICT(financial_year) DO UPDATE SET current_seq = invoice_sequences.current_seq + 1;";

        try (PreparedStatement pstmt = conn.prepareStatement(upsertSql)) {
            pstmt.setString(1, fy);
            pstmt.executeUpdate();
        }

        // Query the newly assigned sequence number
        String selectSql = "SELECT current_seq FROM invoice_sequences WHERE financial_year = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setString(1, fy);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int seq = rs.getInt("current_seq");
                    return String.format("PK/%s/%06d", fy, seq);
                }
            }
        }

        throw new SQLException("Failed to obtain sequential invoice number for FY " + fy);
    }
}

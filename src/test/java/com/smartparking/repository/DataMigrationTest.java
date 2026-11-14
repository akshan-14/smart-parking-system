package com.smartparking.repository;

import com.smartparking.support.TestDatabase;
import com.smartparking.config.DatabaseConnectionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests for Database Migrations, Payment Backfills, and Invoice Number Assignment")
class DataMigrationTest {

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_migration");
        DatabaseInitializer.initializeDatabase();
    }

    @Test
    @DisplayName("Verify no completed payment has amount = 0 unless ticket was free")
    void testNoCompletedPaymentIsZero() throws Exception {
        String sql = "SELECT p.payment_id, p.amount, p.taxable_value, p.cgst, p.sgst, p.invoice_number, t.status as ticket_status " +
                "FROM payments p LEFT JOIN tickets t ON p.ticket_id = t.ticket_id " +
                "WHERE p.status = 'COMPLETED';";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int checked = 0;
            while (rs.next()) {
                double amount = rs.getDouble("amount");
                double taxable = rs.getDouble("taxable_value");
                String inv = rs.getString("invoice_number");

                assertTrue(amount > 0.0, "Completed payment should have a positive non-zero amount");
                assertTrue(taxable > 0.0, "Completed payment should have a positive taxable value");
                assertNotNull(inv, "Completed payment must have an assigned invoice number");
                assertTrue(inv.startsWith("PK/"), "Invoice number must follow PK/YYYY-YY/XXXXXX format");
                checked++;
            }
            assertTrue(checked > 0, "There should be historical completed payments in the seeded database");
        }
    }

    @Test
    @DisplayName("Verify FASTag seeded accounts exist and have positive balances")
    void testFastagAccountsSeeded() throws Exception {
        String sql = "SELECT count(*), min(balance) FROM fastag_accounts WHERE status = 'ACTIVE';";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            assertTrue(rs.next());
            int count = rs.getInt(1);
            double minBalance = rs.getDouble(2);

            assertTrue(count >= 4, "At least 4 demo FASTag accounts must be seeded");
            assertTrue(minBalance > 0.0, "All active FASTag accounts should have positive balances");
        }
    }
}

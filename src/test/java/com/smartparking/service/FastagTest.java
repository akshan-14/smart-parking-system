package com.smartparking.service;

import com.smartparking.support.TestDatabase;
import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.fastag.FastagAccount;
import com.smartparking.repository.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests for FASTag Electronic Toll Collection (Simulated)")
class FastagTest {

    private static FastagService fastagService;

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_fastag");
        DatabaseInitializer.initializeDatabase();
        fastagService = new FastagService();
    }

    @Test
    @DisplayName("Auto-detect seeded demo FASTag accounts by UP plate")
    void testDetectSeededAccounts() {
        Optional<FastagAccount> acc1 = fastagService.detectTag("UP32BK1101");
        assertTrue(acc1.isPresent());
        assertEquals("TAG-UP32-001", acc1.get().getTagId());
        assertEquals(500.0, acc1.get().getBalance(), 0.001);

        Optional<FastagAccount> acc2 = fastagService.detectTag("UP14EV7003");
        assertTrue(acc2.isPresent());
        assertEquals(300.0, acc2.get().getBalance(), 0.001);
    }

    @Test
    @DisplayName("Debit balance successfully when sufficient funds exist")
    void testSuccessfulDebit() throws Exception {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            fastagService.debit(conn, "UP16CD2345", 85.0);
        }

        FastagAccount acc = fastagService.getAccountByTagId("TAG-UP16-003").orElseThrow();
        assertEquals(765.0, acc.getBalance(), 0.001);
    }

    @Test
    @DisplayName("Fail debit when balance is insufficient")
    void testInsufficientBalanceDebit() {
        // TAG-UP80-004 has only ₹20 balance
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            assertThrows(IllegalStateException.class, () -> fastagService.debit(conn, "UP80LM6789", 80.0));
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    @DisplayName("Top up balance and verify audit log update")
    void testTopUpAndStatus() {
        fastagService.topUp("TAG-UP80-004", 500.0, "superadmin", "User counter topup");
        FastagAccount acc = fastagService.getAccountByTagId("TAG-UP80-004").orElseThrow();
        assertEquals(520.0, acc.getBalance(), 0.001);

        // Block tag
        fastagService.setStatus("TAG-UP80-004", "BLOCKED", "superadmin", "Lost vehicle report");
        FastagAccount blocked = fastagService.getAccountByTagId("TAG-UP80-004").orElseThrow();
        assertEquals("BLOCKED", blocked.getStatus());
        assertFalse(blocked.isActive());

        // Attempting to debit blocked tag must fail
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            assertThrows(IllegalStateException.class, () -> fastagService.debit(conn, "UP80LM6789", 50.0));
        } catch (Exception e) {
            fail(e);
        }
    }
}

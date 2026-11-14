package com.smartparking.service;

import com.smartparking.support.TestDatabase;
import com.smartparking.repository.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests for Sequential Financial Year GST Invoice Numbering")
class InvoiceNumberGeneratorTest {

    private static InvoiceNumberGenerator generator;

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_invoice_seq");
        DatabaseInitializer.initializeDatabase();
        generator = new InvoiceNumberGenerator();
    }

    @Test
    @DisplayName("Indian Financial Year boundaries starting April 1st")
    void testFinancialYearCalculation() {
        // Nov 2026 -> FY 2026-27
        assertEquals("2026-27", InvoiceNumberGenerator.getFinancialYear(LocalDate.of(2026, 11, 9)));

        // Jan 2027 -> FY 2026-27
        assertEquals("2026-27", InvoiceNumberGenerator.getFinancialYear(LocalDate.of(2027, 1, 15)));

        // Mar 31, 2027 -> FY 2026-27
        assertEquals("2026-27", InvoiceNumberGenerator.getFinancialYear(LocalDate.of(2027, 3, 31)));

        // Apr 1, 2027 -> FY 2027-28
        assertEquals("2027-28", InvoiceNumberGenerator.getFinancialYear(LocalDate.of(2027, 4, 1)));
    }

    @Test
    @DisplayName("Invoice numbers have no duplicates under concurrent calls")
    void testConcurrentCallsNoDuplicates() throws Exception {
        int threads = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.Set<String> generated = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    String inv = generator.nextInvoiceNumber();
                    generated.add(inv);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threads, generated.size(), "All concurrent invoice numbers must be unique without collisions");
    }

    @Test
    @DisplayName("Invoice numbers follow PK/YYYY-YY/XXXXXX format and increment sequentially without gaps")
    void testSequentialIncrement() {
        String inv1 = generator.nextInvoiceNumber();
        String inv2 = generator.nextInvoiceNumber();

        assertTrue(inv1.matches("^PK/[0-9]{4}-[0-9]{2}/[0-9]{6}$"), "Must match format: " + inv1);
        assertTrue(inv2.matches("^PK/[0-9]{4}-[0-9]{2}/[0-9]{6}$"), "Must match format: " + inv2);

        int seq1 = Integer.parseInt(inv1.substring(inv1.lastIndexOf('/') + 1));
        int seq2 = Integer.parseInt(inv2.substring(inv2.lastIndexOf('/') + 1));
        assertEquals(seq1 + 1, seq2, "Sequence must be strictly contiguous and increment by 1");
    }
}

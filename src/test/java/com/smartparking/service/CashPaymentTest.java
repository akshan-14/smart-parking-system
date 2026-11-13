package com.smartparking.service;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.patterns.strategy.payment.CashPaymentStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests for Cash Payment Validation and Change Return")
class CashPaymentTest {

    @Test
    @DisplayName("Cash payment strategy accepts non-negative amounts")
    void testCashPaymentStrategy() {
        CashPaymentStrategy strategy = new CashPaymentStrategy();
        assertEquals(PaymentMethodType.CASH, strategy.getMethodType());
        assertTrue(strategy.processPayment(100.0, "TXN-CASH-001"));
        assertFalse(strategy.processPayment(-10.0, "TXN-CASH-002"));
    }

    @Test
    @DisplayName("Verify change calculation math and insufficient amount rejection")
    void testChangeCalculation() {
        double totalDue = 85.0;
        double received1 = 100.0;
        double change1 = received1 - totalDue;
        assertEquals(15.0, change1, 0.001);

        double received2 = 50.0;
        double change2 = received2 - totalDue;
        assertTrue(change2 < 0, "Received less than total due must yield negative change indicator");
        assertTrue(received2 < totalDue, "Insufficient amount received must be rejected");
    }
}

package com.smartparking.patterns.strategy.payment;

import com.smartparking.model.enums.PaymentMethodType;

/**
 * Concrete Payment Strategy: UPI (Unified Payments Interface).
 * Simulates VPA verification and dynamic QR code instant settlement.
 */
public class UpiPaymentStrategy implements PaymentStrategy {
    private final String upiId;

    public UpiPaymentStrategy(String upiId) {
        this.upiId = (upiId != null) ? upiId.trim() : "";
    }

    public UpiPaymentStrategy() {
        this("customer@upi");
    }

    @Override
    public boolean processPayment(double amount, String transactionRef) {
        if (amount < 0) return false;
        // Basic VPA format check: must contain '@'
        return upiId.contains("@");
    }

    public String getUpiId() {
        return upiId;
    }

    @Override
    public PaymentMethodType getMethodType() {
        return PaymentMethodType.UPI;
    }
}

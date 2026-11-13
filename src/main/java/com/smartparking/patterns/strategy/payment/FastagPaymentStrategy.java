package com.smartparking.patterns.strategy.payment;

import com.smartparking.model.enums.PaymentMethodType;

/**
 * Strategy for FASTag auto-debit payments.
 * Simulates electronic toll collection (ETC) RFID debit.
 * Note: Labeled (Simulated) per project specifications.
 */
public class FastagPaymentStrategy implements PaymentStrategy {

    @Override
    public boolean processPayment(double amount, String transactionRef) {
        // FASTag auto-debit simulation: succeeds if amount >= 0
        System.out.println("[FASTAG (Simulated)] Auto-debiting \u20B9" + String.format("%.2f", amount) + " for " + transactionRef);
        return amount >= 0.0;
    }

    @Override
    public PaymentMethodType getMethodType() {
        return PaymentMethodType.FASTAG;
    }
}

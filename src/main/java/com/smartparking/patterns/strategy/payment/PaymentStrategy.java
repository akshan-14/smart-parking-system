package com.smartparking.patterns.strategy.payment;

import com.smartparking.model.enums.PaymentMethodType;

/**
 * Strategy Pattern interface for payment execution.
 * Encapsulates payment gateway / transaction logic for different financial channels.
 */
public interface PaymentStrategy {

    /**
     * Executes the simulated payment.
     * @param amount The charge amount to collect
     * @param transactionRef The unique idempotency/audit reference
     * @return true if payment succeeded, false otherwise
     */
    boolean processPayment(double amount, String transactionRef);

    /**
     * Returns the payment method category.
     */
    PaymentMethodType getMethodType();
}

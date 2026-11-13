package com.smartparking.patterns.strategy.payment;

import com.smartparking.model.enums.PaymentMethodType;

/**
 * Concrete Payment Strategy: Cash Payment at Attendant Booth.
 */
public class CashPaymentStrategy implements PaymentStrategy {
    private double tenderedAmount;

    public CashPaymentStrategy(double tenderedAmount) {
        this.tenderedAmount = tenderedAmount;
    }

    public CashPaymentStrategy() {
        this(0.0);
    }

    @Override
    public boolean processPayment(double amount, String transactionRef) {
        if (amount < 0) return false;
        // In physical booth, attendant accepts cash
        if (tenderedAmount > 0 && tenderedAmount < amount) {
            return false; // Insufficient cash tendered
        }
        return true;
    }

    public double getChange(double amount) {
        return Math.max(0.0, tenderedAmount - amount);
    }

    public void setTenderedAmount(double tenderedAmount) {
        this.tenderedAmount = tenderedAmount;
    }

    @Override
    public PaymentMethodType getMethodType() {
        return PaymentMethodType.CASH;
    }
}

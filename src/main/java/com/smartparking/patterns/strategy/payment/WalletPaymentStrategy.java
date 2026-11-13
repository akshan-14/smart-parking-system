package com.smartparking.patterns.strategy.payment;

import com.smartparking.model.enums.PaymentMethodType;

/**
 * Concrete Payment Strategy: Digital Wallet.
 * Simulates wallet balance authorization and deduction.
 */
public class WalletPaymentStrategy implements PaymentStrategy {
    private final String walletProvider;
    private double walletBalance;

    public WalletPaymentStrategy(String walletProvider, double walletBalance) {
        this.walletProvider = (walletProvider != null) ? walletProvider.trim() : "Default Wallet";
        this.walletBalance = Math.max(0.0, walletBalance);
    }

    public WalletPaymentStrategy() {
        this("PayTM / GooglePay Wallet", 1000.0);
    }

    @Override
    public boolean processPayment(double amount, String transactionRef) {
        if (amount < 0 || walletBalance < amount) {
            return false;
        }
        walletBalance -= amount;
        return true;
    }

    public String getWalletProvider() {
        return walletProvider;
    }

    public double getWalletBalance() {
        return walletBalance;
    }

    @Override
    public PaymentMethodType getMethodType() {
        return PaymentMethodType.WALLET;
    }
}

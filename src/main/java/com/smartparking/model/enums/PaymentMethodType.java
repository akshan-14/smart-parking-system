package com.smartparking.model.enums;

/**
 * Supported payment methods in the parking system.
 */
public enum PaymentMethodType {
    CASH("Cash Payment"),
    CARD("Credit / Debit Card"),
    UPI("Unified Payments Interface (UPI)"),
    WALLET("Digital Wallet"),
    FASTAG("FASTag Auto-Debit (Simulated)");

    private final String description;

    PaymentMethodType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

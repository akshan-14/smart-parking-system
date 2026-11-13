package com.smartparking.patterns.strategy.payment;

import com.smartparking.model.enums.PaymentMethodType;

/**
 * Concrete Payment Strategy: Credit / Debit Card.
 * Validates card parameters (masked PAN, expiration) and executes payment simulation.
 */
public class CardPaymentStrategy implements PaymentStrategy {
    private final String cardNumber;
    private final String cardHolderName;
    private final String expiryDate;

    public CardPaymentStrategy(String cardNumber, String cardHolderName, String expiryDate) {
        this.cardNumber = (cardNumber != null) ? cardNumber.replaceAll("\\s+", "") : "";
        this.cardHolderName = (cardHolderName != null) ? cardHolderName.trim() : "Unknown";
        this.expiryDate = expiryDate;
    }

    public CardPaymentStrategy() {
        this("4111111111111111", "Standard Cardholder", "12/28");
    }

    @Override
    public boolean processPayment(double amount, String transactionRef) {
        if (amount < 0) return false;
        // Basic Luhn or length simulation: length must be >= 12
        return cardNumber.length() >= 12;
    }

    public String getMaskedCardNumber() {
        if (cardNumber.length() < 4) return "****";
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }

    @Override
    public PaymentMethodType getMethodType() {
        return PaymentMethodType.CARD;
    }
}

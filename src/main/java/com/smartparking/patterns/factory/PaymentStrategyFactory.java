package com.smartparking.patterns.factory;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.patterns.strategy.payment.*;

/**
 * Factory Pattern implementation for instantiating the appropriate PaymentStrategy.
 */
public class PaymentStrategyFactory {

    public static PaymentStrategy createPaymentStrategy(PaymentMethodType methodType) {
        if (methodType == null) {
            throw new IllegalArgumentException("PaymentMethodType cannot be null.");
        }
        return switch (methodType) {
            case CASH -> new CashPaymentStrategy();
            case CARD -> new CardPaymentStrategy();
            case UPI -> new UpiPaymentStrategy();
            case WALLET -> new WalletPaymentStrategy();
            case FASTAG -> new FastagPaymentStrategy();
        };
    }
}

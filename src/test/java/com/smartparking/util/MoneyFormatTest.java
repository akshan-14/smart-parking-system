package com.smartparking.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("MoneyFormat Indian Numbering Format Tests")
class MoneyFormatTest {

    @Test
    @DisplayName("Should format Indian Rupees with correct Lakhs and Crores groupings")
    void testIndianGrouping() {
        assertEquals("₹12,34,567.50", MoneyFormat.format(1234567.50));
        assertEquals("₹1,23,456.00", MoneyFormat.format(123456.00));
        assertEquals("₹1,00,00,000.00", MoneyFormat.format(10000000.00));
        assertEquals("₹40.00", MoneyFormat.format(40.00));
        assertEquals("₹0.00", MoneyFormat.format(0.00));
        assertEquals("₹10.50", MoneyFormat.format(10.50));
    }

    @Test
    @DisplayName("Should format negative amounts cleanly with Indian grouping")
    void testNegativeAmounts() {
        assertEquals("-₹500.00", MoneyFormat.format(-500.00));
        assertEquals("-₹1,500.00", MoneyFormat.format(-1500.00));
    }
}

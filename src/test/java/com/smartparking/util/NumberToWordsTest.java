package com.smartparking.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Unit Tests for Converting Monetary Amounts into Indian English Words")
class NumberToWordsTest {

    @Test
    @DisplayName("Converts basic amounts without paise")
    void testBasicAmounts() {
        assertEquals("Rupees Zero Only", NumberToWords.convert(0));
        assertEquals("Rupees One Only", NumberToWords.convert(1));
        assertEquals("Rupees Eighteen Only", NumberToWords.convert(18));
        assertEquals("Rupees One Hundred Eighteen Only", NumberToWords.convert(118));
    }

    @Test
    @DisplayName("Converts amounts with Indian grouping (Thousands, Lakhs, Crores)")
    void testIndianGroupingAmounts() {
        assertEquals("Rupees One Thousand Five Hundred Only", NumberToWords.convert(1500));
        assertEquals("Rupees One Lakh Five Thousand Only", NumberToWords.convert(105000));
        assertEquals("Rupees Twelve Lakh Thirty Four Thousand Five Hundred Sixty Seven Only", NumberToWords.convert(1234567));
        assertEquals("Rupees One Crore Only", NumberToWords.convert(10000000));
    }

    @Test
    @DisplayName("Converts amounts with Paise fractions")
    void testAmountsWithPaise() {
        assertEquals("Rupees Ninety Four and Forty Paise Only", NumberToWords.convert(94.40));
        assertEquals("Rupees Zero and Fifty Paise Only", NumberToWords.convert(0.50));
        assertEquals("Rupees One Hundred Eighteen and Twenty Five Paise Only", NumberToWords.convert(118.25));
    }
}

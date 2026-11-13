package com.smartparking.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests for NPCI-compliant UPI Intent Link Generation")
class UpiLinkBuilderTest {

    @Test
    @DisplayName("Generate compliant upi://pay link with formatted parameters")
    void testStandardUpiIntent() {
        String uri = UpiUtil.buildPaymentUri("parking@upi", "Grand Tech Park Garage", 150.0, "Ticket TKT-UP-1001");

        assertTrue(uri.startsWith("upi://pay?"), "Must start with upi://pay?");
        assertTrue(uri.contains("pa=parking%40upi"), "Must contain encoded VPA");
        assertTrue(uri.contains("am=150.00"), "Must contain 2-decimal amount");
        assertTrue(uri.contains("cu=INR"), "Must specify Indian Rupee currency");
        assertTrue(uri.contains("tn=Ticket+TKT-UP-1001"), "Must contain encoded transaction note");
    }

    @Test
    @DisplayName("Correct encoding of special characters in business name and 2-decimal amount")
    void testSpecialCharactersInBusinessName() {
        String uri = UpiUtil.buildPaymentUri("parking@upi", "Grand Tech Park & Garage (Lucknow, UP)", 123.4, "TKT-12345");

        assertTrue(uri.startsWith("upi://pay?"));
        assertTrue(uri.contains("pa=parking%40upi"));
        assertTrue(uri.contains("pn=Grand+Tech+Park+%26+Garage+%28Lucknow%2C+UP%29") || uri.contains("pn=Grand%20Tech%20Park%20%26%20Garage%20%28Lucknow%2C%20UP%29"));
        assertTrue(uri.contains("am=123.40"), "Amount must have exactly 2 decimal places");
        assertTrue(uri.contains("cu=INR"));
        assertTrue(uri.contains("tn=TKT-12345"));
    }

    @Test
    @DisplayName("Handle edge cases such as null and empty fields gracefully")
    void testFallbackDefaults() {
        String uri = UpiUtil.buildPaymentUri(null, null, 0.0, null);

        assertTrue(uri.contains("pa=parking%40upi"));
        assertTrue(uri.contains("am=0.00"));
        assertTrue(uri.contains("cu=INR"));
    }
}

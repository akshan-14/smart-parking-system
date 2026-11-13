package com.smartparking.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Utility for generating standard Unified Payments Interface (UPI) deep links.
 * Format specified by NPCI (National Payments Corporation of India):
 * upi://pay?pa=<vpa>&pn=<name>&am=<amount>&cu=INR&tn=<note>
 */
public final class UpiUtil {

    private UpiUtil() {
        // Utility class
    }

    /**
     * Builds a compliant UPI payment intent URI.
     *
     * @param vpa Virtual Payment Address (e.g. parking@upi)
     * @param businessName Payee Merchant Name (e.g. Grand Tech Park Garage)
     * @param amount Transaction amount (formatted with 2 decimal places)
     * @param note Transaction reference or ticket number
     * @return Complete URL-encoded UPI deep link
     */
    public static String buildPaymentUri(String vpa, String businessName, double amount, String note) {
        String safeVpa = (vpa != null && !vpa.isBlank()) ? vpa.trim() : "parking@upi";
        String safeName = (businessName != null && !businessName.isBlank()) ? businessName.trim() : "Smart Parking";
        String safeNote = (note != null && !note.isBlank()) ? note.trim() : "Parking Fee";
        String formattedAmount = String.format(Locale.US, "%.2f", Math.max(0.0, amount));

        return String.format("upi://pay?pa=%s&pn=%s&am=%s&cu=INR&tn=%s",
                URLEncoder.encode(safeVpa, StandardCharsets.UTF_8),
                URLEncoder.encode(safeName, StandardCharsets.UTF_8),
                formattedAmount,
                URLEncoder.encode(safeNote, StandardCharsets.UTF_8));
    }

    public static String buildUpiIntentUrl(String vpa, String businessName, double amount, String note) {
        return buildPaymentUri(vpa, businessName, amount, note);
    }
}

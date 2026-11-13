package com.smartparking.util;

/**
 * Utility for masking personally identifiable information (PII)
 * in compliance with India's Digital Personal Data Protection (DPDP) Act, 2023.
 */
public final class MaskingUtil {

    private MaskingUtil() {
    }

    /**
     * Masks Indian mobile number (e.g. "9876501234" -> "98XXXXX234").
     */
    public static String maskMobile(String mobile) {
        if (mobile == null) return "";
        String clean = mobile.replaceAll("[^0-9]", "");
        if (clean.length() == 10) {
            return clean.substring(0, 2) + "XXXXX" + clean.substring(7);
        }
        if (clean.length() > 4) {
            return clean.substring(0, 2) + "XXXX" + clean.substring(clean.length() - 2);
        }
        return "XXXXXX";
    }

    /**
     * Masks credit/debit card numbers showing only last 4 digits.
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) return "";
        String clean = cardNumber.replaceAll("[^0-9]", "");
        if (clean.length() >= 4) {
            return "XXXX-XXXX-XXXX-" + clean.substring(clean.length() - 4);
        }
        return "XXXX-XXXX-XXXX-XXXX";
    }

    /**
     * Partially masks a license plate (e.g. "UP32AB1234" -> "UP32**1234").
     */
    public static String maskPlate(String plate) {
        if (plate == null || plate.isBlank()) return "";
        String clean = PlateUtil.normalize(plate);
        if (clean.length() >= 8) {
            return clean.substring(0, 4) + "**" + clean.substring(clean.length() - 4);
        }
        return clean;
    }
}

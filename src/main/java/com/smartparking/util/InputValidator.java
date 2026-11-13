package com.smartparking.util;

import java.util.regex.Pattern;

/**
 * Validation and sanitization utility for user inputs across the system.
 * Prevents SQL Injection, Cross-Site Scripting (XSS), and data corruption.
 */
public final class InputValidator {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^[6-9][0-9]{9}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{3,30}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    private InputValidator() {
    }

    public static void validateFullName(String name) {
        if (name == null || name.trim().length() < 2) {
            throw new IllegalArgumentException("Full name must be at least 2 characters long.");
        }
    }

    public static void validateMobile(String mobile) {
        if (mobile == null || !MOBILE_PATTERN.matcher(mobile.trim()).matches()) {
            throw new IllegalArgumentException("Invalid Indian mobile number. Must be 10 digits starting with 6-9.");
        }
    }

    public static void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("Username must be 3-30 characters alphanumeric (may include _ - .).");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must be at least 8 characters long and contain at least one letter and one number.");
        }
    }

    public static void validatePlate(String plate) {
        if (plate == null || !PlateUtil.isValid(plate)) {
            throw new IllegalArgumentException("Invalid vehicle registration number. Must follow UP or Bharat (BH) series format.");
        }
    }

    public static void validateAmount(double amount) {
        if (amount <= 0.0 || amount > 100000.0) {
            throw new IllegalArgumentException("Amount must be greater than zero and within ₹1,00,000.");
        }
    }

    public static void validateDurationHours(int hours) {
        if (hours <= 0 || hours > 8760) {
            throw new IllegalArgumentException("Duration must be between 1 and 8760 hours (1 year).");
        }
    }

    /**
     * Sanitizes strings for safe rendering in HTML components to mitigate XSS attacks.
     */
    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}

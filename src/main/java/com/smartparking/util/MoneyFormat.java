package com.smartparking.util;

import java.util.Locale;

/**
 * Utility for Indian Currency (INR - ₹) formatting.
 * Implements the standard Indian numbering system (Lakhs & Crores grouping: ##,##,##0.00).
 * Example: 1234567.5 -> ₹12,34,567.50
 */
public final class MoneyFormat {

    public static final Locale LOCALE_IN = Locale.forLanguageTag("en-IN");
    public static final String CURRENCY_SYMBOL = "₹";

    private MoneyFormat() {
        // Utility class
    }

    /**
     * Formats a monetary amount into standard Indian Rupee notation (e.g. ₹12,34,567.50).
     *
     * @param amount The double value to format
     * @return Formatted currency string with Indian numbering grouping and ₹ prefix
     */
    public static String format(double amount) {
        boolean negative = amount < 0;
        double abs = Math.abs(amount);
        long intPart = (long) abs;
        long decPart = Math.round((abs - intPart) * 100.0);
        if (decPart >= 100) {
            intPart += 1;
            decPart = 0;
        }

        String s = String.valueOf(intPart);
        StringBuilder sb = new StringBuilder();
        int len = s.length();
        if (len <= 3) {
            sb.append(s);
        } else {
            String lastThree = s.substring(len - 3);
            String rest = s.substring(0, len - 3);
            StringBuilder restGrouped = new StringBuilder();
            int count = 0;
            for (int i = rest.length() - 1; i >= 0; i--) {
                restGrouped.insert(0, rest.charAt(i));
                count++;
                if (count == 2 && i > 0) {
                    restGrouped.insert(0, ',');
                    count = 0;
                }
            }
            sb.append(restGrouped).append(',').append(lastThree);
        }
        sb.append(String.format(".%02d", decPart));
        return (negative ? "-₹" : "₹") + sb.toString();
    }

    /**
     * Overloaded helper for integer/long amounts.
     */
    public static String format(long amount) {
        return format((double) amount);
    }

    /**
     * Convenient alias for format(double).
     */
    public static String formatRupees(double amount) {
        return format(amount);
    }

    /**
     * Convenient alias for format(long).
     */
    public static String formatRupees(long amount) {
        return format((double) amount);
    }
}

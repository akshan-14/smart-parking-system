package com.smartparking.util;

/**
 * Utility for converting numerical monetary amounts into Indian English words
 * for GST Tax Invoices and official financial receipts.
 * Uses the Indian numbering system: Crores, Lakhs, Thousands, Hundreds.
 * Example: 118 -> "Rupees One Hundred Eighteen Only"
 */
public final class NumberToWords {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private NumberToWords() {
        // Utility class
    }

    /**
     * Converts an amount into Indian Currency words.
     * Example: 118.00 -> "Rupees One Hundred Eighteen Only"
     * Example: 105000.00 -> "Rupees One Lakh Five Thousand Only"
     * Example: 118.50 -> "Rupees One Hundred Eighteen and Fifty Paise Only"
     */
    public static String convert(double amount) {
        if (amount < 0.0) {
            return "Negative " + convert(-amount);
        }

        long rupees = (long) amount;
        long paise = Math.round((amount - rupees) * 100);

        if (paise == 100) {
            rupees += 1;
            paise = 0;
        }

        if (rupees == 0 && paise == 0) {
            return "Rupees Zero Only";
        }

        StringBuilder sb = new StringBuilder();
        if (rupees > 0) {
            sb.append("Rupees ").append(convertRupees(rupees).trim());
        }

        if (paise > 0) {
            if (rupees > 0) {
                sb.append(" and ");
            } else {
                sb.append("Rupees Zero and ");
            }
            sb.append(convertTwoDigits((int) paise).trim()).append(" Paise");
        }

        sb.append(" Only");
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String convertRupees(long n) {
        if (n == 0) return "";

        StringBuilder sb = new StringBuilder();

        // Crores (1,00,00,000)
        long crore = n / 10000000;
        if (crore > 0) {
            sb.append(convertRupees(crore)).append(" Crore ");
            n %= 10000000;
        }

        // Lakhs (1,00,000)
        long lakh = n / 100000;
        if (lakh > 0) {
            sb.append(convertTwoDigits((int) lakh)).append(" Lakh ");
            n %= 100000;
        }

        // Thousands (1,000)
        long thousand = n / 1000;
        if (thousand > 0) {
            sb.append(convertTwoDigits((int) thousand)).append(" Thousand ");
            n %= 1000;
        }

        // Hundreds (100)
        long hundred = n / 100;
        if (hundred > 0) {
            sb.append(ONES[(int) hundred]).append(" Hundred ");
            n %= 100;
        }

        // Remaining two digits
        if (n > 0) {
            sb.append(convertTwoDigits((int) n)).append(" ");
        }

        return sb.toString();
    }

    private static String convertTwoDigits(int n) {
        if (n < 20) {
            return ONES[n];
        }
        int ten = n / 10;
        int unit = n % 10;
        if (unit > 0) {
            return TENS[ten] + " " + ONES[unit];
        }
        return TENS[ten];
    }
}

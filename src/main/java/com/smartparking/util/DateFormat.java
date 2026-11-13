package com.smartparking.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Utility for date & time formatting standardized for India (Asia/Kolkata timezone).
 * Formats timestamps into user-friendly Indian presentation: "dd MMM yyyy, h:mm a"
 * Example: "07 Nov 2026, 3:00 PM"
 */
public final class DateFormat {

    public static final ZoneId ZONE_IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_ONLY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_ONLY_FMT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DB_SQL_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateFormat() {
        // Utility class
    }

    /**
     * Formats an Instant in Asia/Kolkata timezone to "07 Nov 2026, 3:00 PM".
     */
    public static String display(Instant instant) {
        if (instant == null) return "—";
        return DISPLAY_FMT.format(instant.atZone(ZONE_IST));
    }

    /**
     * Formats a LocalDateTime assumed in Asia/Kolkata timezone.
     */
    public static String display(LocalDateTime ldt) {
        if (ldt == null) return "—";
        return DISPLAY_FMT.format(ldt.atZone(ZONE_IST));
    }

    /**
     * Formats a LocalDateTime assumed in Asia/Kolkata timezone to standard display format.
     * Convenient alias for display(LocalDateTime).
     */
    public static String formatIST(LocalDateTime ldt) {
        return display(ldt);
    }

    /**
     * Parses ISO or SQL string and formats in standard display format.
     */
    public static String display(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return "—";
        String s = dateStr.trim();
        try {
            if (s.contains("T")) {
                LocalDateTime ldt = LocalDateTime.parse(s);
                return display(ldt);
            }
            if (s.length() == 19 && s.charAt(10) == ' ') {
                LocalDateTime ldt = LocalDateTime.parse(s, DB_SQL_FMT);
                return display(ldt);
            }
            if (s.length() == 10 && s.indexOf('-') == 4) {
                LocalDate ld = LocalDate.parse(s);
                return displayDate(ld);
            }
            Instant inst = Instant.parse(s);
            return display(inst);
        } catch (DateTimeParseException ignored) {
            return s;
        }
    }

    /**
     * Formats a LocalDate to "07 Nov 2026".
     */
    public static String displayDate(LocalDate date) {
        if (date == null) return "—";
        return DATE_ONLY_FMT.format(date);
    }

    /**
     * Formats time component to "3:00 PM".
     */
    public static String displayTime(LocalDateTime ldt) {
        if (ldt == null) return "—";
        return TIME_ONLY_FMT.format(ldt.atZone(ZONE_IST));
    }

    /**
     * Parses a database SQL string ("yyyy-MM-dd HH:mm:ss") to LocalDateTime.
     */
    public static LocalDateTime parseDbDateTime(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        s = s.trim();
        try {
            if (s.contains("T")) {
                return LocalDateTime.parse(s);
            }
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19), DB_SQL_FMT);
            }
            return LocalDateTime.parse(s, DB_SQL_FMT);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * Formats time component in IST ("h:mm a" or "HH:mm:ss").
     */
    public static String formatISTTime(LocalDateTime ldt) {
        if (ldt == null) return "—";
        return DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH).format(ldt.atZone(ZONE_IST));
    }

    /**
     * Formats LocalDateTime for standardized database persistence (Asia/Kolkata ISO SQL string).
     */
    public static String toDbString(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.format(DB_SQL_FMT);
    }
}

package com.smartparking.model.enums;

/**
 * Enumeration representing parking slot/bay types.
 * Standardized for Indian facility operations.
 */
public enum SlotType {
    COMPACT("Two-Wheeler / Compact Bay", 20.0),
    REGULAR("Standard Bay (Cars / Auto Rickshaws)", 40.0),
    LARGE("Heavy Commercial Vehicle Bay (LCV / HCV)", 80.0),
    ELECTRIC("EV Charging Bay", 60.0),
    ACCESSIBLE("Persons with Disabilities (Divyang) Accessible Bay", 30.0),
    HANDICAPPED("Persons with Disabilities (Divyang) Accessible Bay", 30.0); // Backwards compatibility

    private final String description;
    private final double defaultHourlyRate;

    SlotType(String description, double defaultHourlyRate) {
        this.description = description;
        this.defaultHourlyRate = defaultHourlyRate;
    }

    public String getDescription() {
        return description;
    }

    public double getDefaultHourlyRate() {
        return defaultHourlyRate;
    }

    public static SlotType fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return REGULAR;
        String s = raw.trim().toUpperCase();
        if ("HANDICAPPED".equals(s) || "DIVYANG".equals(s) || "PWD".equals(s) || "ACCESSIBLE".equals(s)) {
            return ACCESSIBLE;
        }
        try {
            return SlotType.valueOf(s);
        } catch (Exception e) {
            return REGULAR;
        }
    }
}

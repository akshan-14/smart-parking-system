package com.smartparking.model.enums;

/**
 * Types of penalties and their base fine amounts.
 */
public enum PenaltyType {
    LOST_TICKET("Lost Parking Ticket Penalty", 250.0),
    OVERSTAY("Overstay Beyond Permitted Duration", 100.0),
    UNAUTHORIZED_SLOT("Unauthorized Parking in Reserved/Disabled Slot", 500.0);

    private final String description;
    private final double baseFine;

    PenaltyType(String description, double baseFine) {
        this.description = description;
        this.baseFine = baseFine;
    }

    public String getDescription() {
        return description;
    }

    public double getBaseFine() {
        return baseFine;
    }
}

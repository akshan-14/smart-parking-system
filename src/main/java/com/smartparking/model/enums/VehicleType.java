package com.smartparking.model.enums;

/**
 * Enumeration representing standardized vehicle classes in the Indian parking system.
 * Supported classes:
 * - MOTORCYCLE (Two-Wheeler)
 * - AUTO_RICKSHAW (Three-Wheeler)
 * - CAR (Standard Four-Wheeler)
 * - EV_CAR (Electric Four-Wheeler)
 * - EV_TWO_WHEELER (Electric Two-Wheeler)
 * - LCV (Light Commercial Vehicle)
 * - HCV (Heavy Commercial Vehicle / Bus / Truck)
 */
public enum VehicleType {
    MOTORCYCLE("Two-Wheeler / Motorcycle"),
    AUTO_RICKSHAW("Three-Wheeler / Auto Rickshaw"),
    CAR("Four-Wheeler / Standard Car"),
    EV_CAR("Electric Four-Wheeler (EV Car)"),
    EV_TWO_WHEELER("Electric Two-Wheeler (EV Bike)"),
    LCV("Light Commercial Vehicle (LCV)"),
    HCV("Heavy Commercial Vehicle (HCV / Truck / Bus)"),

    // Legacy backward-compatibility aliases
    @Deprecated BIKE("Two-Wheeler / Motorcycle"),
    @Deprecated TRUCK("Heavy Commercial Vehicle (HCV / Truck / Bus)"),
    @Deprecated ELECTRIC_CAR("Electric Four-Wheeler (EV Car)");

    private final String displayName;

    VehicleType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Case-insensitive parser supporting legacy values (BIKE, TRUCK, ELECTRIC_CAR) and abbreviations.
     */
    public static VehicleType fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return CAR;
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "BIKE", "MOTORBIKE", "TWO_WHEELER" -> MOTORCYCLE;
            case "TRUCK", "HEAVY", "BUS" -> HCV;
            case "ELECTRIC", "ELECTRIC_CAR", "EV" -> EV_CAR;
            case "EV_BIKE", "EV_MOTORCYCLE" -> EV_TWO_WHEELER;
            case "AUTO", "RICKSHAW" -> AUTO_RICKSHAW;
            case "LIGHT_COMMERCIAL" -> LCV;
            default -> {
                try {
                    yield VehicleType.valueOf(s);
                } catch (Exception e) {
                    yield CAR;
                }
            }
        };
    }
}

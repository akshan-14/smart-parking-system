package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;

import java.util.Objects;

/**
 * Abstract base class representing a Vehicle.
 * Demonstrates:
 * - Abstraction: Abstract methods define vehicle contracts.
 * - Encapsulation: State is private and accessed via validated accessors.
 * - Constructor Chaining: Overloaded constructors route to the canonical constructor.
 */
public abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;
    private String color;
    private String brand;
    private boolean hasHandicappedPermit;

    /**
     * Canonical constructor initializing all fields including handicapped accessibility permit.
     */
    public Vehicle(String licensePlate, VehicleType type, String color, String brand, boolean hasHandicappedPermit) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Vehicle type cannot be null.");
        }
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.type = type;
        this.color = (color != null && !color.trim().isEmpty()) ? color.trim() : "Unknown";
        this.brand = (brand != null && !brand.trim().isEmpty()) ? brand.trim() : "Unknown";
        this.hasHandicappedPermit = hasHandicappedPermit;
    }

    /**
     * Overloaded constructor defaulting hasHandicappedPermit to false.
     * Demonstrates constructor chaining (this() call).
     */
    public Vehicle(String licensePlate, VehicleType type, String color, String brand) {
        this(licensePlate, type, color, brand, false);
    }

    /**
     * Overloaded constructor demonstrating constructor chaining.
     */
    public Vehicle(String licensePlate, VehicleType type) {
        this(licensePlate, type, "Unknown", "Unknown", false);
    }

    /**
     * Abstract method enforcing runtime polymorphism across subclasses.
     * Checks if this vehicle type can fit in a specific slot type.
     */
    public abstract boolean canFitIn(SlotType slotType);

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getType() {
        return type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        if (color != null && !color.trim().isEmpty()) {
            this.color = color.trim();
        }
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        if (brand != null && !brand.trim().isEmpty()) {
            this.brand = brand.trim();
        }
    }

    public boolean isHandicappedPermitted() {
        return hasHandicappedPermit;
    }

    public boolean isDivyangPermitted() {
        return hasHandicappedPermit;
    }

    public boolean hasDivyangPermit() {
        return hasHandicappedPermit;
    }

    public void setHandicappedPermit(boolean hasHandicappedPermit) {
        this.hasHandicappedPermit = hasHandicappedPermit;
    }

    public void setDivyangPermit(boolean hasDivyangPermit) {
        this.hasHandicappedPermit = hasDivyangPermit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vehicle vehicle)) return false;
        return Objects.equals(licensePlate, vehicle.licensePlate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(licensePlate);
    }

    @Override
    public String toString() {
        return String.format("%s [Plate: %s, Brand: %s, Color: %s%s]",
                type.getDisplayName(), licensePlate, brand, color,
                (hasHandicappedPermit ? ", AccessibilityPermit: YES" : ""));
    }
}

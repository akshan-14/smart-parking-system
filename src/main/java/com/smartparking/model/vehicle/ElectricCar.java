package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;

/**
 * Concrete representation of an Electric Automobile.
 * Demonstrates:
 * - Inheritance from Vehicle.
 * - Specialized fields (batteryCapacity, charge percentage).
 * - Polymorphism in fitting compatibility.
 */
public class ElectricCar extends Vehicle {
    private final double batteryCapacityKWh;
    private double currentChargePercentage;
    private boolean isChargingRequested;

    public ElectricCar(String licensePlate, String color, String brand, double batteryCapacityKWh,
                       double currentChargePercentage, boolean isChargingRequested, boolean hasHandicappedPermit) {
        super(licensePlate, VehicleType.EV_CAR, color, brand, hasHandicappedPermit);
        this.batteryCapacityKWh = Math.max(10.0, batteryCapacityKWh);
        this.currentChargePercentage = Math.min(100.0, Math.max(0.0, currentChargePercentage));
        this.isChargingRequested = isChargingRequested;
    }

    public ElectricCar(String licensePlate, String color, String brand, double batteryCapacityKWh,
                       double currentChargePercentage, boolean isChargingRequested) {
        this(licensePlate, color, brand, batteryCapacityKWh, currentChargePercentage, isChargingRequested, false);
    }

    public ElectricCar(String licensePlate, double batteryCapacityKWh) {
        this(licensePlate, "Unknown", "Unknown", batteryCapacityKWh, 50.0, true, false);
    }

    public ElectricCar(String licensePlate) {
        this(licensePlate, "Unknown", "Unknown", 60.0, 50.0, false, false);
    }

    @Override
    public boolean canFitIn(SlotType slotType) {
        if (slotType == SlotType.HANDICAPPED || slotType == SlotType.ACCESSIBLE) {
            return isDivyangPermitted();
        }
        if (isChargingRequested) {
            return slotType == SlotType.ELECTRIC;
        }
        return slotType == SlotType.ELECTRIC || slotType == SlotType.REGULAR;
    }

    public double getBatteryCapacityKWh() {
        return batteryCapacityKWh;
    }

    public double getCurrentChargePercentage() {
        return currentChargePercentage;
    }

    public void setCurrentChargePercentage(double currentChargePercentage) {
        this.currentChargePercentage = Math.min(100.0, Math.max(0.0, currentChargePercentage));
    }

    public boolean isChargingRequested() {
        return isChargingRequested;
    }

    public void setChargingRequested(boolean chargingRequested) {
        isChargingRequested = chargingRequested;
    }

    @Override
    public String toString() {
        return String.format("%s (EV) [Plate: %s, Battery: %.1f kWh, Charge: %.1f%%, NeedsCharge: %s%s]",
                getType().getDisplayName(), getLicensePlate(), batteryCapacityKWh, currentChargePercentage,
                isChargingRequested, (isHandicappedPermitted() ? ", AccessibilityPermit: YES" : ""));
    }
}

package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;

/**
 * Concrete representation of a Standard Automobile (Four-Wheeler).
 * Demonstrates Inheritance and Runtime Polymorphism.
 */
public class Car extends Vehicle {

    public Car(String licensePlate, String color, String brand, boolean hasHandicappedPermit) {
        super(licensePlate, VehicleType.CAR, color, brand, hasHandicappedPermit);
    }

    public Car(String licensePlate, String color, String brand) {
        super(licensePlate, VehicleType.CAR, color, brand);
    }

    public Car(String licensePlate, boolean hasHandicappedPermit) {
        super(licensePlate, VehicleType.CAR, "Unknown", "Unknown", hasHandicappedPermit);
    }

    public Car(String licensePlate) {
        super(licensePlate, VehicleType.CAR);
    }

    @Override
    public boolean canFitIn(SlotType slotType) {
        if (slotType == SlotType.HANDICAPPED || slotType == SlotType.ACCESSIBLE) {
            return isDivyangPermitted();
        }
        return slotType == SlotType.REGULAR;
    }
}

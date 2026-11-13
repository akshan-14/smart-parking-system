package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;

/**
 * Concrete representation of a Motorcycle (Two-Wheeler).
 */
public class Motorcycle extends Vehicle {

    public Motorcycle(String licensePlate, String color, String brand) {
        super(licensePlate, VehicleType.MOTORCYCLE, color, brand);
    }

    public Motorcycle(String licensePlate) {
        super(licensePlate, VehicleType.MOTORCYCLE);
    }

    @Override
    public boolean canFitIn(SlotType slotType) {
        // Motorcycles can fit in two-wheeler / compact slots
        return slotType == SlotType.COMPACT;
    }
}

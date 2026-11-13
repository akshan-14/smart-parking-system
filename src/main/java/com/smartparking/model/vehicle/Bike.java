package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;

/**
 * Concrete representation of a Bike (Motorcycle / Two-Wheeler).
 * Extends Motorcycle for backwards compatibility.
 */
public class Bike extends Motorcycle {

    public Bike(String licensePlate, String color, String brand) {
        super(licensePlate, color, brand);
    }

    public Bike(String licensePlate) {
        super(licensePlate);
    }

    @Override
    public boolean canFitIn(SlotType slotType) {
        return slotType == SlotType.COMPACT;
    }
}

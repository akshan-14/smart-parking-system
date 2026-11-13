package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;

/**
 * Concrete representation of a Heavy Commercial Vehicle (HCV - Truck / Bus).
 */
public class Hcv extends Vehicle {

    public Hcv(String licensePlate, String color, String brand) {
        super(licensePlate, VehicleType.HCV, color, brand);
    }

    public Hcv(String licensePlate) {
        super(licensePlate, VehicleType.HCV);
    }

    @Override
    public boolean canFitIn(SlotType slotType) {
        // Heavy trucks/buses require large bays
        return slotType == SlotType.LARGE;
    }
}

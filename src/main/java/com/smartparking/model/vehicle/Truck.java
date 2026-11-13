package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;

/**
 * Concrete representation of a Heavy Truck.
 * Extends Hcv for backwards compatibility.
 */
public class Truck extends Hcv {

    public Truck(String licensePlate, String color, String brand) {
        super(licensePlate, color, brand);
    }

    public Truck(String licensePlate) {
        super(licensePlate);
    }

    @Override
    public boolean canFitIn(SlotType slotType) {
        return slotType == SlotType.LARGE;
    }
}

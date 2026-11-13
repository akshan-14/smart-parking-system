package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;

/**
 * Concrete representation of a Light Commercial Vehicle (LCV).
 */
public class Lcv extends Vehicle {

    public Lcv(String licensePlate, String color, String brand) {
        super(licensePlate, VehicleType.LCV, color, brand);
    }

    public Lcv(String licensePlate) {
        super(licensePlate, VehicleType.LCV);
    }

    @Override
    public boolean canFitIn(SlotType slotType) {
        // Commercial vehicles require large heavy-duty bays
        return slotType == SlotType.LARGE;
    }
}

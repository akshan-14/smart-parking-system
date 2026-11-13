package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;

/**
 * Concrete representation of an Auto Rickshaw (Three-Wheeler).
 */
public class AutoRickshaw extends Vehicle {

    public AutoRickshaw(String licensePlate, String color, String brand) {
        super(licensePlate, VehicleType.AUTO_RICKSHAW, color, brand);
    }

    public AutoRickshaw(String licensePlate) {
        super(licensePlate, VehicleType.AUTO_RICKSHAW);
    }

    @Override
    public boolean canFitIn(SlotType slotType) {
        return slotType == SlotType.REGULAR;
    }
}

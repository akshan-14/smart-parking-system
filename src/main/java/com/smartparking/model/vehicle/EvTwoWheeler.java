package com.smartparking.model.vehicle;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;

/**
 * Concrete representation of an Electric Two-Wheeler (EV Bike / Scooter).
 */
public class EvTwoWheeler extends Vehicle {
    private final boolean chargingRequested;

    public EvTwoWheeler(String licensePlate, String color, String brand, boolean chargingRequested) {
        super(licensePlate, VehicleType.EV_TWO_WHEELER, color, brand);
        this.chargingRequested = chargingRequested;
    }

    public EvTwoWheeler(String licensePlate, boolean chargingRequested) {
        super(licensePlate, VehicleType.EV_TWO_WHEELER);
        this.chargingRequested = chargingRequested;
    }

    public EvTwoWheeler(String licensePlate) {
        this(licensePlate, false);
    }

    public boolean isChargingRequested() {
        return chargingRequested;
    }

    @Override
    public boolean canFitIn(SlotType slotType) {
        return slotType == SlotType.COMPACT || (chargingRequested && slotType == SlotType.ELECTRIC);
    }
}

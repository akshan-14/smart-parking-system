package com.smartparking.model.slot;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.vehicle.Vehicle;

/**
 * Concrete ParkingSlot: Compact / Two-Wheeler Slot.
 * Accommodates Motorcycles and EV Two-Wheelers.
 * Demonstrates Inheritance and Runtime Polymorphism.
 */
public class CompactSlot extends ParkingSlot {

    public CompactSlot(String slotId, int floorNumber, String slotNumber, double hourlyRate) {
        super(slotId, floorNumber, slotNumber, SlotType.COMPACT, hourlyRate);
    }

    public CompactSlot(String slotId, int floorNumber, String slotNumber) {
        super(slotId, floorNumber, slotNumber, SlotType.COMPACT);
    }

    @Override
    public boolean canAccommodate(Vehicle vehicle) {
        if (vehicle == null) return false;
        // Accessible permits route to AccessibleSlot
        if (vehicle.isDivyangPermitted()) return false;
        return vehicle.getType() == VehicleType.MOTORCYCLE || vehicle.getType() == VehicleType.EV_TWO_WHEELER;
    }
}

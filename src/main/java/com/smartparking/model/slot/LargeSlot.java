package com.smartparking.model.slot;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.vehicle.Vehicle;

/**
 * Concrete ParkingSlot: Large Bay (Heavy Floor / Commercial).
 * Specifically accommodates LCV (Light Commercial) and HCV (Heavy Commercial / Trucks / Buses).
 * Demonstrates Inheritance and Runtime Polymorphism.
 */
public class LargeSlot extends ParkingSlot {

    public LargeSlot(String slotId, int floorNumber, String slotNumber, double hourlyRate) {
        super(slotId, floorNumber, slotNumber, SlotType.LARGE, hourlyRate);
    }

    public LargeSlot(String slotId, int floorNumber, String slotNumber) {
        super(slotId, floorNumber, slotNumber, SlotType.LARGE);
    }

    @Override
    public boolean canAccommodate(Vehicle vehicle) {
        if (vehicle == null) return false;
        if (vehicle.isDivyangPermitted()) return false;
        return vehicle.getType() == VehicleType.LCV || vehicle.getType() == VehicleType.HCV;
    }
}

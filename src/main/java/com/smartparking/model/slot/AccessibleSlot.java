package com.smartparking.model.slot;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.vehicle.Vehicle;

/**
 * Concrete ParkingSlot: Persons with Disabilities (Divyang) Accessible Slot.
 * Located closest to elevators and main entrances.
 * Demonstrates Inheritance and Runtime Polymorphism.
 */
public class AccessibleSlot extends ParkingSlot {

    public AccessibleSlot(String slotId, int floorNumber, String slotNumber, double hourlyRate) {
        super(slotId, floorNumber, slotNumber, SlotType.ACCESSIBLE, hourlyRate);
    }

    public AccessibleSlot(String slotId, int floorNumber, String slotNumber) {
        super(slotId, floorNumber, slotNumber, SlotType.ACCESSIBLE);
    }

    @Override
    public boolean canAccommodate(Vehicle vehicle) {
        if (vehicle == null) return false;
        return vehicle.isDivyangPermitted() || vehicle.isHandicappedPermitted();
    }
}

package com.smartparking.model.slot;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.vehicle.Vehicle;

/**
 * Concrete ParkingSlot: Regular / Standard Bay.
 * Accommodates Standard Cars and Auto Rickshaws.
 * Demonstrates Inheritance and Runtime Polymorphism.
 */
public class RegularSlot extends ParkingSlot {

    public RegularSlot(String slotId, int floorNumber, String slotNumber, double hourlyRate) {
        super(slotId, floorNumber, slotNumber, SlotType.REGULAR, hourlyRate);
    }

    public RegularSlot(String slotId, int floorNumber, String slotNumber) {
        super(slotId, floorNumber, slotNumber, SlotType.REGULAR);
    }

    @Override
    public boolean canAccommodate(Vehicle vehicle) {
        if (vehicle == null) return false;
        // PwD permit holders route to AccessibleSlot
        if (vehicle.isDivyangPermitted()) return false;
        return vehicle.getType() == VehicleType.CAR || vehicle.getType() == VehicleType.AUTO_RICKSHAW;
    }
}

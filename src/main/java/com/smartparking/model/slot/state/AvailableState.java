package com.smartparking.model.slot.state;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

/**
 * Concrete State: The slot is vacant and ready for parking or reservation.
 */
public class AvailableState implements SlotState {

    @Override
    public void occupy(ParkingSlot slot, Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Cannot occupy a slot with a null vehicle.");
        }
        if (!slot.canAccommodate(vehicle)) {
            throw new IllegalArgumentException(String.format("Slot %s of type %s cannot accommodate vehicle %s of type %s.",
                    slot.getSlotNumber(), slot.getSlotType(), vehicle.getLicensePlate(), vehicle.getType()));
        }
        slot.setCurrentVehicle(vehicle);
        slot.setCurrentState(new OccupiedState());
    }

    @Override
    public void vacate(ParkingSlot slot) {
        throw new IllegalStateException("Slot " + slot.getSlotNumber() + " is already vacant.");
    }

    @Override
    public void reserve(ParkingSlot slot) {
        slot.setCurrentState(new ReservedState());
    }

    @Override
    public void markOutOfService(ParkingSlot slot) {
        slot.setCurrentState(new OutOfServiceState());
    }

    @Override
    public String getStatusName() {
        return "AVAILABLE";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String toString() {
        return "AvailableState";
    }
}

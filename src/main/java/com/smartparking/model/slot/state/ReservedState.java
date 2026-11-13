package com.smartparking.model.slot.state;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

/**
 * Concrete State: The slot has been reserved in advance.
 */
public class ReservedState implements SlotState {

    @Override
    public void occupy(ParkingSlot slot, Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Cannot occupy a slot with a null vehicle.");
        }
        if (!slot.canAccommodate(vehicle)) {
            throw new IllegalArgumentException(String.format("Slot %s of type %s cannot accommodate reserved vehicle %s.",
                    slot.getSlotNumber(), slot.getSlotType(), vehicle.getLicensePlate()));
        }
        slot.setCurrentVehicle(vehicle);
        slot.setCurrentState(new OccupiedState());
    }

    @Override
    public void vacate(ParkingSlot slot) {
        // Cancels or releases reservation
        slot.setCurrentVehicle(null);
        slot.setCurrentState(new AvailableState());
    }

    @Override
    public void reserve(ParkingSlot slot) {
        throw new IllegalStateException("Slot " + slot.getSlotNumber() + " is already reserved.");
    }

    @Override
    public void markOutOfService(ParkingSlot slot) {
        slot.setCurrentState(new OutOfServiceState());
    }

    @Override
    public String getStatusName() {
        return "RESERVED";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String toString() {
        return "ReservedState";
    }
}

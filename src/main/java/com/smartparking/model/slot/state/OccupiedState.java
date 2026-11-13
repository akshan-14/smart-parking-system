package com.smartparking.model.slot.state;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

/**
 * Concrete State: The slot is actively occupied by a vehicle.
 */
public class OccupiedState implements SlotState {

    @Override
    public void occupy(ParkingSlot slot, Vehicle vehicle) {
        throw new IllegalStateException(String.format("Slot %s is already occupied by vehicle %s.",
                slot.getSlotNumber(), (slot.getCurrentVehicle() != null ? slot.getCurrentVehicle().getLicensePlate() : "Unknown")));
    }

    @Override
    public void vacate(ParkingSlot slot) {
        slot.setCurrentVehicle(null);
        slot.setCurrentState(new AvailableState());
    }

    @Override
    public void reserve(ParkingSlot slot) {
        throw new IllegalStateException("Cannot reserve an occupied slot.");
    }

    @Override
    public void markOutOfService(ParkingSlot slot) {
        throw new IllegalStateException("Cannot mark an occupied slot out of service until vehicle departs.");
    }

    @Override
    public String getStatusName() {
        return "OCCUPIED";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String toString() {
        return "OccupiedState";
    }
}

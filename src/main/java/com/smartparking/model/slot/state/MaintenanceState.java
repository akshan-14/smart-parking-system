package com.smartparking.model.slot.state;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

/**
 * Concrete State: The slot is closed or under maintenance.
 * State Pattern implementation encapsulating maintenance bay lifecycle rules.
 */
public class MaintenanceState implements SlotState {

    @Override
    public void occupy(ParkingSlot slot, Vehicle vehicle) {
        throw new IllegalStateException("Cannot park in slot " + slot.getSlotNumber() + " - slot is offline for maintenance.");
    }

    @Override
    public void vacate(ParkingSlot slot) {
        // Restores slot to available
        slot.setCurrentVehicle(null);
        slot.setCurrentState(new AvailableState());
    }

    @Override
    public void reserve(ParkingSlot slot) {
        throw new IllegalStateException("Cannot reserve slot " + slot.getSlotNumber() + " - slot is offline for maintenance.");
    }

    @Override
    public void markOutOfService(ParkingSlot slot) {
        // Already in maintenance
    }

    @Override
    public String getStatusName() {
        return "MAINTENANCE";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String toString() {
        return "MaintenanceState";
    }
}

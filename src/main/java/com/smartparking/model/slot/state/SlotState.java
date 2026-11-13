package com.smartparking.model.slot.state;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

/**
 * State Pattern interface for ParkingSlot lifecycle.
 * Defines allowable state transitions and encapsulates state-specific behaviors.
 */
public interface SlotState {

    /**
     * Attempts to occupy the slot with a vehicle.
     * @param slot The context slot
     * @param vehicle The vehicle parking in the slot
     */
    void occupy(ParkingSlot slot, Vehicle vehicle);

    /**
     * Attempts to vacate the slot.
     * @param slot The context slot
     */
    void vacate(ParkingSlot slot);

    /**
     * Attempts to reserve the slot.
     * @param slot The context slot
     */
    void reserve(ParkingSlot slot);

    /**
     * Marks the slot out of service for maintenance.
     * @param slot The context slot
     */
    void markOutOfService(ParkingSlot slot);

    /**
     * Gets the human-readable status name (e.g. AVAILABLE, OCCUPIED, etc.)
     */
    String getStatusName();

    /**
     * Returns true if the slot is currently available for allocation.
     */
    boolean isAvailable();
}

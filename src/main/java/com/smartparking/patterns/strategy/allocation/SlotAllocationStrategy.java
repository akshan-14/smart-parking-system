package com.smartparking.patterns.strategy.allocation;

import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

import java.util.List;
import java.util.Optional;

/**
 * Strategy Pattern interface for automatic parking slot allocation algorithms.
 * Allows switching slot assignment behaviors dynamically at runtime.
 */
public interface SlotAllocationStrategy {

    /**
     * Finds and returns the best candidate slot across all floors for the given vehicle.
     * @param floors List of parking floors
     * @param vehicle The incoming vehicle seeking parking
     * @return An Optional containing the chosen ParkingSlot, or empty if lot is full
     */
    Optional<ParkingSlot> allocateSlot(List<ParkingFloor> floors, Vehicle vehicle);
}

package com.smartparking.patterns.strategy.allocation;

import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Concrete Strategy: Nearest-First Allocation.
 * Allocates the slot on the lowest available floor with the lowest slot number (closest to entry gate).
 */
public class NearestFirstAllocationStrategy implements SlotAllocationStrategy {

    @Override
    public Optional<ParkingSlot> allocateSlot(List<ParkingFloor> floors, Vehicle vehicle) {
        if (floors == null || vehicle == null) {
            return Optional.empty();
        }

        // Sort floors by floor number ascending
        return floors.stream()
                .sorted(Comparator.comparingInt(ParkingFloor::getFloorNumber))
                .flatMap(floor -> floor.getAllSlots().stream())
                .filter(ParkingSlot::isAvailable)
                .filter(slot -> slot.canAccommodate(vehicle))
                .findFirst();
    }
}

package com.smartparking.patterns.strategy.allocation;

import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Concrete Strategy: Random Slot Allocation.
 * Randomly distributes incoming vehicles across all available compatible slots.
 * Useful for load distribution across parking levels.
 */
public class RandomAllocationStrategy implements SlotAllocationStrategy {

    @Override
    public Optional<ParkingSlot> allocateSlot(List<ParkingFloor> floors, Vehicle vehicle) {
        if (floors == null || vehicle == null) {
            return Optional.empty();
        }

        List<ParkingSlot> compatibleAvailableSlots = floors.stream()
                .flatMap(floor -> floor.getAllSlots().stream())
                .filter(ParkingSlot::isAvailable)
                .filter(slot -> slot.canAccommodate(vehicle))
                .collect(Collectors.toList());

        if (compatibleAvailableSlots.isEmpty()) {
            return Optional.empty();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(compatibleAvailableSlots.size());
        return Optional.of(compatibleAvailableSlots.get(randomIndex));
    }
}

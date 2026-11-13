package com.smartparking.patterns.strategy.allocation;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Concrete Strategy: Best-Fit Allocation.
 * Prioritizes the most space-efficient slot type to conserve larger and specialized slots
 * (e.g. allocating compact slots for bikes rather than taking up regular car slots).
 */
public class BestFitAllocationStrategy implements SlotAllocationStrategy {

    @Override
    public Optional<ParkingSlot> allocateSlot(List<ParkingFloor> floors, Vehicle vehicle) {
        if (floors == null || vehicle == null) {
            return Optional.empty();
        }

        SlotType preferredType = getPreferredSlotType(vehicle.getType());

        // First pass: look for exact preferred slot type
        Optional<ParkingSlot> idealSlot = floors.stream()
                .sorted(Comparator.comparingInt(ParkingFloor::getFloorNumber))
                .flatMap(floor -> floor.getAllSlots().stream())
                .filter(ParkingSlot::isAvailable)
                .filter(slot -> slot.getSlotType() == preferredType)
                .filter(slot -> slot.canAccommodate(vehicle))
                .findFirst();

        if (idealSlot.isPresent()) {
            return idealSlot;
        }

        // Fallback pass: any compatible slot
        return floors.stream()
                .sorted(Comparator.comparingInt(ParkingFloor::getFloorNumber))
                .flatMap(floor -> floor.getAllSlots().stream())
                .filter(ParkingSlot::isAvailable)
                .filter(slot -> slot.canAccommodate(vehicle))
                .findFirst();
    }

    private SlotType getPreferredSlotType(VehicleType vehicleType) {
        if (vehicleType == null) return SlotType.REGULAR;
        return switch (vehicleType) {
            case MOTORCYCLE, EV_TWO_WHEELER, BIKE -> SlotType.COMPACT;
            case AUTO_RICKSHAW, CAR -> SlotType.REGULAR;
            case LCV, HCV, TRUCK -> SlotType.LARGE;
            case EV_CAR, ELECTRIC_CAR -> SlotType.ELECTRIC;
        };
    }
}

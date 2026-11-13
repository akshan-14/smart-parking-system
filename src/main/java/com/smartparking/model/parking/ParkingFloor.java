package com.smartparking.model.parking;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Vehicle;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a single floor in a multi-level parking facility.
 * Demonstrates:
 * - Composition: A ParkingFloor owns and manages its ParkingSlots.
 * - Encapsulation: Protects internal slot collection via unmodifiable views.
 */
public class ParkingFloor {
    private final int floorNumber;
    private final String floorName;
    private final Map<String, ParkingSlot> slots; // slotId -> ParkingSlot

    public ParkingFloor(int floorNumber, String floorName) {
        this.floorNumber = floorNumber;
        this.floorName = (floorName != null && !floorName.trim().isEmpty())
                ? floorName.trim()
                : "Floor " + floorNumber;
        this.slots = new LinkedHashMap<>();
    }

    public ParkingFloor(int floorNumber) {
        this(floorNumber, "Floor " + floorNumber);
    }

    /**
     * Adds a parking slot to this floor (Composition).
     */
    public void addSlot(ParkingSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Parking slot cannot be null.");
        }
        slots.put(slot.getSlotId(), slot);
    }

    /**
     * Retrieves an unmodifiable collection of all slots on this floor.
     */
    public List<ParkingSlot> getAllSlots() {
        return Collections.unmodifiableList(new ArrayList<>(slots.values()));
    }

    /**
     * Alias for getAllSlots() to access floor slots.
     */
    public List<ParkingSlot> getSlots() {
        return getAllSlots();
    }

    /**
     * Finds a slot by its ID.
     */
    public Optional<ParkingSlot> getSlotById(String slotId) {
        return Optional.ofNullable(slots.get(slotId));
    }

    /**
     * Returns all currently available slots on this floor.
     */
    public List<ParkingSlot> getAvailableSlots() {
        return slots.values().stream()
                .filter(ParkingSlot::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Returns all available slots capable of accommodating the specified vehicle.
     */
    public List<ParkingSlot> getAvailableSlotsForVehicle(Vehicle vehicle) {
        if (vehicle == null) return Collections.emptyList();
        return slots.values().stream()
                .filter(ParkingSlot::isAvailable)
                .filter(slot -> slot.canAccommodate(vehicle))
                .collect(Collectors.toList());
    }

    /**
     * Returns slots filtered by slot type.
     */
    public List<ParkingSlot> getSlotsByType(SlotType type) {
        return slots.values().stream()
                .filter(slot -> slot.getSlotType() == type)
                .collect(Collectors.toList());
    }

    public int getTotalSlotsCount() {
        return slots.size();
    }

    public int getAvailableSlotsCount() {
        return (int) slots.values().stream().filter(ParkingSlot::isAvailable).count();
    }

    public int getOccupiedSlotsCount() {
        return (int) slots.values().stream().filter(s -> "OCCUPIED".equalsIgnoreCase(s.getStatus())).count();
    }

    public int getReservedSlotsCount() {
        return (int) slots.values().stream().filter(s -> "RESERVED".equalsIgnoreCase(s.getStatus())).count();
    }

    public int getMaintenanceSlotsCount() {
        return (int) slots.values().stream().filter(s -> "MAINTENANCE".equalsIgnoreCase(s.getStatus()) || "OUT_OF_SERVICE".equalsIgnoreCase(s.getStatus())).count();
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public String getFloorName() {
        return floorName;
    }

    @Override
    public String toString() {
        return String.format("Floor %d ('%s') [Available: %d / %d]",
                floorNumber, floorName, getAvailableSlotsCount(), getTotalSlotsCount());
    }
}

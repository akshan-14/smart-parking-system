package com.smartparking.model.parking;

import com.smartparking.model.user.Attendant;

import java.util.*;

/**
 * Top-level entity representing a multi-floor Parking Facility.
 * Demonstrates:
 * - Composition: Owns its ParkingFloors (if lot ceases, floors cease).
 * - Aggregation: Maintains an active team of Attendants (attendants exist independently).
 * - Encapsulation: Protects internal floor and staff collections.
 */
public class ParkingLot {
    private final String lotId;
    private final String name;
    private final String address;
    private final List<ParkingFloor> floors;       // Composition
    private final List<Attendant> attendants;     // Aggregation

    public ParkingLot(String lotId, String name, String address) {
        if (lotId == null || lotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Lot ID cannot be null or empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Lot Name cannot be null or empty.");
        }
        this.lotId = lotId.trim();
        this.name = name.trim();
        this.address = (address != null) ? address.trim() : "Default Address";
        this.floors = new ArrayList<>();
        this.attendants = new ArrayList<>();
    }

    public ParkingLot(String lotId, String name) {
        this(lotId, name, "Default Address");
    }

    /**
     * Adds a floor to this parking facility (Composition).
     */
    public void addFloor(ParkingFloor floor) {
        if (floor == null) {
            throw new IllegalArgumentException("ParkingFloor cannot be null.");
        }
        this.floors.add(floor);
    }

    public List<ParkingFloor> getFloors() {
        return Collections.unmodifiableList(floors);
    }

    public Optional<ParkingFloor> getFloorByNumber(int floorNumber) {
        return floors.stream()
                .filter(f -> f.getFloorNumber() == floorNumber)
                .findFirst();
    }

    /**
     * Assigns an attendant to work at this parking facility (Aggregation).
     */
    public void assignAttendant(Attendant attendant) {
        if (attendant != null && !attendants.contains(attendant)) {
            attendants.add(attendant);
        }
    }

    public void removeAttendant(Attendant attendant) {
        attendants.remove(attendant);
    }

    public List<Attendant> getAttendants() {
        return Collections.unmodifiableList(attendants);
    }

    public int getTotalCapacity() {
        return floors.stream().mapToInt(ParkingFloor::getTotalSlotsCount).sum();
    }

    public int getAvailableCapacity() {
        return floors.stream().mapToInt(ParkingFloor::getAvailableSlotsCount).sum();
    }

    public int getOccupiedCapacity() {
        return floors.stream().mapToInt(ParkingFloor::getOccupiedSlotsCount).sum();
    }

    public int getReservedCapacity() {
        return floors.stream().mapToInt(ParkingFloor::getReservedSlotsCount).sum();
    }

    public int getMaintenanceCapacity() {
        return floors.stream().mapToInt(ParkingFloor::getMaintenanceSlotsCount).sum();
    }

    /**
     * Computes the current occupancy rate in percentage (0.0 to 100.0).
     */
    public double getOccupancyPercentage() {
        int total = getTotalCapacity();
        if (total == 0) return 0.0;
        return ((double) getOccupiedCapacity() / total) * 100.0;
    }

    public String getLotId() {
        return lotId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return String.format("ParkingLot['%s' (%s) | Floors: %d, Occupancy: %.1f%% (%d/%d slots)]",
                name, lotId, floors.size(), getOccupancyPercentage(), getOccupiedCapacity(), getTotalCapacity());
    }
}

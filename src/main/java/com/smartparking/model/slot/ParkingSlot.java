package com.smartparking.model.slot;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.slot.state.AvailableState;
import com.smartparking.model.slot.state.SlotState;
import com.smartparking.model.vehicle.Vehicle;

import java.util.Objects;

/**
 * Abstract base class representing a physical Parking Slot.
 * Demonstrates:
 * - Abstraction: Abstract methods define suitability rules.
 * - Encapsulation: Internal state is protected and mutated via State Pattern.
 * - State Pattern: Delegates state-dependent operations to SlotState.
 * - Constructor Chaining: Overloaded constructors cascade to full constructor.
 */
public abstract class ParkingSlot {
    private final String slotId;
    private final int floorNumber;
    private final String slotNumber;
    private final SlotType slotType;
    private double hourlyRate;
    private SlotState currentState;
    private Vehicle currentVehicle;

    /**
     * Canonical constructor.
     */
    public ParkingSlot(String slotId, int floorNumber, String slotNumber, SlotType slotType, double hourlyRate) {
        if (slotId == null || slotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Slot ID cannot be null or empty.");
        }
        if (slotNumber == null || slotNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Slot Number cannot be null or empty.");
        }
        if (slotType == null) {
            throw new IllegalArgumentException("Slot type cannot be null.");
        }
        this.slotId = slotId.trim();
        this.floorNumber = floorNumber;
        this.slotNumber = slotNumber.trim();
        this.slotType = slotType;
        this.hourlyRate = Math.max(0.0, hourlyRate);
        this.currentState = new AvailableState();
        this.currentVehicle = null;
    }

    /**
     * Overloaded constructor using the default hourly rate of the SlotType.
     * Demonstrates constructor chaining (this() call).
     */
    public ParkingSlot(String slotId, int floorNumber, String slotNumber, SlotType slotType) {
        this(slotId, floorNumber, slotNumber, slotType, slotType.getDefaultHourlyRate());
    }

    /**
     * Abstract method enforcing runtime polymorphism.
     * Subclasses define whether a given vehicle can be accommodated.
     */
    public abstract boolean canAccommodate(Vehicle vehicle);

    /**
     * Checks if this slot can fit a given VehicleType.
     */
    public boolean canFitVehicle(com.smartparking.model.enums.VehicleType type) {
        if (type == null) return false;
        if (slotType == null) return true;
        return switch (slotType) {
            case COMPACT -> type == com.smartparking.model.enums.VehicleType.MOTORCYCLE || type == com.smartparking.model.enums.VehicleType.EV_TWO_WHEELER || type == com.smartparking.model.enums.VehicleType.BIKE;
            case REGULAR -> type == com.smartparking.model.enums.VehicleType.CAR || type == com.smartparking.model.enums.VehicleType.AUTO_RICKSHAW || type == com.smartparking.model.enums.VehicleType.EV_CAR || type == com.smartparking.model.enums.VehicleType.ELECTRIC_CAR;
            case LARGE -> type == com.smartparking.model.enums.VehicleType.LCV || type == com.smartparking.model.enums.VehicleType.HCV || type == com.smartparking.model.enums.VehicleType.TRUCK;
            case ELECTRIC -> type == com.smartparking.model.enums.VehicleType.EV_CAR || type == com.smartparking.model.enums.VehicleType.EV_TWO_WHEELER || type == com.smartparking.model.enums.VehicleType.ELECTRIC_CAR;
            case HANDICAPPED, ACCESSIBLE -> true;
            default -> true;
        };
    }

    // --- State Pattern Delegations ---

    public void occupy(Vehicle vehicle) {
        currentState.occupy(this, vehicle);
    }

    public void vacate() {
        currentState.vacate(this);
    }

    public void reserve() {
        currentState.reserve(this);
    }

    public void markOutOfService() {
        currentState.markOutOfService(this);
    }

    public boolean isAvailable() {
        return currentState.isAvailable();
    }

    public String getStatus() {
        return currentState.getStatusName();
    }

    // --- Getters & Mutators ---

    public String getSlotId() {
        return slotId;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public String getSlotNumber() {
        return slotNumber;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public double getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(double hourlyRate) {
        if (hourlyRate >= 0.0) {
            this.hourlyRate = hourlyRate;
        }
    }

    public SlotState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(SlotState currentState) {
        if (currentState == null) {
            throw new IllegalArgumentException("Slot state cannot be null.");
        }
        this.currentState = currentState;
    }

    public Vehicle getCurrentVehicle() {
        return currentVehicle;
    }

    public String getCurrentVehiclePlate() {
        return currentVehicle != null ? currentVehicle.getLicensePlate() : null;
    }

    public void setCurrentVehicle(Vehicle currentVehicle) {
        this.currentVehicle = currentVehicle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParkingSlot that)) return false;
        return Objects.equals(slotId, that.slotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotId);
    }

    @Override
    public String toString() {
        return String.format("Slot[%s | Floor %d, #%s, Type: %s, State: %s, Rate: \u20B9%.2f/hr, Vehicle: %s]",
                slotId, floorNumber, slotNumber, slotType, currentState.getStatusName(), hourlyRate,
                (currentVehicle != null ? currentVehicle.getLicensePlate() : "None"));
    }
}

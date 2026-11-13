package com.smartparking.model.ticket;

import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.VehicleType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Core entity representing a Parking Ticket issued upon vehicle entry.
 * Demonstrates:
 * - Composition: Maintains penalties incurred on this ticket.
 * - Encapsulation: Invariants on financial calculations and timestamp progression.
 * - Constructor Chaining.
 */
public class ParkingTicket {
    private final int ticketId;
    private final String ticketNumber;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final String slotId;
    private final int floorNumber;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private TicketStatus status;
    private double baseFee;
    private double penaltyFee;
    private double evChargingFee;
    private double totalAmount;
    private final List<Penalty> penalties; // Composition

    /**
     * Canonical constructor.
     */
    public ParkingTicket(int ticketId, String ticketNumber, String licensePlate, VehicleType vehicleType,
                         String slotId, int floorNumber, LocalDateTime entryTime, LocalDateTime exitTime,
                         TicketStatus status, double baseFee, double penaltyFee, double evChargingFee,
                         double totalAmount, List<Penalty> penalties) {
        if (ticketNumber == null || ticketNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticket number cannot be null or empty.");
        }
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty.");
        }
        if (vehicleType == null) {
            throw new IllegalArgumentException("Vehicle type cannot be null.");
        }
        if (slotId == null || slotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Slot ID cannot be null or empty.");
        }
        this.ticketId = ticketId;
        this.ticketNumber = ticketNumber.trim();
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.vehicleType = vehicleType;
        this.slotId = slotId.trim();
        this.floorNumber = floorNumber;
        this.entryTime = (entryTime != null) ? entryTime : LocalDateTime.now();
        this.exitTime = exitTime;
        this.status = (status != null) ? status : TicketStatus.ACTIVE;
        this.baseFee = Math.max(0.0, baseFee);
        this.penaltyFee = Math.max(0.0, penaltyFee);
        this.evChargingFee = Math.max(0.0, evChargingFee);
        this.totalAmount = Math.max(0.0, totalAmount);
        this.penalties = (penalties != null) ? new ArrayList<>(penalties) : new ArrayList<>();
    }

    /**
     * Convenience constructor without floorNumber and penalties list.
     */
    public ParkingTicket(int ticketId, String ticketNumber, String licensePlate, VehicleType vehicleType,
                         String slotId, LocalDateTime entryTime, LocalDateTime exitTime,
                         TicketStatus status, double baseFee, double penaltyFee, double evChargingFee,
                         double totalAmount) {
        this(ticketId, ticketNumber, licensePlate, vehicleType, slotId, 1, entryTime, exitTime, status, baseFee, penaltyFee, evChargingFee, totalAmount, new ArrayList<>());
    }

    /**
     * Constructor used when issuing a new ticket upon entry.
     * Demonstrates constructor chaining.
     */
    public ParkingTicket(String licensePlate, VehicleType vehicleType, String slotId, int floorNumber) {
        this(0, "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                licensePlate, vehicleType, slotId, floorNumber,
                LocalDateTime.now(), null, TicketStatus.ACTIVE,
                0.0, 0.0, 0.0, 0.0, new ArrayList<>());
    }

    /**
     * Overloaded constructor with custom entry time (useful for simulation & testing).
     */
    public ParkingTicket(String licensePlate, VehicleType vehicleType, String slotId, int floorNumber, LocalDateTime entryTime) {
        this(0, "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                licensePlate, vehicleType, slotId, floorNumber,
                entryTime, null, TicketStatus.ACTIVE,
                0.0, 0.0, 0.0, 0.0, new ArrayList<>());
    }

    /**
     * Calculates duration in minutes from entry to exit (or now if active).
     */
    public long getDurationInMinutes() {
        LocalDateTime end = (exitTime != null) ? exitTime : LocalDateTime.now();
        return Math.max(0, Duration.between(entryTime, end).toMinutes());
    }

    /**
     * Calculates duration rounded up to the nearest whole hour (standard parking billing).
     */
    public long getDurationInHours() {
        long minutes = getDurationInMinutes();
        if (minutes == 0) return 0;
        return (long) Math.ceil(minutes / 60.0);
    }

    /**
     * Adds an assessed penalty to this ticket and recalculates total.
     */
    public void addPenalty(Penalty penalty) {
        if (penalty != null) {
            penalties.add(penalty);
            this.penaltyFee += penalty.getAmount();
            recalculateTotal();
        }
    }

    /**
     * Recomputes total financial obligation.
     */
    public void recalculateTotal() {
        this.totalAmount = this.baseFee + this.penaltyFee + this.evChargingFee;
    }

    public void markPaid() {
        this.status = TicketStatus.PAID;
    }

    public void markExited() {
        this.exitTime = LocalDateTime.now();
        this.status = TicketStatus.EXITED;
    }

    public void markLost() {
        this.status = TicketStatus.LOST;
    }

    // --- Accessors & Mutators ---

    public int getTicketId() {
        return ticketId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public String getSlotId() {
        return slotId;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public double getBaseFee() {
        return baseFee;
    }

    public void setBaseFee(double baseFee) {
        this.baseFee = Math.max(0.0, baseFee);
        recalculateTotal();
    }

    public double getPenaltyFee() {
        return penaltyFee;
    }

    public void setPenaltyFee(double penaltyFee) {
        this.penaltyFee = Math.max(0.0, penaltyFee);
        recalculateTotal();
    }

    public double getEvChargingFee() {
        return evChargingFee;
    }

    public void setEvChargingFee(double evChargingFee) {
        this.evChargingFee = Math.max(0.0, evChargingFee);
        recalculateTotal();
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = Math.max(0.0, totalAmount);
    }

    public List<Penalty> getPenalties() {
        return Collections.unmodifiableList(penalties);
    }

    @Override
    public String toString() {
        return String.format("Ticket[%s | Plate: %s, Slot: %s, Floor: %d, Entry: %s, Status: %s, Total: \u20B9%.2f]",
                ticketNumber, licensePlate, slotId, floorNumber, entryTime, status, totalAmount);
    }
}

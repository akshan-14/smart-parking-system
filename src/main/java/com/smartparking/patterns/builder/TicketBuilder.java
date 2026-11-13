package com.smartparking.patterns.builder;

import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.ticket.Penalty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builder Pattern implementation for constructing ParkingTicket instances.
 * Eliminates long-parameter constructor anti-patterns and provides a clean fluent API.
 */
public class TicketBuilder {
    private int ticketId = 0;
    private String ticketNumber = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private String licensePlate;
    private VehicleType vehicleType;
    private String slotId;
    private int floorNumber = 1;
    private LocalDateTime entryTime = LocalDateTime.now();
    private LocalDateTime exitTime;
    private TicketStatus status = TicketStatus.ACTIVE;
    private double baseFee = 0.0;
    private double penaltyFee = 0.0;
    private double evChargingFee = 0.0;
    private double totalAmount = 0.0;
    private final List<Penalty> penalties = new ArrayList<>();

    public static TicketBuilder newInstance() {
        return new TicketBuilder();
    }

    public TicketBuilder setTicketId(int ticketId) {
        this.ticketId = ticketId;
        return this;
    }

    public TicketBuilder setTicketNumber(String ticketNumber) {
        if (ticketNumber != null && !ticketNumber.trim().isEmpty()) {
            this.ticketNumber = ticketNumber.trim();
        }
        return this;
    }

    public TicketBuilder setLicensePlate(String licensePlate) {
        this.licensePlate = (licensePlate != null) ? licensePlate.trim().toUpperCase() : null;
        return this;
    }

    public TicketBuilder setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
        return this;
    }

    public TicketBuilder setSlotId(String slotId) {
        this.slotId = (slotId != null) ? slotId.trim() : null;
        return this;
    }

    public TicketBuilder setFloorNumber(int floorNumber) {
        this.floorNumber = floorNumber;
        return this;
    }

    public TicketBuilder setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
        return this;
    }

    public TicketBuilder setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
        return this;
    }

    public TicketBuilder setStatus(TicketStatus status) {
        this.status = status;
        return this;
    }

    public TicketBuilder setBaseFee(double baseFee) {
        this.baseFee = Math.max(0.0, baseFee);
        return this;
    }

    public TicketBuilder setPenaltyFee(double penaltyFee) {
        this.penaltyFee = Math.max(0.0, penaltyFee);
        return this;
    }

    public TicketBuilder setEvChargingFee(double evChargingFee) {
        this.evChargingFee = Math.max(0.0, evChargingFee);
        return this;
    }

    public TicketBuilder setTotalAmount(double totalAmount) {
        this.totalAmount = Math.max(0.0, totalAmount);
        return this;
    }

    public TicketBuilder addPenalty(Penalty penalty) {
        if (penalty != null) {
            this.penalties.add(penalty);
            this.penaltyFee += penalty.getAmount();
        }
        return this;
    }

    public TicketBuilder setPenalties(List<Penalty> penalties) {
        this.penalties.clear();
        if (penalties != null) {
            this.penalties.addAll(penalties);
            this.penaltyFee = penalties.stream().mapToDouble(Penalty::getAmount).sum();
        }
        return this;
    }

    public ParkingTicket build() {
        if (licensePlate == null || licensePlate.isEmpty()) {
            throw new IllegalStateException("Cannot build ParkingTicket: License plate is required.");
        }
        if (vehicleType == null) {
            throw new IllegalStateException("Cannot build ParkingTicket: Vehicle type is required.");
        }
        if (slotId == null || slotId.isEmpty()) {
            throw new IllegalStateException("Cannot build ParkingTicket: Slot ID is required.");
        }

        double calculatedTotal = (totalAmount > 0.0) ? totalAmount : (baseFee + penaltyFee + evChargingFee);

        return new ParkingTicket(ticketId, ticketNumber, licensePlate, vehicleType, slotId,
                floorNumber, entryTime, exitTime, status, baseFee, penaltyFee, evChargingFee,
                calculatedTotal, penalties);
    }
}

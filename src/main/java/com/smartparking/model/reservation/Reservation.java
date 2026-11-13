package com.smartparking.model.reservation;

import com.smartparking.model.enums.ReservationStatus;
import com.smartparking.model.enums.VehicleType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an advance parking slot reservation made by a customer.
 * Demonstrates Encapsulation and Constructor Chaining.
 */
public class Reservation {
    private final int reservationId;
    private final String reservationCode;
    private final int userId;
    private final String slotId;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private ReservationStatus status;

    public Reservation(int reservationId, String reservationCode, int userId, String slotId,
                       String licensePlate, VehicleType vehicleType,
                       LocalDateTime startTime, LocalDateTime endTime, ReservationStatus status) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty.");
        }
        if (slotId == null || slotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Slot ID cannot be null or empty.");
        }
        if (startTime == null || endTime == null || endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
        this.reservationId = reservationId;
        this.reservationCode = (reservationCode != null && !reservationCode.trim().isEmpty())
                ? reservationCode.trim()
                : "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.userId = userId;
        this.slotId = slotId.trim();
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.vehicleType = (vehicleType != null) ? vehicleType : VehicleType.CAR;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = (status != null) ? status : ReservationStatus.CONFIRMED;
    }

    /**
     * Overloaded constructor for booking a new reservation.
     * Demonstrates constructor chaining.
     */
    public Reservation(int userId, String slotId, String licensePlate, VehicleType vehicleType,
                       LocalDateTime startTime, LocalDateTime endTime) {
        this(0, null, userId, slotId, licensePlate, vehicleType, startTime, endTime, ReservationStatus.CONFIRMED);
    }

    public boolean isActiveNow() {
        LocalDateTime now = LocalDateTime.now();
        return status == ReservationStatus.CONFIRMED && !now.isBefore(startTime) && !now.isAfter(endTime);
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public void checkIn() {
        this.status = ReservationStatus.CHECKED_IN;
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }

    public int getReservationId() {
        return reservationId;
    }

    public String getReservationCode() {
        return reservationCode;
    }

    public int getUserId() {
        return userId;
    }

    public String getSlotId() {
        return slotId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return String.format("Reservation[%s | User #%d, Slot: %s, Plate: %s, %s to %s, Status: %s]",
                reservationCode, userId, slotId, licensePlate, startTime, endTime, status);
    }
}

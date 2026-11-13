package com.smartparking.model.ev;

import java.time.LocalDateTime;

/**
 * Model representing an EV Charging Session.
 * Sourced from the SQLite charging_sessions table.
 */
public class ChargingSession {

    private final int id;
    private final int ticketId;
    private final int chargerId;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private double kwh;
    private final double unitPrice;
    private double energyCharge;
    private double idleFee;
    private String status; // ACTIVE, COMPLETED, CANCELLED

    public ChargingSession(int id, int ticketId, int chargerId, LocalDateTime startTime,
                           LocalDateTime endTime, double kwh, double unitPrice,
                           double energyCharge, double idleFee, String status) {
        this.id = id;
        this.ticketId = ticketId;
        this.chargerId = chargerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.kwh = kwh;
        this.unitPrice = unitPrice;
        this.energyCharge = energyCharge;
        this.idleFee = idleFee;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public int getTicketId() {
        return ticketId;
    }

    public int getChargerId() {
        return chargerId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public double getKwh() {
        return kwh;
    }

    public void setKwh(double kwh) {
        this.kwh = kwh;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getEnergyCharge() {
        return energyCharge;
    }

    public void setEnergyCharge(double energyCharge) {
        this.energyCharge = energyCharge;
    }

    public double getIdleFee() {
        return idleFee;
    }

    public void setIdleFee(double idleFee) {
        this.idleFee = idleFee;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status);
    }

    public double getTotalAmount() {
        return energyCharge + idleFee;
    }

    @Override
    public String toString() {
        return "ChargingSession{" +
                "id=" + id +
                ", ticketId=" + ticketId +
                ", chargerId=" + chargerId +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", kwh=" + kwh +
                ", unitPrice=" + unitPrice +
                ", energyCharge=" + energyCharge +
                ", idleFee=" + idleFee +
                ", status='" + status + '\'' +
                '}';
    }
}

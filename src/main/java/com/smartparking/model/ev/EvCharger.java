package com.smartparking.model.ev;

/**
 * Model representing an Electric Vehicle (EV) Charging Station installed in a dedicated bay.
 * Sourced from the SQLite ev_chargers table.
 */
public class EvCharger {

    private final int id;
    private final String slotId;
    private final String connectorType;
    private final double powerKw;
    private String status; // AVAILABLE, CHARGING, FAULT, OFFLINE

    public EvCharger(int id, String slotId, String connectorType, double powerKw, String status) {
        this.id = id;
        this.slotId = slotId;
        this.connectorType = connectorType;
        this.powerKw = powerKw;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public String getSlotId() {
        return slotId;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public double getPowerKw() {
        return powerKw;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAvailable() {
        return "AVAILABLE".equalsIgnoreCase(status);
    }

    public boolean isFault() {
        return "FAULT".equalsIgnoreCase(status);
    }

    public boolean isOffline() {
        return "OFFLINE".equalsIgnoreCase(status);
    }

    public boolean isCharging() {
        return "CHARGING".equalsIgnoreCase(status);
    }

    @Override
    public String toString() {
        return "EvCharger{" +
                "id=" + id +
                ", slotId='" + slotId + '\'' +
                ", connectorType='" + connectorType + '\'' +
                ", powerKw=" + powerKw +
                ", status='" + status + '\'' +
                '}';
    }
}

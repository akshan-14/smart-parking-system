package com.smartparking.model.fastag;

import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

/**
 * Model representing a FASTag account linked to an Indian vehicle plate (Simulated).
 * Features real-time prepaid balance tracking, status lifecycle, and automated barrier debit.
 */
public class FastagAccount {
    private final String tagId;
    private final String licensePlate;
    private double balance;
    private String status; // ACTIVE, BLOCKED, SUSPENDED
    private final String createdAt;
    private String updatedAt;

    public FastagAccount(String tagId, String licensePlate, double balance, String status, String createdAt, String updatedAt) {
        this.tagId = tagId != null ? tagId.trim() : "";
        this.licensePlate = PlateUtil.normalize(licensePlate);
        this.balance = Math.max(0.0, balance);
        this.status = (status != null && !status.isBlank()) ? status.toUpperCase() : "ACTIVE";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public FastagAccount(String tagId, String licensePlate, double balance) {
        this(tagId, licensePlate, balance, "ACTIVE", null, null);
    }

    public String getTagId() {
        return tagId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getPlate() {
        return licensePlate;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = Math.max(0.0, balance);
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

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return String.format("FASTag[%s | Plate: %s | Bal: %s | Status: %s]",
                tagId, PlateUtil.format(licensePlate), MoneyFormat.format(balance), status);
    }
}

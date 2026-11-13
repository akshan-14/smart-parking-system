package com.smartparking.model.membership;

import com.smartparking.model.enums.MembershipType;
import com.smartparking.model.enums.VehicleType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Represents a monthly parking pass for a registered vehicle class.
 * Flat fee model: grants 100% discount (₹0 hourly parking) during the validity window.
 */
public class MembershipPass {
    private final int membershipId;
    private final int userId;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final int durationMonths;
    private final double monthlyFee;
    private final double totalPrice;
    private final LocalDate startDate;
    private final LocalDate expiryDate;
    private boolean active;

    // Retained for backwards compatibility with legacy tests
    private final MembershipType legacyPassType;
    private final double legacyDiscountPercentage;

    public MembershipPass(int membershipId, int userId, String licensePlate,
                          VehicleType vehicleType, int durationMonths,
                          double monthlyFee, double totalPrice,
                          LocalDate startDate, LocalDate expiryDate, boolean active) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty.");
        }
        this.membershipId = membershipId;
        this.userId = userId;
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.vehicleType = (vehicleType != null) ? vehicleType : VehicleType.CAR;
        this.durationMonths = Math.max(1, durationMonths);
        this.monthlyFee = Math.max(0.0, monthlyFee);
        this.totalPrice = Math.max(0.0, totalPrice);
        this.startDate = (startDate != null) ? startDate : LocalDate.now();
        this.expiryDate = (expiryDate != null) ? expiryDate : this.startDate.plusMonths(this.durationMonths);
        this.active = active;
        this.legacyPassType = MembershipType.PLATINUM;
        this.legacyDiscountPercentage = 1.0; // 100% free hourly parking
    }

    /**
     * Legacy constructor retained for backwards compatibility.
     */
    public MembershipPass(int membershipId, int userId, String licensePlate,
                          MembershipType passType, double discountPercentage,
                          LocalDate startDate, LocalDate expiryDate, boolean active) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty.");
        }
        this.membershipId = membershipId;
        this.userId = userId;
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.vehicleType = VehicleType.CAR;
        this.durationMonths = 1;
        this.monthlyFee = 1500.0;
        this.totalPrice = 1500.0;
        this.startDate = (startDate != null) ? startDate : LocalDate.now();
        this.expiryDate = (expiryDate != null) ? expiryDate : this.startDate.plusMonths(1);
        this.active = active;
        this.legacyPassType = (passType != null) ? passType : MembershipType.PLATINUM;
        this.legacyDiscountPercentage = Math.max(0.0, Math.min(1.0, discountPercentage));
    }

    public MembershipPass(int userId, String licensePlate, MembershipType passType) {
        this(0, userId, licensePlate, passType,
                passType != null ? passType.getDiscountRate() : 1.0,
                LocalDate.now(),
                LocalDate.now().plusDays(passType != null ? passType.getValidityDays() : 30),
                true);
    }

    /**
     * Checks if this pass is currently active and non-expired.
     */
    public boolean isValid() {
        LocalDate today = LocalDate.now();
        return active && !today.isBefore(startDate) && !today.isAfter(expiryDate);
    }

    public long getDaysLeft() {
        LocalDate today = LocalDate.now();
        if (!active || today.isAfter(expiryDate)) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(today, expiryDate));
    }

    public String getStatus() {
        return isValid() ? "ACTIVE" : "EXPIRED";
    }

    public void deactivate() {
        this.active = false;
    }

    public int getMembershipId() { return membershipId; }
    public int getUserId() { return userId; }
    public String getLicensePlate() { return licensePlate; }
    public VehicleType getVehicleType() { return vehicleType; }
    public int getDurationMonths() { return durationMonths; }
    public double getMonthlyFee() { return monthlyFee; }
    public double getTotalPrice() { return totalPrice; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public boolean isActive() { return active; }

    public MembershipType getPassType() { return legacyPassType; }
    public double getDiscountPercentage() { return legacyDiscountPercentage; }

    @Override
    public String toString() {
        return String.format("Pass[ID: #%d, %s, Plate: %s, %d mo, Total: ₹%.0f, Valid: %s to %s (%s)]",
                membershipId, vehicleType.name(), licensePlate, durationMonths, totalPrice,
                startDate, expiryDate, getStatus());
    }
}

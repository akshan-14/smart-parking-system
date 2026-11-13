package com.smartparking.model.user;

import com.smartparking.model.enums.UserRole;

import java.time.LocalDateTime;

/**
 * Concrete User: Registered Customer.
 * Authorized to view own tickets, make reservations, view own passes, and manage personal data.
 */
public class Customer extends User {
    private String primaryLicensePlate;

    public Customer(int userId, String username, String passwordHash, String salt, String fullName,
                    String email, String phone, String primaryLicensePlate, boolean active, int failedAttempts,
                    LocalDateTime lockedUntil, LocalDateTime createdAt, LocalDateTime lastLoginAt,
                    boolean forcePasswordChange) {
        super(userId, username, passwordHash, salt, fullName, email, phone, UserRole.CUSTOMER,
                active, failedAttempts, lockedUntil, createdAt, lastLoginAt, forcePasswordChange);
        this.primaryLicensePlate = (primaryLicensePlate != null) ? primaryLicensePlate.trim().toUpperCase() : null;
    }

    public Customer(int userId, String username, String passwordHash, String salt, String fullName, String email, String phone, String primaryLicensePlate, LocalDateTime createdAt) {
        super(userId, username, passwordHash, salt, fullName, email, phone, UserRole.CUSTOMER, createdAt);
        this.primaryLicensePlate = (primaryLicensePlate != null) ? primaryLicensePlate.trim().toUpperCase() : null;
    }

    public Customer(int userId, String username, String passwordHash, String fullName, String email, String phone, String primaryLicensePlate, LocalDateTime createdAt) {
        this(userId, username, passwordHash, "", fullName, email, phone, primaryLicensePlate, createdAt);
    }

    public Customer(int userId, String username, String passwordHash, String salt, String fullName, String email, String phone) {
        this(userId, username, passwordHash, salt, fullName, email, phone, null, LocalDateTime.now());
    }

    public Customer(int userId, String username, String passwordHash, String fullName, String email, String phone) {
        this(userId, username, passwordHash, "", fullName, email, phone, null, LocalDateTime.now());
    }

    @Override
    public boolean hasPermission(String permissionName) {
        if (permissionName == null) return false;
        return switch (permissionName.toUpperCase()) {
            case "CREATE_RESERVATION", "VIEW_OWN_TICKET", "MAKE_PAYMENT", "PURCHASE_MEMBERSHIP", "VIEW_MY_DATA" -> true;
            default -> false;
        };
    }

    public String getPrimaryLicensePlate() {
        return primaryLicensePlate;
    }

    public void setPrimaryLicensePlate(String primaryLicensePlate) {
        this.primaryLicensePlate = (primaryLicensePlate != null) ? primaryLicensePlate.trim().toUpperCase() : null;
    }
}

package com.smartparking.model.user;

import com.smartparking.model.enums.UserRole;

import java.time.LocalDateTime;

/**
 * Concrete User: Parking Booth Attendant.
 * Authorized to check vehicles in/out, issue tickets, collect payments, and manage parking slots.
 */
public class Attendant extends User {
    private String shiftName; // e.g., "Morning", "Night"

    public Attendant(int userId, String username, String passwordHash, String salt, String fullName,
                     String email, String phone, String shiftName, boolean active, int failedAttempts,
                     LocalDateTime lockedUntil, LocalDateTime createdAt, LocalDateTime lastLoginAt,
                     boolean forcePasswordChange) {
        super(userId, username, passwordHash, salt, fullName, email, phone, UserRole.ATTENDANT,
                active, failedAttempts, lockedUntil, createdAt, lastLoginAt, forcePasswordChange);
        this.shiftName = (shiftName != null) ? shiftName : "General";
    }

    public Attendant(int userId, String username, String passwordHash, String salt, String fullName, String email, String phone, String shiftName, LocalDateTime createdAt) {
        super(userId, username, passwordHash, salt, fullName, email, phone, UserRole.ATTENDANT, createdAt);
        this.shiftName = (shiftName != null) ? shiftName : "General";
    }

    public Attendant(int userId, String username, String passwordHash, String fullName, String email, String phone, String shiftName, LocalDateTime createdAt) {
        this(userId, username, passwordHash, "", fullName, email, phone, shiftName, createdAt);
    }

    public Attendant(int userId, String username, String passwordHash, String salt, String fullName, String email, String phone) {
        this(userId, username, passwordHash, salt, fullName, email, phone, "General", LocalDateTime.now());
    }

    public Attendant(int userId, String username, String passwordHash, String fullName, String email, String phone) {
        this(userId, username, passwordHash, "", fullName, email, phone, "General", LocalDateTime.now());
    }

    @Override
    public boolean hasPermission(String permissionName) {
        if (permissionName == null) return false;
        return switch (permissionName.toUpperCase()) {
            case "ISSUE_TICKET", "PROCESS_PAYMENT", "CHECK_IN", "CHECK_OUT", "SEARCH_VEHICLE", "CREATE_RESERVATION" -> true;
            default -> false;
        };
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }
}

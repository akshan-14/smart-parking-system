package com.smartparking.model.user;

import com.smartparking.model.enums.UserRole;

import java.time.LocalDateTime;

/**
 * Concrete User: System Administrator.
 * Possesses unrestricted permissions across pricing, floors, slots, reports, users, and audit logs.
 */
public class Admin extends User {

    public Admin(int userId, String username, String passwordHash, String salt, String fullName,
                 String email, String phone, boolean active, int failedAttempts,
                 LocalDateTime lockedUntil, LocalDateTime createdAt, LocalDateTime lastLoginAt,
                 boolean forcePasswordChange) {
        super(userId, username, passwordHash, salt, fullName, email, phone, UserRole.ADMIN,
                active, failedAttempts, lockedUntil, createdAt, lastLoginAt, forcePasswordChange);
    }

    public Admin(int userId, String username, String passwordHash, String salt, String fullName, String email, String phone, LocalDateTime createdAt) {
        super(userId, username, passwordHash, salt, fullName, email, phone, UserRole.ADMIN, createdAt);
    }

    public Admin(int userId, String username, String passwordHash, String fullName, String email, String phone, LocalDateTime createdAt) {
        super(userId, username, passwordHash, "", fullName, email, phone, UserRole.ADMIN, createdAt);
    }

    public Admin(int userId, String username, String passwordHash, String salt, String fullName, String email, String phone) {
        super(userId, username, passwordHash, salt, fullName, email, phone, UserRole.ADMIN);
    }

    public Admin(int userId, String username, String passwordHash, String fullName, String email, String phone) {
        super(userId, username, passwordHash, "", fullName, email, phone, UserRole.ADMIN);
    }

    @Override
    public boolean hasPermission(String permissionName) {
        return true;
    }
}

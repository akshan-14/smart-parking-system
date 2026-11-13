package com.smartparking.model.user;

import com.smartparking.model.enums.UserRole;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract base class representing a system User.
 * Demonstrates:
 * - Abstraction: Base contracts for identity and access permissions.
 * - Encapsulation: Strict access control on credentials and profile information.
 * - Constructor Chaining.
 */
public abstract class User {
    private final int userId;
    private final String username;
    private String passwordHash;
    private String salt;
    private String fullName;
    private String email;
    private String phone;
    private final UserRole role;
    private boolean active = true;
    private int failedAttempts = 0;
    private LocalDateTime lockedUntil;
    private final LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private boolean forcePasswordChange = false;

    /**
     * Comprehensive canonical constructor for Phase 6 security & audit requirements.
     */
    public User(int userId, String username, String passwordHash, String salt, String fullName,
                String email, String phone, UserRole role, boolean active, int failedAttempts,
                LocalDateTime lockedUntil, LocalDateTime createdAt, LocalDateTime lastLoginAt,
                boolean forcePasswordChange) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty.");
        }
        if (role == null) {
            throw new IllegalArgumentException("User role cannot be null.");
        }
        this.userId = userId;
        this.username = username.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.salt = (salt != null) ? salt : "";
        this.fullName = (fullName != null) ? fullName.trim() : "";
        this.email = (email != null) ? email.trim() : "";
        this.phone = (phone != null) ? phone.trim() : "";
        this.role = role;
        this.active = active;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
        this.lastLoginAt = lastLoginAt;
        this.forcePasswordChange = forcePasswordChange;
    }

    /**
     * Standard constructor including salt and createdAt.
     */
    public User(int userId, String username, String passwordHash, String salt, String fullName,
                String email, String phone, UserRole role, LocalDateTime createdAt) {
        this(userId, username, passwordHash, salt, fullName, email, phone, role, true, 0, null, createdAt, null, false);
    }

    /**
     * Overloaded constructor without salt (defaults to empty salt).
     */
    public User(int userId, String username, String passwordHash, String fullName,
                String email, String phone, UserRole role, LocalDateTime createdAt) {
        this(userId, username, passwordHash, "", fullName, email, phone, role, true, 0, null, createdAt, null, false);
    }

    /**
     * Overloaded constructor for new registrations.
     */
    public User(int userId, String username, String passwordHash, String salt,
                String fullName, String email, String phone, UserRole role) {
        this(userId, username, passwordHash, salt, fullName, email, phone, role, true, 0, null, LocalDateTime.now(), null, false);
    }

    public User(int userId, String username, String passwordHash, String fullName,
                String email, String phone, UserRole role) {
        this(userId, username, passwordHash, "", fullName, email, phone, role, true, 0, null, LocalDateTime.now(), null, false);
    }

    public abstract boolean hasPermission(String permissionName);

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = (salt != null) ? salt : "";
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMobile() {
        return phone;
    }

    public void setMobile(String mobile) {
        this.phone = mobile;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    public void setForcePasswordChange(boolean forcePasswordChange) {
        this.forcePasswordChange = forcePasswordChange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return userId == user.userId && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username);
    }

    @Override
    public String toString() {
        return String.format("%s [ID: %d, Username: '%s', Name: '%s', Role: %s, Active: %b]",
                getClass().getSimpleName(), userId, username, fullName, role, active);
    }
}

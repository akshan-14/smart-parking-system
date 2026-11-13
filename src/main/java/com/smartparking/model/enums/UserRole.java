package com.smartparking.model.enums;

/**
 * Access control roles for system security.
 */
public enum UserRole {
    ADMIN("System Administrator"),
    ATTENDANT("Parking Booth Attendant"),
    CUSTOMER("Registered Parking Customer");

    private final String roleDescription;

    UserRole(String roleDescription) {
        this.roleDescription = roleDescription;
    }

    public String getRoleDescription() {
        return roleDescription;
    }
}

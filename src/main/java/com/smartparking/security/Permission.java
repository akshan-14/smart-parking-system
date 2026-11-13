package com.smartparking.security;

/**
 * Granular permissions for Role-Based Access Control (RBAC).
 */
public enum Permission {
    // Navigation / Screen Permissions
    VIEW_OVERVIEW,
    VIEW_FLOOR_MAP,
    VIEW_ENTRY_GATE,
    VIEW_EXIT_GATE,
    VIEW_FIND_VEHICLE,
    VIEW_BOOKING,
    VIEW_PASSES,
    VIEW_REPORTS,
    VIEW_AUDIT_LOG,
    VIEW_USER_MANAGEMENT,
    VIEW_SETTINGS,
    VIEW_MY_DATA,
    VIEW_PRIVACY_NOTICE,

    // Administrative & Operational Actions
    PARK_VEHICLE_ENTRY,
    CHECKOUT_VEHICLE,
    RELEASE_BAY,
    MAINTENANCE_BAY,
    OVERRIDE_FEE,
    REFUND_PAYMENT,
    PURGE_RESERVATIONS,
    MANAGE_FASTAG,
    UPDATE_TARIFFS,
    MANAGE_USERS,
    BACKUP_DATABASE,
    RUN_RETENTION_JOB,

    // Customer Actions
    CUSTOMER_VIEW_OWN_DATA,
    CUSTOMER_BOOK_RESERVATION,
    CUSTOMER_PURCHASE_PASS,
    CUSTOMER_LINK_VEHICLE,
    CUSTOMER_REQUEST_DELETION
}

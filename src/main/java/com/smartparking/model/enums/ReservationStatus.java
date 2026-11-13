package com.smartparking.model.enums;

/**
 * Status lifecycle of a parking slot reservation.
 */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    CANCELLED,
    EXPIRED
}

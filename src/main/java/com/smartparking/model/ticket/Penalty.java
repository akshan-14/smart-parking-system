package com.smartparking.model.ticket;

import com.smartparking.model.enums.PenaltyType;

import java.time.LocalDateTime;

/**
 * Represents a penalty assessment against a parking ticket.
 * Demonstrates Encapsulation and Constructor Chaining.
 */
public class Penalty {
    private final int penaltyId;
    private final int ticketId;
    private final PenaltyType type;
    private final double amount;
    private final String reason;
    private final LocalDateTime issuedAt;

    public Penalty(int penaltyId, int ticketId, PenaltyType type, double amount, String reason, LocalDateTime issuedAt) {
        if (type == null) {
            throw new IllegalArgumentException("Penalty type cannot be null.");
        }
        this.penaltyId = penaltyId;
        this.ticketId = ticketId;
        this.type = type;
        this.amount = Math.max(0.0, amount);
        this.reason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : type.getDescription();
        this.issuedAt = (issuedAt != null) ? issuedAt : LocalDateTime.now();
    }

    public Penalty(int ticketId, PenaltyType type, String reason) {
        this(0, ticketId, type, type.getBaseFine(), reason, LocalDateTime.now());
    }

    public Penalty(int ticketId, PenaltyType type) {
        this(0, ticketId, type, type.getBaseFine(), type.getDescription(), LocalDateTime.now());
    }

    public int getPenaltyId() {
        return penaltyId;
    }

    public int getTicketId() {
        return ticketId;
    }

    public PenaltyType getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    @Override
    public String toString() {
        return String.format("Penalty[%s: \u20B9%.2f, Reason: '%s', Issued: %s]",
                type, amount, reason, issuedAt);
    }
}

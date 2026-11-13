package com.smartparking.model.payment;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a payment transaction made for a ticket or reservation.
 * Demonstrates Encapsulation and Constructor Chaining.
 */
public class PaymentTransaction {
    private final int paymentId;
    private final Integer ticketId;        // Nullable if paying for reservation or membership
    private final Integer reservationId;   // Nullable if paying for ticket or membership
    private final Integer membershipId;    // Nullable if paying for ticket or reservation
    private final double amount;
    private final double taxableValue;
    private final double cgst;
    private final double sgst;
    private final double roundOff;
    private final PaymentMethodType paymentMethod;
    private final String transactionRef;
    private String invoiceNumber;
    private PaymentStatus status;
    private final LocalDateTime paymentTime;

    public PaymentTransaction(int paymentId, Integer ticketId, Integer reservationId, Integer membershipId,
                              double amount, double taxableValue, double cgst, double sgst, double roundOff,
                              PaymentMethodType paymentMethod, String transactionRef, String invoiceNumber,
                              PaymentStatus status, LocalDateTime paymentTime) {
        if (amount < 0.0) {
            throw new IllegalArgumentException("Payment amount cannot be negative.");
        }
        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method cannot be null.");
        }
        this.paymentId = paymentId;
        this.ticketId = ticketId;
        this.reservationId = reservationId;
        this.membershipId = membershipId;
        this.amount = amount;
        this.taxableValue = taxableValue;
        this.cgst = cgst;
        this.sgst = sgst;
        this.roundOff = roundOff;
        this.paymentMethod = paymentMethod;
        this.transactionRef = (transactionRef != null && !transactionRef.trim().isEmpty())
                ? transactionRef.trim()
                : "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.invoiceNumber = invoiceNumber;
        this.status = (status != null) ? status : PaymentStatus.PENDING;
        this.paymentTime = (paymentTime != null) ? paymentTime : LocalDateTime.now();
    }

    public PaymentTransaction(int paymentId, Integer ticketId, Integer reservationId, double amount,
                              PaymentMethodType paymentMethod, String transactionRef,
                              PaymentStatus status, LocalDateTime paymentTime) {
        this(paymentId, ticketId, reservationId, null, amount, 0.0, 0.0, 0.0, 0.0,
                paymentMethod, transactionRef, null, status, paymentTime);
    }

    /**
     * Overloaded constructor for initiating a new ticket payment.
     * Demonstrates constructor chaining.
     */
    public PaymentTransaction(int ticketId, double amount, PaymentMethodType method) {
        this(0, ticketId, null, null, amount, 0.0, 0.0, 0.0, 0.0, method, null, null, PaymentStatus.COMPLETED, LocalDateTime.now());
    }

    /**
     * Overloaded constructor for initiating a reservation deposit.
     */
    public PaymentTransaction(double amount, PaymentMethodType method, int reservationId) {
        this(0, null, reservationId, null, amount, 0.0, 0.0, 0.0, 0.0, method, null, null, PaymentStatus.COMPLETED, LocalDateTime.now());
    }

    public int getPaymentId() {
        return paymentId;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public Integer getReservationId() {
        return reservationId;
    }

    public Integer getMembershipId() {
        return membershipId;
    }

    public double getAmount() {
        return amount;
    }

    public double getTaxableValue() {
        return taxableValue;
    }

    public double getCgst() {
        return cgst;
    }

    public double getSgst() {
        return sgst;
    }

    public double getRoundOff() {
        return roundOff;
    }

    public PaymentMethodType getPaymentMethod() {
        return paymentMethod;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public LocalDateTime getPaymentTime() {
        return paymentTime;
    }

    @Override
    public String toString() {
        return String.format("Transaction[%s | Amount: ₹%.2f, Method: %s, Status: %s, Invoice: %s, Time: %s]",
                transactionRef, amount, paymentMethod, status, invoiceNumber, paymentTime);
    }
}

package com.smartparking.service;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.PaymentStatus;
import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.patterns.factory.PaymentStrategyFactory;
import com.smartparking.patterns.strategy.payment.PaymentStrategy;
import com.smartparking.repository.PaymentDao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service managing payment execution and transaction persistence.
 */
public class PaymentService {
    private final PaymentDao paymentDao;

    public PaymentService(PaymentDao paymentDao) {
        this.paymentDao = paymentDao;
    }

    public PaymentService() {
        this(new PaymentDao());
    }

    /**
     * Executes payment for a parking ticket using the specified payment method.
     */
    public PaymentTransaction processTicketPayment(int ticketId, double amount, PaymentMethodType methodType) {
        try (java.sql.Connection conn = com.smartparking.config.DatabaseConnectionManager.getInstance().getConnection()) {
            return processTicketPaymentWithConnection(conn, ticketId, amount, methodType);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Error processing ticket payment: " + e.getMessage(), e);
        }
    }

    /**
     * Executes payment for a parking ticket participating in an external atomic transaction.
     */
    public PaymentTransaction processTicketPaymentWithConnection(java.sql.Connection conn, int ticketId, double amount, PaymentMethodType methodType) throws java.sql.SQLException {
        GstCalculator.GstResult gst = new GstCalculator().calculate(amount);
        return processTicketPaymentWithConnection(conn, ticketId, gst, methodType, null);
    }

    /**
     * Executes payment for a parking ticket participating in an external atomic transaction with full GST itemization.
     */
    public PaymentTransaction processTicketPaymentWithConnection(java.sql.Connection conn, int ticketId,
                                                                 GstCalculator.GstResult gst,
                                                                 PaymentMethodType methodType,
                                                                 String invoiceNumber) throws java.sql.SQLException {
        double grandTotal = gst != null ? gst.grandTotal() : 0.0;
        double taxable = gst != null ? gst.taxableValue() : 0.0;
        double cgst = gst != null ? gst.cgstAmount() : 0.0;
        double sgst = gst != null ? gst.sgstAmount() : 0.0;
        double roundOff = gst != null ? gst.roundOff() : 0.0;

        String ref = "TXN-" + methodType.name() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (grandTotal <= 0.0) {
            PaymentTransaction freeTxn = new PaymentTransaction(0, ticketId, null, null, 0.0, 0.0, 0.0, 0.0, 0.0,
                    methodType, ref, invoiceNumber, PaymentStatus.COMPLETED, LocalDateTime.now());
            int id = paymentDao.saveWithConnection(conn, freeTxn);
            return new PaymentTransaction(id, ticketId, null, null, 0.0, 0.0, 0.0, 0.0, 0.0,
                    methodType, ref, invoiceNumber, PaymentStatus.COMPLETED, freeTxn.getPaymentTime());
        }

        PaymentStrategy strategy = PaymentStrategyFactory.createPaymentStrategy(methodType);
        boolean success = strategy.processPayment(grandTotal, ref);
        PaymentStatus status = success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;

        PaymentTransaction txn = new PaymentTransaction(0, ticketId, null, null, grandTotal, taxable, cgst, sgst, roundOff,
                methodType, ref, invoiceNumber, status, LocalDateTime.now());
        int id = paymentDao.saveWithConnection(conn, txn);

        if (!success) {
            throw new IllegalStateException("Payment processing failed for reference: " + ref);
        }

        return new PaymentTransaction(id, ticketId, null, null, grandTotal, taxable, cgst, sgst, roundOff,
                methodType, ref, invoiceNumber, status, txn.getPaymentTime());
    }

    /**
     * Executes payment for a membership pass participating in an external atomic transaction.
     */
    public PaymentTransaction processMembershipPaymentWithConnection(java.sql.Connection conn, int membershipId,
                                                                     GstCalculator.GstResult gst,
                                                                     PaymentMethodType methodType,
                                                                     String invoiceNumber) throws java.sql.SQLException {
        double grandTotal = gst != null ? gst.grandTotal() : 0.0;
        double taxable = gst != null ? gst.taxableValue() : 0.0;
        double cgst = gst != null ? gst.cgstAmount() : 0.0;
        double sgst = gst != null ? gst.sgstAmount() : 0.0;
        double roundOff = gst != null ? gst.roundOff() : 0.0;

        String ref = "TXN-PASS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PaymentStrategy strategy = PaymentStrategyFactory.createPaymentStrategy(methodType);
        boolean success = strategy.processPayment(grandTotal, ref);
        PaymentStatus status = success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;

        PaymentTransaction txn = new PaymentTransaction(0, null, null, membershipId, grandTotal, taxable, cgst, sgst, roundOff,
                methodType, ref, invoiceNumber, status, LocalDateTime.now());
        int id = paymentDao.saveWithConnection(conn, txn);

        if (!success) {
            throw new IllegalStateException("Pass payment failed for reference: " + ref);
        }

        return new PaymentTransaction(id, null, null, membershipId, grandTotal, taxable, cgst, sgst, roundOff,
                methodType, ref, invoiceNumber, status, txn.getPaymentTime());
    }

    /**
     * Executes payment deposit for an advance slot reservation.
     */
    public PaymentTransaction processReservationPayment(int reservationId, double amount, PaymentMethodType methodType) {
        PaymentStrategy strategy = PaymentStrategyFactory.createPaymentStrategy(methodType);
        String ref = "TXN-RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        boolean success = strategy.processPayment(amount, ref);
        PaymentStatus status = success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;

        PaymentTransaction txn = new PaymentTransaction(0, null, reservationId, amount, methodType, ref, status, LocalDateTime.now());
        int id = paymentDao.save(txn);

        return new PaymentTransaction(id, null, reservationId, amount, methodType, ref, status, txn.getPaymentTime());
    }

    public List<PaymentTransaction> getTicketPayments(int ticketId) {
        return paymentDao.findByTicketId(ticketId);
    }

    public List<PaymentTransaction> getAllTransactions() {
        return paymentDao.findAll();
    }
}

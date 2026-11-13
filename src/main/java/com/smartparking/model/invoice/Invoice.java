package com.smartparking.model.invoice;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import java.time.LocalDateTime;

/**
 * Model representing a comprehensive Indian GST Tax Invoice.
 * Follows Rule 46 of the CGST Rules, 2017.
 */
public class Invoice {
    private int invoiceId;
    private final String invoiceNumber;
    private final String financialYear;
    private final String invoiceDate;
    private final Integer ticketId;
    private final Integer membershipId;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private long durationMinutes;
    private final double taxableValue;
    private final double cgstRate;
    private final double cgstAmount;
    private final double sgstRate;
    private final double sgstAmount;
    private final double roundOff;
    private final double totalAmount;
    private final String amountInWords;
    private final String paymentMethod;
    private final String transactionRef;
    private final String qrData;

    public Invoice(int invoiceId, String invoiceNumber, String financialYear, String invoiceDate,
                   Integer ticketId, Integer membershipId, String licensePlate, VehicleType vehicleType,
                   LocalDateTime entryTime, LocalDateTime exitTime, long durationMinutes,
                   double taxableValue, double cgstRate, double cgstAmount,
                   double sgstRate, double sgstAmount, double roundOff,
                   double totalAmount, String amountInWords, String paymentMethod,
                   String transactionRef, String qrData) {
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.financialYear = financialYear;
        this.invoiceDate = invoiceDate != null ? invoiceDate : DateFormat.display(LocalDateTime.now());
        this.ticketId = ticketId;
        this.membershipId = membershipId;
        this.licensePlate = PlateUtil.normalize(licensePlate);
        this.vehicleType = (vehicleType != null) ? vehicleType : VehicleType.CAR;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.durationMinutes = durationMinutes;
        this.taxableValue = Math.max(0.0, taxableValue);
        this.cgstRate = cgstRate;
        this.cgstAmount = Math.max(0.0, cgstAmount);
        this.sgstRate = sgstRate;
        this.sgstAmount = Math.max(0.0, sgstAmount);
        this.roundOff = roundOff;
        this.totalAmount = Math.max(0.0, totalAmount);
        this.amountInWords = amountInWords;
        this.paymentMethod = paymentMethod != null ? paymentMethod : "CASH";
        this.transactionRef = transactionRef != null ? transactionRef : "N/A";
        this.qrData = qrData != null ? qrData : invoiceNumber;
    }

    public int getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(int invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public String getInvoiceDate() {
        return invoiceDate;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public Integer getMembershipId() {
        return membershipId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(long durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public double getTaxableValue() {
        return taxableValue;
    }

    public double getCgstRate() {
        return cgstRate;
    }

    public double getCgstAmount() {
        return cgstAmount;
    }

    public double getSgstRate() {
        return sgstRate;
    }

    public double getSgstAmount() {
        return sgstAmount;
    }

    public double getRoundOff() {
        return roundOff;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public String getAmountInWords() {
        return amountInWords;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public String getQrData() {
        return qrData;
    }
}

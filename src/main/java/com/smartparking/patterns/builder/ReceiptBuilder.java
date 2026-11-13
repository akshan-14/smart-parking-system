package com.smartparking.patterns.builder;

import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.ticket.Penalty;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder Pattern implementation for constructing itemized customer receipts.
 * Standardized for Indian Rupee (INR - ₹) and Asia/Kolkata timezone presentations.
 */
public class ReceiptBuilder {

    private String facilityName = "Smart Parking Facility";
    private String ticketNumber = "N/A";
    private String licensePlate = "N/A";
    private String slotLocation = "N/A";
    private String entryTimeStr = "N/A";
    private String exitTimeStr = "N/A";
    private long durationMinutes = 0;
    private double baseFee = 0.0;
    private String pricingPolicy = "Automated Tariff (Indian Garage)";
    private double evChargingFee = 0.0;
    private double discountAmount = 0.0;
    private String membershipTier = null;
    private String monthlyPassNotice = null;
    private final List<String> penaltyDetails = new ArrayList<>();
    private double totalAmount = 0.0;
    private String paymentMethod = "CASH";
    private String transactionRef = "N/A";

    public static ReceiptBuilder newInstance() {
        return new ReceiptBuilder();
    }

    public ReceiptBuilder setFacilityName(String facilityName) {
        if (facilityName != null) this.facilityName = facilityName;
        return this;
    }

    public ReceiptBuilder setTicket(ParkingTicket ticket) {
        if (ticket != null) {
            this.ticketNumber = ticket.getTicketNumber();
            this.licensePlate = ticket.getLicensePlate();
            this.slotLocation = ticket.getSlotId() + " (Floor " + ticket.getFloorNumber() + ")";
            this.entryTimeStr = ticket.getEntryTime() != null ? DateFormat.display(ticket.getEntryTime()) : "N/A";
            this.exitTimeStr = ticket.getExitTime() != null ? DateFormat.display(ticket.getExitTime()) : "N/A";
            this.durationMinutes = ticket.getDurationInMinutes();
            this.baseFee = ticket.getBaseFee();
            this.evChargingFee = ticket.getEvChargingFee();
            this.totalAmount = ticket.getTotalAmount();

            for (Penalty p : ticket.getPenalties()) {
                this.penaltyDetails.add(String.format("Penalty [%s]: %s (%s)", p.getType(), MoneyFormat.format(p.getAmount()), p.getReason()));
            }
        }
        return this;
    }

    public ReceiptBuilder setPaymentTransaction(PaymentTransaction transaction) {
        if (transaction != null) {
            this.paymentMethod = transaction.getPaymentMethod().getDescription();
            this.transactionRef = transaction.getTransactionRef();
        }
        return this;
    }

    public ReceiptBuilder setPricingPolicy(String pricingPolicy) {
        if (pricingPolicy != null) this.pricingPolicy = pricingPolicy;
        return this;
    }

    public ReceiptBuilder setMonthlyPass(LocalDate expiryDate) {
        if (expiryDate != null) {
            this.monthlyPassNotice = "Monthly Pass: Active until " + DateFormat.displayDate(expiryDate);
        }
        return this;
    }

    public ReceiptBuilder setMembershipDiscount(double discountAmount, String membershipTier) {
        this.discountAmount = Math.max(0.0, discountAmount);
        this.membershipTier = membershipTier;
        return this;
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("                 OFFICIAL PARKING RECEIPT           \n");
        sb.append("               ").append(facilityName).append("\n");
        sb.append("====================================================\n");
        sb.append(String.format("Ticket Number   : %s\n", ticketNumber));
        sb.append(String.format("License Plate   : %s  [%s]\n", PlateUtil.format(licensePlate), PlateUtil.district(licensePlate)));
        sb.append(String.format("Slot Location   : %s\n", slotLocation));
        sb.append(String.format("Entry Timestamp : %s\n", entryTimeStr));
        sb.append(String.format("Exit Timestamp  : %s\n", exitTimeStr));
        long hrs = durationMinutes / 60;
        long mins = durationMinutes % 60;
        sb.append(String.format("Total Duration  : %d hrs %d mins (~%d hrs)\n", hrs, mins, (long) Math.ceil(durationMinutes / 60.0)));
        sb.append("----------------------------------------------------\n");
        sb.append("ITEMIZED CHARGES:\n");
        sb.append(String.format("  * Base Parking Fee    : %s (%s)\n", MoneyFormat.format(baseFee), pricingPolicy));

        if (evChargingFee > 0.0) {
            sb.append(String.format("  * EV Charging Station : %s\n", MoneyFormat.format(evChargingFee)));
        }

        for (String penalty : penaltyDetails) {
            sb.append(String.format("  * %s\n", penalty));
        }

        if (monthlyPassNotice != null) {
            sb.append(String.format("  * %s (Hourly Fee Waived)\n", monthlyPassNotice));
        } else if (discountAmount > 0.0) {
            String tierSuffix = (membershipTier != null && !membershipTier.isBlank()) ? " (" + membershipTier + ")" : "";
            sb.append(String.format("  * Membership Discount : -%s%s\n", MoneyFormat.format(discountAmount), tierSuffix));
        }

        sb.append("----------------------------------------------------\n");
        sb.append(String.format("TOTAL PAID (Incl. GST): %s\n", MoneyFormat.format(totalAmount)));
        sb.append(String.format("Payment Method  : %s\n", paymentMethod));
        sb.append(String.format("Transaction Ref : %s\n", transactionRef));
        sb.append("====================================================\n");
        sb.append("           Thank you for parking with us!           \n");
        sb.append("====================================================\n");
        return sb.toString();
    }
}

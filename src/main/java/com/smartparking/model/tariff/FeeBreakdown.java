package com.smartparking.model.tariff;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Detailed fee breakdown computed automatically at checkout.
 * Presented to attendant/customer before payment is initiated.
 */
public class FeeBreakdown {
    private final String ticketNumber;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final LocalDateTime entryTime;
    private final LocalDateTime exitTime;
    private final long durationMinutes;
    private final long billedHours;
    private final double baseFee;
    private final double peakMultiplier;
    private final boolean peakApplied;
    private final double passDiscount;
    private final boolean passActive;
    private final LocalDate passExpiryDate;
    private final double lostTicketPenalty;
    private final double overnightFee;
    private final double evKwh;
    private final double evUnitPrice;
    private final double evEnergyCharge;
    private final double idleFee;
    private final double totalAmount;

    public FeeBreakdown(String ticketNumber, String licensePlate, VehicleType vehicleType,
                        LocalDateTime entryTime, LocalDateTime exitTime, long durationMinutes,
                        long billedHours, double baseFee, double peakMultiplier, boolean peakApplied,
                        double passDiscount, boolean passActive, LocalDate passExpiryDate,
                        double lostTicketPenalty, double overnightFee,
                        double evKwh, double evUnitPrice, double evEnergyCharge, double idleFee,
                        double totalAmount) {
        this.ticketNumber = ticketNumber;
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.durationMinutes = durationMinutes;
        this.billedHours = billedHours;
        this.baseFee = baseFee;
        this.peakMultiplier = peakMultiplier;
        this.peakApplied = peakApplied;
        this.passDiscount = passDiscount;
        this.passActive = passActive;
        this.passExpiryDate = passExpiryDate;
        this.lostTicketPenalty = lostTicketPenalty;
        this.overnightFee = overnightFee;
        this.evKwh = evKwh;
        this.evUnitPrice = evUnitPrice;
        this.evEnergyCharge = evEnergyCharge;
        this.idleFee = idleFee;
        this.totalAmount = Math.max(0.0, totalAmount);
    }

    public FeeBreakdown(String ticketNumber, String licensePlate, VehicleType vehicleType,
                        LocalDateTime entryTime, LocalDateTime exitTime, long durationMinutes,
                        long billedHours, double baseFee, double peakMultiplier, boolean peakApplied,
                        double passDiscount, boolean passActive, LocalDate passExpiryDate,
                        double lostTicketPenalty, double overnightFee, double totalAmount) {
        this(ticketNumber, licensePlate, vehicleType, entryTime, exitTime, durationMinutes,
                billedHours, baseFee, peakMultiplier, peakApplied, passDiscount, passActive,
                passExpiryDate, lostTicketPenalty, overnightFee, 0.0, 0.0, 0.0, 0.0, totalAmount);
    }

    public String getTicketNumber() { return ticketNumber; }
    public String getLicensePlate() { return licensePlate; }
    public VehicleType getVehicleType() { return vehicleType; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public long getDurationMinutes() { return durationMinutes; }
    public long getBilledHours() { return billedHours; }
    public double getBaseFee() { return baseFee; }
    public double getPeakMultiplier() { return peakMultiplier; }
    public boolean isPeakApplied() { return peakApplied; }
    public double getPassDiscount() { return passDiscount; }
    public boolean isPassActive() { return passActive; }
    public LocalDate getPassExpiryDate() { return passExpiryDate; }
    public double getLostTicketPenalty() { return lostTicketPenalty; }
    public double getOvernightFee() { return overnightFee; }
    public double getEvKwh() { return evKwh; }
    public double getEvUnitPrice() { return evUnitPrice; }
    public double getEvEnergyCharge() { return evEnergyCharge; }
    public double getIdleFee() { return idleFee; }
    public double getTotalAmount() { return totalAmount; }

    public String getBillingRuleNotice() {
        return "First 15 minutes free. Billed per started hour. Daily cap applies.";
    }

    public String toSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("            FEE BREAKDOWN & BILLING PREVIEW         \n");
        sb.append("====================================================\n");
        sb.append(String.format("Ticket Number   : %s\n", ticketNumber));
        sb.append(String.format("License Plate   : %s  [%s]\n", PlateUtil.format(licensePlate), PlateUtil.district(licensePlate)));
        sb.append(String.format("Vehicle Class   : %s\n", vehicleType != null ? vehicleType.getDisplayName() : "Unknown"));
        sb.append(String.format("Entry Time      : %s\n", DateFormat.display(entryTime)));
        sb.append(String.format("Exit Time       : %s\n", DateFormat.display(exitTime)));
        long hrs = durationMinutes / 60;
        long mins = durationMinutes % 60;
        sb.append(String.format("Duration        : %d hrs %d mins (%d billed hours)\n", hrs, mins, billedHours));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("Base Parking Fee: %s\n", MoneyFormat.format(baseFee)));

        if (peakApplied && peakMultiplier > 1.0) {
            sb.append(String.format("Peak Multiplier : %.2fx (Surge pricing active)\n", peakMultiplier));
        }

        if (overnightFee > 0.0) {
            sb.append(String.format("Overnight Fee   : %s (Parked past midnight)\n", MoneyFormat.format(overnightFee)));
        }

        if (passActive) {
            sb.append(String.format("Monthly Pass    : Active until %s (-%s)\n",
                    DateFormat.displayDate(passExpiryDate), MoneyFormat.format(passDiscount)));
        }

        if (lostTicketPenalty > 0.0) {
            sb.append(String.format("Lost Ticket Pen.: %s\n", MoneyFormat.format(lostTicketPenalty)));
        }

        if (evEnergyCharge > 0.0) {
            sb.append(String.format("EV Charging     : %.2f kWh @ %s/kWh = %s (Simulated)\n",
                    evKwh, MoneyFormat.format(evUnitPrice), MoneyFormat.format(evEnergyCharge)));
        }

        if (idleFee > 0.0) {
            sb.append(String.format("EV Idle Fee     : %s (Plugged beyond grace period)\n", MoneyFormat.format(idleFee)));
        }

        sb.append("----------------------------------------------------\n");
        sb.append(String.format("TOTAL DUE       : %s\n", MoneyFormat.format(totalAmount)));
        sb.append("Rule: ").append(getBillingRuleNotice()).append("\n");
        sb.append("====================================================\n");
        return sb.toString();
    }
}

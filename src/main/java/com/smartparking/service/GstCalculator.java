package com.smartparking.service;

import com.smartparking.repository.SystemSettingsDao;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for calculating Goods and Services Tax (GST) compliant with Indian tax regulations.
 * Parking services fall under SAC 9967 (Supporting services in transport).
 *
 * NOTE FOR COLLEGE PROJECT SUBMISSION:
 * Verify the current GST rate applicable to parking services before final submission.
 * Standard rate is 18% (9% CGST + 9% SGST) for commercial parking facilities in Uttar Pradesh.
 */
public class GstCalculator {

    private final SystemSettingsDao settingsDao;
    private final Double overrideRate;
    private final String overrideGstin;
    private final String overrideBusinessName;
    private final String overrideAddress;

    public GstCalculator(SystemSettingsDao settingsDao) {
        this.settingsDao = settingsDao != null ? settingsDao : new SystemSettingsDao();
        this.overrideRate = null;
        this.overrideGstin = null;
        this.overrideBusinessName = null;
        this.overrideAddress = null;
    }

    public GstCalculator(double gstRate, String gstin, String businessName, String businessAddress) {
        this.settingsDao = null;
        this.overrideRate = gstRate;
        this.overrideGstin = gstin;
        this.overrideBusinessName = businessName;
        this.overrideAddress = businessAddress;
    }

    public GstCalculator() {
        this(new SystemSettingsDao());
    }

    /**
     * Immutable data record encapsulating complete GST breakdown.
     */
    public record GstResult(
            double taxableValue,
            double gstRatePercent,
            double cgstRatePercent,
            double cgstAmount,
            double sgstRatePercent,
            double sgstAmount,
            double roundOff,
            double grandTotal
    ) {}

    /**
     * Calculates CGST, SGST, round-off, and grand total for a given taxable value.
     * If taxableValue <= 0 (e.g. grace period or active pass), GST is ₹0.00.
     * Uses RoundingMode.HALF_UP for exact 2-decimal tax values, and shows round-off
     * separately so the grand total is an exact whole rupee.
     */
    public GstResult calculate(double taxableValue) {
        if (taxableValue <= 0.0) {
            double rate = getGstRatePercent();
            return new GstResult(0.0, rate, rate / 2.0, 0.0, rate / 2.0, 0.0, 0.0, 0.0);
        }

        double rate = getGstRatePercent();
        double halfRate = rate / 2.0;

        BigDecimal taxableBd = BigDecimal.valueOf(taxableValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cgstRateBd = BigDecimal.valueOf(halfRate);
        BigDecimal sgstRateBd = BigDecimal.valueOf(halfRate);
        BigDecimal hundred = BigDecimal.valueOf(100.0);

        // CGST = taxableValue * (rate / 2) / 100 rounded HALF_UP
        BigDecimal cgstBd = taxableBd.multiply(cgstRateBd).divide(hundred, 2, RoundingMode.HALF_UP);

        // SGST = taxableValue * (rate / 2) / 100 rounded HALF_UP
        BigDecimal sgstBd = taxableBd.multiply(sgstRateBd).divide(hundred, 2, RoundingMode.HALF_UP);

        // Exact total before round-off
        BigDecimal exactTotalBd = taxableBd.add(cgstBd).add(sgstBd);

        // Grand total rounded to nearest whole rupee (0 decimal places)
        BigDecimal grandTotalBd = exactTotalBd.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);

        // Round-off = grandTotal - exactTotal
        BigDecimal roundOffBd = grandTotalBd.subtract(exactTotalBd).setScale(2, RoundingMode.HALF_UP);

        return new GstResult(
                taxableBd.doubleValue(),
                rate,
                halfRate,
                cgstBd.doubleValue(),
                halfRate,
                sgstBd.doubleValue(),
                roundOffBd.doubleValue(),
                grandTotalBd.doubleValue()
        );
    }

    public double getGstRatePercent() {
        if (overrideRate != null) return overrideRate;
        return settingsDao != null ? settingsDao.getDouble("gst_percentage", 18.0) : 18.0;
    }

    public String getGstin() {
        if (overrideGstin != null) return overrideGstin;
        return settingsDao != null ? settingsDao.getString("gstin", "09AAAAA0000A1Z5") : "09AAAAA0000A1Z5";
    }

    public String getBusinessName() {
        if (overrideBusinessName != null) return overrideBusinessName;
        return settingsDao != null ? settingsDao.getString("business_name", "Grand Tech Park Garage") : "Grand Tech Park Garage";
    }

    public String getBusinessAddress() {
        if (overrideAddress != null) return overrideAddress;
        return settingsDao != null ? settingsDao.getString("business_address", "100 Innovation Blvd, Gomti Nagar, Lucknow, Uttar Pradesh 226010") : "100 Innovation Blvd, Gomti Nagar, Lucknow, Uttar Pradesh 226010";
    }

    public String getUpiVpa() {
        return settingsDao != null ? settingsDao.getString("upi_vpa", "parking@upi") : "parking@upi";
    }
}

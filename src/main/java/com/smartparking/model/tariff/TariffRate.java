package com.smartparking.model.tariff;

import com.smartparking.model.enums.VehicleType;

/**
 * Model representing tariff rates and caps for a specific vehicle class.
 * Sourced directly from the SQLite tariffs table (Single Source of Truth).
 */
public class TariffRate {
    private final VehicleType vehicleType;
    private final double firstHourRate;
    private final double extraHourRate;
    private final double dailyCap;
    private final double overnightRate;
    private final double monthlyPassRate;

    public TariffRate(VehicleType vehicleType, double firstHourRate, double extraHourRate,
                      double dailyCap, double overnightRate, double monthlyPassRate) {
        this.vehicleType = vehicleType;
        this.firstHourRate = firstHourRate;
        this.extraHourRate = extraHourRate;
        this.dailyCap = dailyCap;
        this.overnightRate = overnightRate;
        this.monthlyPassRate = monthlyPassRate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public double getFirstHourRate() {
        return firstHourRate;
    }

    public double getExtraHourRate() {
        return extraHourRate;
    }

    public double getDailyCap() {
        return dailyCap;
    }

    public double getOvernightRate() {
        return overnightRate;
    }

    public double getMonthlyPassRate() {
        return monthlyPassRate;
    }

    @Override
    public String toString() {
        return String.format("TariffRate[%s: 1st Hr=₹%.0f, Extra=₹%.0f/hr, Cap=₹%.0f/day, Overnight=₹%.0f, Pass=₹%.0f/mo]",
                vehicleType.name(), firstHourRate, extraHourRate, dailyCap, overnightRate, monthlyPassRate);
    }
}

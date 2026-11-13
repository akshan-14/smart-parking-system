package com.smartparking.patterns.strategy.pricing;

import com.smartparking.model.ticket.ParkingTicket;

/**
 * Strategy Pattern interface for parking pricing algorithms.
 * Encapsulates dynamic pricing models (Standard, Peak-Hour Surge, Weekend Discount).
 */
public interface PricingStrategy {

    /**
     * Computes the base parking fee.
     * @param ticket The parking ticket with entry and exit details
     * @param baseHourlyRate The slot's designated base rate per hour
     * @param currentOccupancyPercentage Current parking facility occupancy rate (0.0 to 100.0)
     * @return Computed base parking fee
     */
    double calculatePrice(ParkingTicket ticket, double baseHourlyRate, double currentOccupancyPercentage);

    /**
     * Descriptive name of this pricing algorithm.
     */
    String getStrategyName();
}

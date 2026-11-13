package com.smartparking.patterns.strategy.pricing;

import com.smartparking.model.ticket.ParkingTicket;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * Concrete Strategy: Weekend Discount Pricing.
 * Automatically applies a 20% discount if the vehicle is parked on Saturday or Sunday.
 */
public class WeekendPricingStrategy implements PricingStrategy {
    private static final double WEEKEND_DISCOUNT = 0.20; // 20% discount

    @Override
    public double calculatePrice(ParkingTicket ticket, double baseHourlyRate, double currentOccupancyPercentage) {
        if (ticket == null) return 0.0;

        long minutes = ticket.getDurationInMinutes();
        if (minutes <= 15) return 0.0;

        long hours = Math.max(1, ticket.getDurationInHours());
        double subtotal = hours * baseHourlyRate;

        // Check if ticket entry was on weekend
        LocalDateTime entry = ticket.getEntryTime();
        if (entry.getDayOfWeek() == DayOfWeek.SATURDAY || entry.getDayOfWeek() == DayOfWeek.SUNDAY) {
            subtotal = subtotal * (1.0 - WEEKEND_DISCOUNT);
        }

        return subtotal;
    }

    @Override
    public String getStrategyName() {
        return "Weekend 20% Discount Pricing";
    }
}

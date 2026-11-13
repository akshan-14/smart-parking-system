package com.smartparking.patterns.strategy.pricing;

import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.service.TariffService;

/**
 * Concrete Strategy: Peak-Hour & Occupancy Surge Pricing.
 * Dynamically adjusts parking rates based on real-time facility demand and occupancy
 * configured in the settings table.
 */
public class PeakHourPricingStrategy implements PricingStrategy {

    private final TariffService tariffService;

    public PeakHourPricingStrategy(TariffService tariffService) {
        this.tariffService = (tariffService != null) ? tariffService : new TariffService();
    }

    public PeakHourPricingStrategy() {
        this(new TariffService());
    }

    @Override
    public double calculatePrice(ParkingTicket ticket, double baseHourlyRate, double currentOccupancyPercentage) {
        if (ticket == null) return 0.0;

        long minutes = ticket.getDurationInMinutes();
        if (minutes <= tariffService.getGracePeriodMinutes()) {
            return 0.0; // Grace period
        }

        long hours = Math.max(1, ticket.getDurationInHours());
        double multiplier = getSurgeMultiplier(currentOccupancyPercentage);

        return (hours * baseHourlyRate) * multiplier;
    }

    public double getSurgeMultiplier(double occupancyPercentage) {
        return tariffService.getPeakMultiplier(occupancyPercentage);
    }

    @Override
    public String getStrategyName() {
        return "Peak-Hour Surge Pricing";
    }
}

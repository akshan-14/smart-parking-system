package com.smartparking.patterns.strategy.pricing;

import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.service.TariffService;

/**
 * Concrete Strategy: Standard Hourly Pricing.
 * Charges flat hourly rates rounded up to the nearest hour, with a settings-configured grace period.
 */
public class StandardHourlyPricingStrategy implements PricingStrategy {

    private final TariffService tariffService;

    public StandardHourlyPricingStrategy(TariffService tariffService) {
        this.tariffService = (tariffService != null) ? tariffService : new TariffService();
    }

    public StandardHourlyPricingStrategy() {
        this(new TariffService());
    }

    @Override
    public double calculatePrice(ParkingTicket ticket, double baseHourlyRate, double currentOccupancyPercentage) {
        if (ticket == null) return 0.0;

        long minutes = ticket.getDurationInMinutes();
        if (minutes <= tariffService.getGracePeriodMinutes()) {
            return 0.0;
        }

        long hours = ticket.getDurationInHours();
        return Math.max(1, hours) * baseHourlyRate;
    }

    @Override
    public String getStrategyName() {
        return "Standard Hourly (" + tariffService.getGracePeriodMinutes() + "-min Grace Period)";
    }
}

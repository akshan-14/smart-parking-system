package com.smartparking.service;

import com.smartparking.model.tariff.FeeBreakdown;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.patterns.strategy.pricing.PricingStrategy;
import com.smartparking.patterns.strategy.pricing.StandardHourlyPricingStrategy;

import java.time.LocalDateTime;

/**
 * Service acting as Context for fee computation and pricing operations.
 * Coordinates automatic tariff calculation and membership deductions.
 */
public class PricingService {
    private PricingStrategy activeStrategy;
    private final MembershipService membershipService;
    private final TariffService tariffService;

    public PricingService(PricingStrategy initialStrategy, MembershipService membershipService, TariffService tariffService) {
        this.activeStrategy = (initialStrategy != null) ? initialStrategy : new StandardHourlyPricingStrategy();
        this.membershipService = (membershipService != null) ? membershipService : new MembershipService();
        this.tariffService = (tariffService != null) ? tariffService : new TariffService();
    }

    public PricingService(PricingStrategy initialStrategy, MembershipService membershipService) {
        this(initialStrategy, membershipService, new TariffService());
    }

    public PricingService() {
        this(new StandardHourlyPricingStrategy(), new MembershipService(), new TariffService());
    }

    /**
     * Calculates final parking fee automatically from vehicle class, stay duration,
     * daily cap, and active monthly pass status.
     */
    public double calculateTotalParkingFee(ParkingTicket ticket, double baseHourlyRate, double currentOccupancyPercentage) {
        if (ticket == null) return 0.0;

        LocalDateTime exitTime = ticket.getExitTime() != null ? ticket.getExitTime() : LocalDateTime.now();
        FeeBreakdown breakdown = tariffService.calculateFee(ticket, exitTime, false,
                membershipService.getActiveMembership(ticket.getLicensePlate()).orElse(null));

        return breakdown.getTotalAmount();
    }

    public FeeBreakdown calculateFeePreview(ParkingTicket ticket, LocalDateTime exitTime, boolean isLost) {
        if (ticket == null) return null;
        return tariffService.calculateFee(ticket, exitTime, isLost,
                membershipService.getActiveMembership(ticket.getLicensePlate()).orElse(null));
    }

    public PricingStrategy getActiveStrategy() {
        return activeStrategy;
    }

    public void setActiveStrategy(PricingStrategy activeStrategy) {
        if (activeStrategy == null) {
            throw new IllegalArgumentException("Pricing strategy cannot be null.");
        }
        this.activeStrategy = activeStrategy;
    }

    public MembershipService getMembershipService() {
        return membershipService;
    }

    public TariffService getTariffService() {
        return tariffService;
    }
}

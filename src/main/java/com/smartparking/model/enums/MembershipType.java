package com.smartparking.model.enums;

/**
 * Membership tiers offering progressive discounts and benefits.
 */
public enum MembershipType {
    SILVER("Silver Tier", 0.10, 30),      // 10% discount, 30 days valid
    GOLD("Gold Tier", 0.20, 90),          // 20% discount, 90 days valid
    PLATINUM("Platinum Tier", 0.35, 365); // 35% discount, 365 days valid

    private final String tierName;
    private final double discountRate;
    private final int validityDays;

    MembershipType(String tierName, double discountRate, int validityDays) {
        this.tierName = tierName;
        this.discountRate = discountRate;
        this.validityDays = validityDays;
    }

    public String getTierName() {
        return tierName;
    }

    public double getDiscountRate() {
        return discountRate;
    }

    public int getValidityDays() {
        return validityDays;
    }
}

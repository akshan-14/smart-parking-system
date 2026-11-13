package com.smartparking.model.slot;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.vehicle.Vehicle;

/**
 * Concrete ParkingSlot: Electric Charging Slot.
 * Equipped with EV charging infrastructure.
 * Accommodates Electric Cars (EV_CAR) and Electric Two-Wheelers (EV_TWO_WHEELER).
 */
public class ElectricSlot extends ParkingSlot {
    private double chargingRatePerHour;
    private boolean isChargerActive;

    public ElectricSlot(String slotId, int floorNumber, String slotNumber, double hourlyRate, double chargingRatePerHour) {
        super(slotId, floorNumber, slotNumber, SlotType.ELECTRIC, hourlyRate);
        this.chargingRatePerHour = Math.max(0.0, chargingRatePerHour);
        this.isChargerActive = false;
    }

    public ElectricSlot(String slotId, int floorNumber, String slotNumber) {
        this(slotId, floorNumber, slotNumber, SlotType.ELECTRIC.getDefaultHourlyRate(), 15.0);
    }

    @Override
    public boolean canAccommodate(Vehicle vehicle) {
        if (vehicle == null) return false;
        if (vehicle.isDivyangPermitted()) return false;
        return vehicle.getType() == VehicleType.EV_CAR || vehicle.getType() == VehicleType.EV_TWO_WHEELER;
    }

    public double getChargingRatePerHour() {
        return chargingRatePerHour;
    }

    public void setChargingRatePerHour(double chargingRatePerHour) {
        if (chargingRatePerHour >= 0.0) {
            this.chargingRatePerHour = chargingRatePerHour;
        }
    }

    public boolean isChargerActive() {
        return isChargerActive;
    }

    public void setChargerActive(boolean chargerActive) {
        isChargerActive = chargerActive;
    }

    @Override
    public void vacate() {
        super.vacate();
        this.isChargerActive = false; // reset charging station on vehicle departure
    }
}

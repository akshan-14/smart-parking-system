package com.smartparking.patterns.factory;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.vehicle.*;

/**
 * Factory Pattern implementation for creating concrete Vehicle instances.
 * Encapsulates vehicle instantiation based on runtime parameters.
 */
public class VehicleFactory {

    public static Vehicle createVehicle(VehicleType type, String licensePlate, String color, String brand) {
        if (type == null) {
            throw new IllegalArgumentException("VehicleType cannot be null.");
        }
        return switch (type) {
            case MOTORCYCLE, BIKE -> new Motorcycle(licensePlate, color, brand);
            case AUTO_RICKSHAW -> new AutoRickshaw(licensePlate, color, brand);
            case CAR -> new Car(licensePlate, color, brand);
            case EV_CAR, ELECTRIC_CAR -> new ElectricCar(licensePlate, color, brand, 60.0, 50.0, false);
            case EV_TWO_WHEELER -> new EvTwoWheeler(licensePlate, color, brand, false);
            case LCV -> new Lcv(licensePlate, color, brand);
            case HCV, TRUCK -> new Hcv(licensePlate, color, brand);
        };
    }

    public static Vehicle createVehicle(VehicleType type, String licensePlate) {
        return createVehicle(type, licensePlate, "Unknown", "Unknown");
    }

    public static Vehicle createVehicle(String typeStr, String licensePlate) {
        VehicleType type = VehicleType.fromString(typeStr);
        return createVehicle(type, licensePlate);
    }
}

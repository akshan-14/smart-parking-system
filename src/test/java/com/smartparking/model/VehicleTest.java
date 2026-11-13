package com.smartparking.model;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.vehicle.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Vehicle Hierarchy & Polymorphism Tests")
class VehicleTest {

    @Test
    @DisplayName("Should demonstrate constructor chaining and valid vehicle creation")
    void testVehicleConstructorChaining() {
        Vehicle car = new Car("UP32AB1234", "White", "Honda");
        assertEquals("UP32AB1234", car.getLicensePlate());
        assertEquals(VehicleType.CAR, car.getType());
        assertEquals("White", car.getColor());
        assertEquals("Honda", car.getBrand());

        Vehicle bike = new Bike("UP16XY9999");
        assertEquals("UP16XY9999", bike.getLicensePlate());
        assertTrue(bike.getType() == VehicleType.MOTORCYCLE || bike.getType() == VehicleType.BIKE);
        assertEquals("Unknown", bike.getColor());
    }

    @Test
    @DisplayName("Should test runtime polymorphism on canFitIn across vehicle subclasses")
    void testRuntimePolymorphismCanFitIn() {
        Vehicle bike = new Bike("B-1");
        Vehicle car = new Car("C-1");
        Vehicle truck = new Truck("T-1");
        ElectricCar evWithCharging = new ElectricCar("EV-1", "Blue", "Tesla", 60.0, 40.0, true);
        ElectricCar evWithoutCharging = new ElectricCar("EV-2", "Red", "Nissan", 40.0, 80.0, false);

        // Strict suitability: Bike fits only in COMPACT
        assertTrue(bike.canFitIn(SlotType.COMPACT));
        assertFalse(bike.canFitIn(SlotType.REGULAR));
        assertFalse(bike.canFitIn(SlotType.LARGE));

        // Car fits in REGULAR
        assertTrue(car.canFitIn(SlotType.REGULAR));
        assertFalse(car.canFitIn(SlotType.COMPACT));
        assertFalse(car.canFitIn(SlotType.LARGE));

        // Truck fits only in LARGE
        assertTrue(truck.canFitIn(SlotType.LARGE));
        assertFalse(truck.canFitIn(SlotType.COMPACT));
        assertFalse(truck.canFitIn(SlotType.REGULAR));

        // EV with charging request fits only in ELECTRIC
        assertTrue(evWithCharging.canFitIn(SlotType.ELECTRIC));
        assertFalse(evWithCharging.canFitIn(SlotType.REGULAR));

        // EV without charging request fits in ELECTRIC and REGULAR
        assertTrue(evWithoutCharging.canFitIn(SlotType.ELECTRIC));
        assertTrue(evWithoutCharging.canFitIn(SlotType.REGULAR));
    }

    @Test
    @DisplayName("Should validate license plate normalization and equality contract")
    void testVehicleEquality() {
        Vehicle v1 = new Car("up32ab1234");
        Vehicle v2 = new Car("UP32AB1234");
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }
}

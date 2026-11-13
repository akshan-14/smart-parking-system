package com.smartparking.model;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.slot.HandicappedSlot;
import com.smartparking.model.vehicle.Car;
import com.smartparking.model.vehicle.ElectricCar;
import com.smartparking.model.vehicle.Truck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Handicapped Accessibility Permit Validation Tests")
class HandicappedPermitTest {

    @Test
    @DisplayName("Car with handicapped permit is accepted in HandicappedSlot")
    void testCarWithPermitAccepted() {
        Car permittedCar = new Car("UP32HC1111", "Silver", "Toyota", true);
        assertTrue(permittedCar.isHandicappedPermitted());
        assertTrue(permittedCar.canFitIn(SlotType.HANDICAPPED));

        HandicappedSlot slot = new HandicappedSlot("A47", 1, "A47", 30.0);
        assertTrue(slot.canAccommodate(permittedCar));
    }

    @Test
    @DisplayName("Car without handicapped permit is rejected from HandicappedSlot")
    void testCarWithoutPermitRejected() {
        Car regularCar = new Car("UP32RG2222", "Black", "Honda", false);
        assertFalse(regularCar.isHandicappedPermitted());
        assertFalse(regularCar.canFitIn(SlotType.HANDICAPPED));

        HandicappedSlot slot = new HandicappedSlot("A47", 1, "A47", 30.0);
        assertFalse(slot.canAccommodate(regularCar));
    }

    @Test
    @DisplayName("Electric car with handicapped permit is accepted in HandicappedSlot")
    void testElectricCarWithPermitAccepted() {
        ElectricCar permittedEv = new ElectricCar("UP16EV3333", "White", "Nissan", 60.0, 50.0, false, true);
        assertTrue(permittedEv.isHandicappedPermitted());
        assertTrue(permittedEv.canFitIn(SlotType.HANDICAPPED));

        HandicappedSlot slot = new HandicappedSlot("A47", 1, "A47", 30.0);
        assertTrue(slot.canAccommodate(permittedEv));
    }

    @Test
    @DisplayName("Truck is rejected from HandicappedSlot regardless of permits")
    void testTruckInHandicappedSlotRejected() {
        Truck truck = new Truck("UP14TR9999");
        assertFalse(truck.canFitIn(SlotType.HANDICAPPED));

        HandicappedSlot slot = new HandicappedSlot("A47", 1, "A47", 30.0);
        assertFalse(slot.canAccommodate(truck));
    }
}

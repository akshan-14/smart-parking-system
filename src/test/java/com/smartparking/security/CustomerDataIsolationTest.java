package com.smartparking.security;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.UserVehicleDao;
import com.smartparking.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: Customer Data Isolation & Vehicle Privacy Tests")
public class CustomerDataIsolationTest {

    private UserVehicleDao vehicleDao;

    @BeforeEach
    void setUp() {
        DatabaseInitializer.initializeDatabase();
        this.vehicleDao = new UserVehicleDao();
    }

    @Test
    @DisplayName("Customer only retrieves their own linked vehicle plates")
    void testCustomerPlateIsolation() {
        AuthService authService = new AuthService();
        var cust2 = authService.registerCustomer("Customer Two", "9876543219", "customertwo", "Cust@12345", true);

        int cust1Id = 3; // seeded customer1
        int cust2Id = cust2.getUserId();

        vehicleDao.linkVehicle(cust1Id, "UP32AB1234");
        vehicleDao.linkVehicle(cust2Id, "UP14ZZ9999");

        List<String> cust1Plates = vehicleDao.findPlatesByUserId(cust1Id);
        List<String> cust2Plates = vehicleDao.findPlatesByUserId(cust2Id);

        assertTrue(cust1Plates.contains("UP32AB1234"));
        assertFalse(cust1Plates.contains("UP14ZZ9999"), "Customer 1 must never see Customer 2's vehicle");

        assertTrue(vehicleDao.isVehicleOwnedByUser(cust1Id, "UP32AB1234"));
        assertFalse(vehicleDao.isVehicleOwnedByUser(cust1Id, "UP14ZZ9999"));
    }
}

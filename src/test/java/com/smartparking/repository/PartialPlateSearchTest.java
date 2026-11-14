package com.smartparking.repository;

import com.smartparking.support.TestDatabase;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.ticket.ParkingTicket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Partial License Plate Search Tests")
class PartialPlateSearchTest {

    private static TicketDao ticketDao;

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_partial_search");
        DatabaseInitializer.initializeDatabase();
        ticketDao = new TicketDao();

        // Seed sample test tickets (unique plates not conflicting with seeded demo ticket)
        ParkingTicket t1 = new ParkingTicket("UP32PS1234", VehicleType.CAR, "A20", 1, LocalDateTime.now().minusHours(1));
        ParkingTicket t2 = new ParkingTicket("UP16PS9999", VehicleType.CAR, "A21", 1, LocalDateTime.now().minusHours(2));
        ParkingTicket t3 = new ParkingTicket("22BH5678PS", VehicleType.CAR, "A22", 1, LocalDateTime.now().minusHours(3));
        ticketDao.save(t1);
        ticketDao.save(t2);
        ticketDao.save(t3);
    }

    @Test
    @DisplayName("Partial search by middle numbers or characters matches successfully")
    void testPartialSearch() {
        // Search by "1234"
        List<ParkingTicket> res1 = ticketDao.searchActiveByPlate("1234");
        assertTrue(res1.stream().anyMatch(t -> t.getLicensePlate().contains("1234")));

        // Search by district code "UP16"
        List<ParkingTicket> res2 = ticketDao.searchActiveByPlate("UP16");
        assertTrue(res2.stream().anyMatch(t -> t.getLicensePlate().startsWith("UP16")));

        // Search by Bharat series "22BH"
        List<ParkingTicket> res3 = ticketDao.searchActiveByPlate("22bh");
        assertTrue(res3.stream().anyMatch(t -> t.getLicensePlate().contains("22BH")));

        // All active tickets
        List<ParkingTicket> all = ticketDao.findAllActive();
        assertTrue(all.size() >= 3);
    }
}

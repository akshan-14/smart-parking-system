package com.smartparking.service;

import com.smartparking.model.enums.PenaltyType;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.ticket.Penalty;
import com.smartparking.repository.TicketDao;

/**
 * Service managing enforcement and assessment of parking penalties.
 * Sourced from the settings/tariff table.
 */
public class PenaltyService {
    private final TicketDao ticketDao;
    private final TariffService tariffService;

    public PenaltyService(TicketDao ticketDao, TariffService tariffService) {
        this.ticketDao = ticketDao;
        this.tariffService = (tariffService != null) ? tariffService : new TariffService();
    }

    public PenaltyService(TicketDao ticketDao) {
        this(ticketDao, new TariffService());
    }

    public PenaltyService() {
        this(new TicketDao(), new TariffService());
    }

    /**
     * Assesses a penalty for a lost parking ticket from the tariff settings.
     */
    public Penalty assessLostTicketPenalty(ParkingTicket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Parking ticket cannot be null.");
        }
        double amount = tariffService.getLostTicketPenalty();
        Penalty penalty = new Penalty(0, ticket.getTicketId(), PenaltyType.LOST_TICKET, amount,
                "Customer reported ticket lost.", null);
        ticket.addPenalty(penalty);
        ticket.markLost();
        if (ticket.getTicketId() > 0) {
            ticketDao.savePenalty(penalty);
            ticketDao.update(ticket);
        }
        return penalty;
    }

    /**
     * Checks and applies overstay penalties if duration exceeds allowed threshold (e.g. 24 hours).
     */
    public boolean checkAndApplyOverstay(ParkingTicket ticket, int maxPermittedHours) {
        if (ticket == null) return false;
        long hours = ticket.getDurationInHours();
        if (hours > maxPermittedHours) {
            long excessHours = hours - maxPermittedHours;
            double amount = PenaltyType.OVERSTAY.getBaseFine() + (excessHours * tariffService.getOverstayPenaltyPerHour());
            Penalty penalty = new Penalty(0, ticket.getTicketId(), PenaltyType.OVERSTAY, amount,
                    "Overstay of " + excessHours + " hours beyond permitted " + maxPermittedHours + "h.", null);
            ticket.addPenalty(penalty);
            if (ticket.getTicketId() > 0) {
                ticketDao.savePenalty(penalty);
                ticketDao.update(ticket);
            }
            return true;
        }
        return false;
    }
}

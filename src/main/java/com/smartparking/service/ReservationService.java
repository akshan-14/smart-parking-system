package com.smartparking.service;

import com.smartparking.model.enums.ReservationStatus;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.reservation.Reservation;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.user.User;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.ReservationDao;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.security.UserSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service managing parking slot advance reservations.
 * Includes automated background scheduler for no-show reservation expiry (15-min grace).
 */
public class ReservationService {
    private final ReservationDao reservationDao;
    private final ParkingSlotDao parkingSlotDao;
    private final TariffService tariffService;
    private ScheduledExecutorService autoExpiryScheduler;

    public ReservationService(ReservationDao reservationDao, ParkingSlotDao parkingSlotDao, TariffService tariffService) {
        this.reservationDao = reservationDao;
        this.parkingSlotDao = parkingSlotDao;
        this.tariffService = (tariffService != null) ? tariffService : new TariffService();
        // Immediate startup purge of stale past reservations
        purgeExpiredReservations(this.tariffService.getReservationGraceMinutes());
        // Start background scheduled expiry task (daemon)
        startAutoExpiryScheduler(this.tariffService.getReservationGraceMinutes(), 30);
    }

    public ReservationService(ReservationDao reservationDao, ParkingSlotDao parkingSlotDao) {
        this(reservationDao, parkingSlotDao, new TariffService());
    }

    public ReservationService() {
        this(new ReservationDao(), new ParkingSlotDao(), new TariffService());
    }

    /**
     * Starts the background daemon task that automatically purges no-show expired reservations.
     */
    public synchronized void startAutoExpiryScheduler(int graceMinutes, long periodSeconds) {
        if (autoExpiryScheduler == null || autoExpiryScheduler.isShutdown()) {
            autoExpiryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Reservation-AutoExpiry-Daemon");
                t.setDaemon(true);
                return t;
            });
            autoExpiryScheduler.scheduleAtFixedRate(() -> {
                try {
                    purgeExpiredReservations(graceMinutes);
                } catch (Exception e) {
                    System.err.println("[AUTO-EXPIRY] Error purging expired reservations: " + e.getMessage());
                }
            }, periodSeconds, periodSeconds, TimeUnit.SECONDS);
        }
    }

    public synchronized void stopAutoExpiryScheduler() {
        if (autoExpiryScheduler != null && !autoExpiryScheduler.isShutdown()) {
            autoExpiryScheduler.shutdownNow();
        }
    }

    /**
     * Reserves a designated parking slot for a customer within the given time range.
     */
    public Reservation makeReservation(int userId, String slotId, String licensePlate,
                                       VehicleType vehicleType, LocalDateTime startTime, LocalDateTime endTime) {
        if (UserSession.getInstance().isAuthenticated()) {
            User u = UserSession.getInstance().getCurrentUser().orElse(null);
            if (u != null && u.getRole() == UserRole.CUSTOMER) {
                AccessControl.checkPermission(Permission.CUSTOMER_BOOK_RESERVATION);
            } else {
                AccessControl.checkPermission(Permission.VIEW_BOOKING);
            }
        }

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time must be specified.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
        if (licensePlate == null || !com.smartparking.util.PlateUtil.isValid(licensePlate)) {
            throw new IllegalArgumentException("License plate must be a valid UP or Bharat series registration.");
        }

        Optional<ParkingSlot> slotOpt = parkingSlotDao.findById(slotId);
        if (slotOpt.isEmpty()) {
            throw new IllegalArgumentException("Slot with ID " + slotId + " does not exist.");
        }

        ParkingSlot slot = slotOpt.get();
        if (!slot.isAvailable()) {
            throw new IllegalStateException("Slot " + slotId + " is not currently available for reservation.");
        }
        if (reservationDao.hasOverlap(slotId, startTime, endTime)) {
            throw new IllegalStateException("Slot " + slotId + " is already reserved during the requested time window.");
        }

        // Apply State Pattern on slot
        slot.reserve();
        parkingSlotDao.updateStatus(slotId, "RESERVED", com.smartparking.util.PlateUtil.normalize(licensePlate));

        Reservation res = new Reservation(userId, slotId, com.smartparking.util.PlateUtil.normalize(licensePlate), vehicleType, startTime, endTime);
        int generatedId = reservationDao.save(res);

        AuditLogService.getInstance().log("RESERVATION_CREATED", "RESERVATION", res.getReservationCode(),
                null, "slot=" + slotId + ", plate=" + res.getLicensePlate(), "Advance parking slot reserved", "SUCCESS");

        return new Reservation(generatedId, res.getReservationCode(), userId, slotId,
                res.getLicensePlate(), vehicleType, startTime, endTime, ReservationStatus.CONFIRMED);
    }

    /**
     * Extends a confirmed reservation by a specified number of hours.
     */
    public boolean extendReservation(String reservationCode, int extraHours) {
        if (extraHours <= 0) return false;
        Optional<Reservation> resOpt = reservationDao.findByCode(reservationCode);
        if (resOpt.isEmpty()) return false;
        Reservation res = resOpt.get();
        if (res.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed reservations can be extended.");
        }
        LocalDateTime newEnd = res.getEndTime().plusHours(extraHours);
        if (reservationDao.hasOverlap(res.getSlotId(), res.getEndTime(), newEnd)) {
            throw new IllegalStateException("Cannot extend: another reservation conflicts with the new duration.");
        }
        reservationDao.extendEndTime(res.getReservationId(), newEnd);
        return true;
    }

    /**
     * Validates and checks in an incoming reservation.
     */
    public Optional<Reservation> checkInReservation(String licensePlate) {
        Optional<Reservation> resOpt = reservationDao.findActiveByPlate(licensePlate);
        if (resOpt.isPresent()) {
            Reservation res = resOpt.get();
            res.checkIn();
            reservationDao.updateStatus(res.getReservationId(), ReservationStatus.CHECKED_IN);
            return Optional.of(res);
        }
        return Optional.empty();
    }

    /**
     * Cancels an existing reservation and frees the slot.
     */
    public boolean cancelReservation(String reservationCode) {
        Optional<Reservation> resOpt = reservationDao.findByCode(reservationCode);
        if (resOpt.isPresent()) {
            Reservation res = resOpt.get();
            res.cancel();
            reservationDao.updateStatus(res.getReservationId(), ReservationStatus.CANCELLED);
            parkingSlotDao.updateStatus(res.getSlotId(), "AVAILABLE", null);
            AuditLogService.getInstance().log("RESERVATION_CANCELLED", "RESERVATION", reservationCode,
                    "CONFIRMED", "CANCELLED", "User cancelled advance booking", "SUCCESS");
            return true;
        }
        return false;
    }

    /**
     * Purges confirmed reservations whose start time plus grace period has passed (no-shows).
     * Marks them EXPIRED and releases the parking slots.
     * @param graceMinutes Grace period in minutes after reservation start before cancellation
     * @return Number of reservations expired
     */
    public int purgeExpiredReservations(int graceMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(0, graceMinutes));
        List<Reservation> expired = reservationDao.findConfirmedReservationsPastStartTime(cutoff);
        for (Reservation res : expired) {
            res.expire();
            reservationDao.updateStatus(res.getReservationId(), ReservationStatus.EXPIRED);
            parkingSlotDao.updateStatus(res.getSlotId(), "AVAILABLE", null);
        }
        if (!expired.isEmpty()) {
            AuditLogService.getInstance().logWithActor(null, "SYSTEM", "SYSTEM", "RESERVATIONS_PURGED", "RESERVATION", "BATCH",
                    null, "count=" + expired.size(), "Automatic purge of stale no-show reservations", "SUCCESS");
        }
        return expired.size();
    }

    public List<Reservation> getUserReservations(int userId) {
        return reservationDao.findByUserId(userId);
    }

    public List<Reservation> getAllReservations() {
        return reservationDao.findAll();
    }
}

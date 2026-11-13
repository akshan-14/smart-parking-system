package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.MembershipType;
import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.invoice.Invoice;
import com.smartparking.model.membership.MembershipPass;
import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.model.tariff.TariffRate;
import com.smartparking.repository.MembershipDao;
import com.smartparking.util.PlateUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing customer membership subscriptions, flat monthly pass issuance,
 * renewals, cancellations, and pass verification.
 */
public class MembershipService {
    public record PassSubscriptionResult(MembershipPass pass, PaymentTransaction payment, Invoice invoice) {}

    private final MembershipDao membershipDao;
    private final TariffService tariffService;
    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final GstCalculator gstCalculator;

    public MembershipService(MembershipDao membershipDao, TariffService tariffService) {
        this.membershipDao = membershipDao;
        this.tariffService = tariffService;
        this.paymentService = new PaymentService();
        this.invoiceService = new InvoiceService();
        this.gstCalculator = new GstCalculator();
    }

    public MembershipService(MembershipDao membershipDao) {
        this(membershipDao, new TariffService());
    }

    public MembershipService() {
        this(new MembershipDao(), new TariffService());
    }

    public MembershipPass issuePass(String licensePlate, VehicleType vehicleType, int durationMonths) {
        return issuePass(1, licensePlate, vehicleType, LocalDate.now(), durationMonths);
    }

    public double calculatePassPrice(VehicleType vehicleType, int durationMonths) {
        TariffRate tariff = tariffService.getTariffRate(vehicleType);
        return tariff.getMonthlyPassRate() * Math.max(1, durationMonths);
    }

    /**
     * Issues a new flat-fee monthly pass with mandatory payment settlement and sequential GST invoice.
     */
    public PassSubscriptionResult issuePassWithPayment(int userId, String licensePlate, VehicleType vehicleType,
                                                       LocalDate startDate, int durationMonths,
                                                       PaymentMethodType paymentMethod) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty.");
        }
        String normPlate = PlateUtil.normalize(licensePlate);
        if (!PlateUtil.isValid(normPlate)) {
            throw new IllegalArgumentException("Enter a valid UP plate, e.g. UP32 AB 1234");
        }

        if (vehicleType == null) {
            vehicleType = VehicleType.CAR;
        }

        if (startDate == null) {
            startDate = LocalDate.now();
        }

        if (durationMonths <= 0) {
            durationMonths = 1;
        }

        if (paymentMethod == null) {
            paymentMethod = PaymentMethodType.UPI;
        }

        // Rule: Block issuing a second active pass for the same plate
        Optional<MembershipPass> existing = membershipDao.findActiveByPlate(normPlate);
        if (existing.isPresent() && existing.get().isValid()) {
            throw new IllegalStateException("An active pass already exists for this vehicle. Use Renew instead.");
        }

        // Determine monthly fee from tariffs table
        TariffRate tariff = tariffService.getTariffRate(vehicleType);
        double monthlyFee = tariff.getMonthlyPassRate();
        double totalPrice = monthlyFee * durationMonths;
        LocalDate expiryDate = startDate.plusMonths(durationMonths);

        MembershipPass pass = new MembershipPass(0, userId, normPlate, vehicleType,
                durationMonths, monthlyFee, totalPrice, startDate, expiryDate, true);

        // Atomic transaction: save pass, create invoice, save payment transaction
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                int generatedId = membershipDao.saveWithConnection(conn, pass);
                MembershipPass savedPass = new MembershipPass(generatedId, userId, normPlate, vehicleType,
                        durationMonths, monthlyFee, totalPrice, startDate, expiryDate, true);

                GstCalculator.GstResult gst = gstCalculator.calculate(totalPrice);
                String txnRef = "TXN-PASS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                Invoice invoice = invoiceService.createMembershipInvoice(conn, savedPass, gst, paymentMethod.name(), txnRef);
                PaymentTransaction payment = paymentService.processMembershipPaymentWithConnection(conn, generatedId, gst, paymentMethod, invoice.getInvoiceNumber());

                conn.commit();
                AuditLogService.getInstance().log("PASS_ISSUED", "PASS", String.valueOf(generatedId),
                        null, "plate=" + normPlate + ", duration=" + durationMonths, "Monthly pass issued", "SUCCESS");
                System.out.printf("[SIMULATED PAYMENT] Recorded %s for %d-month %s membership pass for %s (Expires: %s, Invoice: %s)%n",
                        com.smartparking.util.MoneyFormat.format(gst.grandTotal()), durationMonths, vehicleType.name(), normPlate, expiryDate, invoice.getInvoiceNumber());

                return new PassSubscriptionResult(savedPass, payment, invoice);
            } catch (Exception ex) {
                conn.rollback();
                throw new RuntimeException("Failed to issue pass with payment: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error during pass subscription: " + e.getMessage(), e);
        }
    }

    /**
     * Issues a new flat-fee monthly pass for a vehicle class and duration.
     * Blocks duplicate active passes for the same license plate.
     */
    public MembershipPass issuePass(int userId, String licensePlate, VehicleType vehicleType,
                                    LocalDate startDate, int durationMonths) {
        return issuePassWithPayment(userId, licensePlate, vehicleType, startDate, durationMonths, PaymentMethodType.UPI).pass();
    }

    /**
     * Renews an existing membership pass with payment settlement and sequential GST invoice.
     */
    public PassSubscriptionResult renewMembershipWithPayment(int membershipId, int additionalMonths, PaymentMethodType paymentMethod) {
        if (additionalMonths <= 0) additionalMonths = 1;
        if (paymentMethod == null) paymentMethod = PaymentMethodType.UPI;

        List<MembershipPass> all = membershipDao.findAll();
        MembershipPass pass = all.stream()
                .filter(p -> p.getMembershipId() == membershipId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Membership pass #" + membershipId + " not found."));

        LocalDate base = pass.getExpiryDate().isAfter(LocalDate.now()) ? pass.getExpiryDate() : LocalDate.now();
        LocalDate newExpiry = base.plusMonths(additionalMonths);

        TariffRate tariff = tariffService.getTariffRate(pass.getVehicleType());
        double renewalCost = tariff.getMonthlyPassRate() * additionalMonths;

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                membershipDao.renewWithConnection(conn, membershipId, newExpiry, additionalMonths, renewalCost);

                MembershipPass updatedPass = new MembershipPass(membershipId, pass.getUserId(), pass.getLicensePlate(),
                        pass.getVehicleType(), pass.getDurationMonths() + additionalMonths,
                        pass.getMonthlyFee(), pass.getTotalPrice() + renewalCost,
                        pass.getStartDate(), newExpiry, true);

                GstCalculator.GstResult gst = gstCalculator.calculate(renewalCost);
                String txnRef = "TXN-RENEW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                Invoice invoice = invoiceService.createMembershipInvoice(conn, updatedPass, gst, paymentMethod.name(), txnRef);
                PaymentTransaction payment = paymentService.processMembershipPaymentWithConnection(conn, membershipId, gst, paymentMethod, invoice.getInvoiceNumber());

                conn.commit();
                AuditLogService.getInstance().log("PASS_RENEWED", "PASS", String.valueOf(membershipId),
                        null, "extraMonths=" + additionalMonths + ", newExpiry=" + newExpiry, "Monthly pass renewed", "SUCCESS");
                System.out.printf("[SIMULATED PAYMENT] Renewed Pass #%d for %s by %d month(s). Recorded fee: %s (New Expiry: %s, Invoice: %s)%n",
                        membershipId, pass.getLicensePlate(), additionalMonths, com.smartparking.util.MoneyFormat.format(gst.grandTotal()), newExpiry, invoice.getInvoiceNumber());

                return new PassSubscriptionResult(updatedPass, payment, invoice);
            } catch (Exception ex) {
                conn.rollback();
                throw new RuntimeException("Failed to renew pass with payment: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error during pass renewal: " + e.getMessage(), e);
        }
    }

    /**
     * Renews an existing membership pass by extending its expiry date.
     */
    public MembershipPass renewMembership(int membershipId, int additionalMonths) {
        return renewMembershipWithPayment(membershipId, additionalMonths, PaymentMethodType.UPI).pass();
    }

    /**
     * Cancels an active membership pass.
     */
    public void cancelMembership(int membershipId) {
                membershipDao.cancel(membershipId);
                AuditLogService.getInstance().log("PASS_CANCELLED", "PASS", String.valueOf(membershipId),
                        "ACTIVE", "CANCELLED", "Membership pass cancelled", "SUCCESS");
    }

    /**
     * Retrieves active valid membership for plate, if any.
     */
    public Optional<MembershipPass> getActiveMembership(String licensePlate) {
        if (licensePlate == null) return Optional.empty();
        return membershipDao.findActiveByPlate(PlateUtil.normalize(licensePlate));
    }

    /**
     * Retrieves the latest membership record (active or expired) for display and status verification.
     */
    public Optional<MembershipPass> getLatestMembership(String licensePlate) {
        if (licensePlate == null) return Optional.empty();
        return membershipDao.findLatestByPlate(PlateUtil.normalize(licensePlate));
    }

    public List<MembershipPass> getAllMemberships() {
        return membershipDao.findAll();
    }

    public List<MembershipPass> getUserMemberships(int userId) {
        return membershipDao.findByUserId(userId);
    }

    /**
     * Applies membership discount to a calculated parking fee.
     * Flat monthly pass holders pay ₹0.00 hourly parking fee!
     */
    public double applyMembershipDiscount(String licensePlate, double rawFee) {
        Optional<MembershipPass> passOpt = getActiveMembership(licensePlate);
        if (passOpt.isPresent() && passOpt.get().isValid()) {
            return 0.0; // 100% covered by flat monthly pass
        }
        return rawFee;
    }

    /**
     * Legacy subscription purchase helper retained for backwards compatibility.
     */
    public MembershipPass purchaseMembership(int userId, String licensePlate, MembershipType passType) {
        return issuePass(userId, licensePlate, VehicleType.CAR, LocalDate.now(), 1);
    }
}

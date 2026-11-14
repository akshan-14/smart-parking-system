package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.fastag.FastagAccount;
import com.smartparking.repository.AuditLogDao;
import com.smartparking.repository.FastagDao;
import com.smartparking.repository.SqliteTransaction;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.security.UserSession;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Service managing electronic toll and parking collection via FASTag (Simulated).
 * Facilitates contactless automatic gate access and electronic toll wallet debits.
 */
public class FastagService {

    private final FastagDao fastagDao;
    private final AuditLogDao auditLogDao;

    public FastagService(FastagDao fastagDao, AuditLogDao auditLogDao) {
        this.fastagDao = fastagDao != null ? fastagDao : new FastagDao();
        this.auditLogDao = auditLogDao != null ? auditLogDao : new AuditLogDao();
    }

    public FastagService() {
        this(new FastagDao(), new AuditLogDao());
    }

    /**
     * Detects whether a vehicle license plate has an active FASTag account (Simulated).
     */
    public Optional<FastagAccount> detectTag(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) return Optional.empty();
        return fastagDao.findByPlate(licensePlate)
                .filter(FastagAccount::isActive);
    }

    public Optional<FastagAccount> getAccountByPlate(String licensePlate) {
        return fastagDao.findByPlate(licensePlate);
    }

    public Optional<FastagAccount> getAccountByTagId(String tagId) {
        return fastagDao.findByTagId(tagId);
    }

    public List<FastagAccount> getAllAccounts() {
        return fastagDao.findAll();
    }

    /**
     * Executes an electronic wallet debit for a parking transaction (Simulated).
     *
     * @param conn Open database connection participating in the checkout transaction
     * @param plate Vehicle license plate
     * @param amount Grand total amount in INR to deduct
     * @throws IllegalStateException If tag not found, inactive/blocked, or has insufficient balance
     */
    public void debit(Connection conn, String plate, double amount) throws SQLException {
        FastagAccount acc = fastagDao.findByPlate(plate)
                .orElseThrow(() -> new IllegalStateException("No FASTag account linked to vehicle " + plate));

        if (!acc.isActive()) {
            throw new IllegalStateException("FASTag " + acc.getTagId() + " is " + acc.getStatus() + ". Please use an alternative payment method.");
        }

        if (acc.getBalance() < amount) {
            throw new IllegalStateException(String.format("Insufficient FASTag balance: Available %s, Required %s.",
                    MoneyFormat.format(acc.getBalance()), MoneyFormat.format(amount)));
        }

        double newBalance = acc.getBalance() - amount;
        fastagDao.updateBalance(conn, acc.getTagId(), newBalance);
        System.out.printf("[SIMULATED FASTAG] Debited %s from %s (%s). New Balance: %s%n",
                MoneyFormat.format(amount), acc.getTagId(), plate, MoneyFormat.format(newBalance));
    }

    /**
     * Admin Action: Tops up a FASTag account balance and logs the action to audit_log.
     */
    public void topUp(String tagId, double amount, String adminUser, String reason) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.MANAGE_FASTAG);
        }
        if (!(amount > 0) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Top-up amount must be greater than zero.");
        }
        String note = (reason != null && !reason.isBlank()) ? reason : "Prepaid wallet recharge";

        // Balance is read and written while holding the write lock, so concurrent top-ups/debits cannot lose an
        // update, and the new balance commits together with its FASTAG_TOPUP entry.
        SqliteTransaction.immediate(conn -> {
            FastagAccount acc = fastagDao.findByTagId(conn, tagId)
                    .orElseThrow(() -> new IllegalArgumentException("FASTag ID '" + tagId + "' not found."));
            double oldBal = acc.getBalance();
            double newBal = oldBal + amount;
            fastagDao.updateBalance(conn, acc.getTagId(), newBal);
            AuditLogService.getInstance().recordInTransactionAsActor(conn, adminUser, "FASTAG_TOPUP", "TICKET",
                    acc.getTagId(), MoneyFormat.format(oldBal), MoneyFormat.format(newBal), note, "SUCCESS");
            return null;
        });
    }

    /**
     * Admin Action: Links a FASTag identifier to an Indian vehicle plate.
     */
    public void linkTag(String tagId, String plate, double initialBalance, String adminUser, String reason) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.MANAGE_FASTAG);
        }
        if (tagId == null || tagId.isBlank()) {
            throw new IllegalArgumentException("Tag ID cannot be empty.");
        }
        if (!(initialBalance >= 0) || Double.isInfinite(initialBalance)) {
            throw new IllegalArgumentException("Initial balance must be zero or more.");
        }
        String normPlate = PlateUtil.normalize(plate);
        if (!PlateUtil.isValid(normPlate)) {
            throw new IllegalArgumentException("Invalid vehicle registration plate: " + plate);
        }
        String normTag = tagId.trim().toUpperCase();
        String note = (reason != null && !reason.isBlank()) ? reason : "Linked FASTag to plate";

        FastagAccount account = new FastagAccount(normTag, normPlate, initialBalance, "ACTIVE", null, null);
        SqliteTransaction.immediate(conn -> {
            // Linking an existing tag overwrites its plate, balance and status, so record what was replaced.
            String previous = fastagDao.findByTagId(conn, normTag)
                    .map(a -> "plate=" + a.getLicensePlate() + ", balance=" + MoneyFormat.format(a.getBalance())
                            + ", status=" + a.getStatus())
                    .orElse(null);
            fastagDao.save(conn, account);
            AuditLogService.getInstance().recordInTransactionAsActor(conn, adminUser, "FASTAG_LINK", "TICKET", normTag,
                    previous, "plate=" + normPlate + ", balance=" + MoneyFormat.format(initialBalance) + ", status=ACTIVE",
                    note, "SUCCESS");
            return null;
        });
    }

    public void linkTag(String tagId, String plate, double initialBalance, String adminUser) {
        linkTag(tagId, plate, initialBalance, adminUser, "Linked FASTag to plate");
    }

    /**
     * Admin Action: Changes the status of a FASTag account (e.g. BLOCKED, ACTIVE).
     */
    public void setStatus(String tagId, String newStatus, String adminUser, String reason) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.MANAGE_FASTAG);
        }
        if (newStatus == null || !java.util.Set.of("ACTIVE", "BLOCKED", "SUSPENDED").contains(newStatus.trim().toUpperCase())) {
            throw new IllegalArgumentException("Invalid FASTag status: " + newStatus);
        }
        String status = newStatus.trim().toUpperCase();
        String note = (reason != null && !reason.isBlank()) ? reason : "Status updated by administrator";

        SqliteTransaction.immediate(conn -> {
            FastagAccount acc = fastagDao.findByTagId(conn, tagId)
                    .orElseThrow(() -> new IllegalArgumentException("FASTag ID '" + tagId + "' not found."));
            fastagDao.updateStatus(conn, acc.getTagId(), status);
            AuditLogService.getInstance().recordInTransactionAsActor(conn, adminUser, "FASTAG_STATUS_CHANGE", "TICKET",
                    acc.getTagId(), acc.getStatus(), status, note, "SUCCESS");
            return null;
        });
    }

    public void updateStatus(String tagId, String newStatus, String adminUser, String reason) {
        setStatus(tagId, newStatus, adminUser, reason);
    }
}

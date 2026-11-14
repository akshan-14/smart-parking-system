package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.user.User;
import com.smartparking.repository.SqliteTransaction;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.security.UserSession;
import com.smartparking.util.DateFormat;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing automated data retention and customer erasure workflows
 * in strict compliance with the Digital Personal Data Protection (DPDP) Act, 2023.
 */
public class DataRetentionService {

    private static volatile DataRetentionService instance;
    private final SystemSettingsDao settingsDao;
    private final AuditLogService auditLogService;

    public DataRetentionService(SystemSettingsDao settingsDao, AuditLogService auditLogService) {
        this.settingsDao = settingsDao;
        this.auditLogService = auditLogService;
    }

    public DataRetentionService() {
        this(new SystemSettingsDao(), AuditLogService.getInstance());
    }

    public static DataRetentionService getInstance() {
        if (instance == null) {
            synchronized (DataRetentionService.class) {
                if (instance == null) {
                    instance = new DataRetentionService();
                }
            }
        }
        return instance;
    }

    /**
     * Executes the DPDP statutory retention job:
     * - Anonymizes ticket and plate data older than retention limit (365 days).
     * - Retains accounting and invoice figures for statutory tax compliance.
     * - Cleans up entry snapshots older than 30 days.
     */
    public RetentionReport runRetentionJob() {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.RUN_RETENTION_JOB);
        }
        int ticketRetentionDays = settingsDao.getInt("retention.ticket_days", 365);
        int imageRetentionDays = settingsDao.getInt("retention.image_days", 30);

        LocalDateTime ticketCutoff = LocalDateTime.now().minusDays(ticketRetentionDays);
        String ticketCutoffStr = DateFormat.toDbString(ticketCutoff);

        // Expired camera snapshots are files, which cannot take part in a database transaction; they are purged
        // first and the count is recorded in the audit entry below.
        int deletedImages = purgeExpiredSnapshots(imageRetentionDays);

        String updateTicketsSql = "UPDATE tickets SET license_plate = 'UP00XX0000' " +
                "WHERE exit_time IS NOT NULL AND exit_time < ? AND license_plate != 'UP00XX0000';";

        // Ticket anonymisation and its RETENTION_JOB_EXECUTED entry commit together, or neither does. Failures
        // propagate instead of being printed and reported as a successful run.
        int anonymizedTickets = SqliteTransaction.immediate(conn -> {
            int updated;
            try (PreparedStatement ps = conn.prepareStatement(updateTicketsSql)) {
                ps.setString(1, ticketCutoffStr);
                updated = ps.executeUpdate();
            }
            auditLogService.recordInTransactionAsActor(conn, "SYSTEM_RETENTION_JOB", "RETENTION_JOB_EXECUTED",
                    "SYSTEM_JOB", "DATA_RETENTION", "cutoff=" + ticketCutoffStr,
                    "anonymized_tickets=" + updated + ", deleted_images=" + deletedImages,
                    "Statutory DPDP Act 2023 retention cycle completed", "SUCCESS");
            return updated;
        });

        return new RetentionReport(anonymizedTickets, deletedImages, ticketRetentionDays, imageRetentionDays);
    }

    /**
     * Creates a customer personal data deletion request under Section 12(3) of DPDP Act 2023.
     */
    public void requestDeletion(int userId, String username, String mobile) {
        User current = UserSession.getInstance().getCurrentUser().orElse(null);
        if (current != null) {
            AccessControl.checkPermission(Permission.CUSTOMER_REQUEST_DELETION);
            if (current.getUserId() != userId) {
                throw new SecurityException("Access Denied: an erasure request can only be submitted for your own account.");
            }
        }
        String sql = "INSERT INTO deletion_requests (user_id, username, mobile, requested_at, status) " +
                "VALUES (?, ?, ?, ?, 'PENDING');";

        // Request row and DELETION_REQUESTED entry commit together, or neither does.
        SqliteTransaction.immediate(conn -> {
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT COUNT(*) FROM deletion_requests WHERE user_id = ? AND status = 'PENDING';")) {
                check.setInt(1, userId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new IllegalStateException("An erasure request for this account is already pending review.");
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setString(2, username);
                ps.setString(3, mobile);
                ps.setString(4, DateFormat.toDbString(LocalDateTime.now()));
                ps.executeUpdate();
            }
            auditLogService.recordInTransaction(conn, userId, username, "CUSTOMER", "DELETION_REQUESTED",
                    "DPDP_REQUEST", String.valueOf(userId), null, null,
                    "Customer submitted personal data erasure request", "SUCCESS");
            return null;
        });
    }

    public List<DeletionRequest> getPendingDeletionRequests() {
        List<DeletionRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM deletion_requests WHERE status = 'PENDING' ORDER BY id ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new DeletionRequest(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("mobile"),
                        rs.getString("requested_at"),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching deletion requests: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Administrator approves customer data erasure request.
     * Anonymizes customer PII while retaining accounting/tax invoices under GST statutory rules.
     */
    public void approveDeletion(int requestId, String adminUsername) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.MANAGE_USERS);
        }
        String reviewer = UserSession.getInstance().getCurrentUser().map(User::getUsername)
                .orElse(adminUsername != null ? adminUsername : "SYSTEM");

        // Anonymisation, request status and CUSTOMER_DATA_ERASURE_APPROVED commit together, or none does. Only a
        // PENDING request can be approved, so an approval can't be replayed.
        SqliteTransaction.immediate(conn -> {
            int userId;
            String username;
            try (PreparedStatement ps = conn.prepareStatement("SELECT user_id, username, status FROM deletion_requests WHERE id = ?;")) {
                ps.setInt(1, requestId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Erasure request #" + requestId + " not found.");
                    }
                    if (!"PENDING".equals(rs.getString("status"))) {
                        throw new IllegalStateException("Erasure request #" + requestId + " is already " + rs.getString("status") + ".");
                    }
                    userId = rs.getInt("user_id");
                    username = rs.getString("username");
                }
            }

            // 1. Anonymize user account
            try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET full_name = '[DELETED_USER]', email = NULL, phone = NULL, " +
                    "active = 0, shift_or_plate = NULL WHERE user_id = ?;")) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }
            // 2. Clear customer linked vehicles
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM user_vehicles WHERE user_id = ?;")) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }
            // 3. Mark deletion request as APPROVED
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE deletion_requests SET status = 'APPROVED', reviewed_at = ?, reviewed_by = ? WHERE id = ?;")) {
                ps.setString(1, DateFormat.toDbString(LocalDateTime.now()));
                ps.setString(2, reviewer);
                ps.setInt(3, requestId);
                ps.executeUpdate();
            }
            auditLogService.recordInTransactionAsActor(conn, reviewer, "CUSTOMER_DATA_ERASURE_APPROVED", "USER",
                    String.valueOf(userId), username, "[ANONYMIZED]", "Admin approved DPDP erasure request #" + requestId +
                            " (Invoices preserved per GST compliance)", "SUCCESS");
            return null;
        });
    }

    private int purgeExpiredSnapshots(int retentionDays) {
        int deleted = 0;
        File dir = new File("snapshots");
        if (dir.exists() && dir.isDirectory()) {
            long cutoffMillis = System.currentTimeMillis() - ((long) retentionDays * 24 * 3600 * 1000);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.lastModified() < cutoffMillis) {
                        if (f.delete()) {
                            deleted++;
                        }
                    }
                }
            }
        }
        return deleted;
    }

    public record RetentionReport(int ticketsAnonymized, int imagesDeleted, int ticketRetentionDays, int imageRetentionDays) {}
    public record DeletionRequest(int id, int userId, String username, String mobile, String requestedAt, String status) {}
}

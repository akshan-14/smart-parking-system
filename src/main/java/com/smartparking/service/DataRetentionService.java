package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.repository.SystemSettingsDao;
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
        int ticketRetentionDays = settingsDao.getInt("retention.ticket_days", 365);
        int imageRetentionDays = settingsDao.getInt("retention.image_days", 30);

        LocalDateTime ticketCutoff = LocalDateTime.now().minusDays(ticketRetentionDays);
        String ticketCutoffStr = DateFormat.toDbString(ticketCutoff);

        int anonymizedTickets = 0;
        String updateTicketsSql = "UPDATE tickets SET license_plate = 'UP00XX0000' " +
                "WHERE exit_time IS NOT NULL AND exit_time < ? AND license_plate != 'UP00XX0000';";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateTicketsSql)) {
            ps.setString(1, ticketCutoffStr);
            anonymizedTickets = ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Retention error anonymizing tickets: " + e.getMessage());
        }

        // Clean up expired entry camera snapshots
        int deletedImages = purgeExpiredSnapshots(imageRetentionDays);

        RetentionReport report = new RetentionReport(anonymizedTickets, deletedImages, ticketRetentionDays, imageRetentionDays);

        auditLogService.log("RETENTION_JOB_EXECUTED", "SYSTEM_JOB", "DATA_RETENTION",
                "cutoff=" + ticketCutoffStr,
                "anonymized_tickets=" + anonymizedTickets + ", deleted_images=" + deletedImages,
                "Statutory DPDP Act 2023 retention cycle completed", "SUCCESS");

        return report;
    }

    /**
     * Creates a customer personal data deletion request under Section 12(3) of DPDP Act 2023.
     */
    public void requestDeletion(int userId, String username, String mobile) {
        String sql = "INSERT INTO deletion_requests (user_id, username, mobile, requested_at, status) " +
                "VALUES (?, ?, ?, ?, 'PENDING');";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, username);
            ps.setString(3, mobile);
            ps.setString(4, DateFormat.toDbString(LocalDateTime.now()));
            ps.executeUpdate();

            auditLogService.logWithActor(userId, username, "CUSTOMER", "DELETION_REQUESTED",
                    "DPDP_REQUEST", String.valueOf(userId), null, null,
                    "Customer submitted personal data erasure request", "SUCCESS");
        } catch (SQLException e) {
            throw new RuntimeException("Error submitting deletion request: " + e.getMessage(), e);
        }
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
        String selectSql = "SELECT * FROM deletion_requests WHERE id = ?;";
        int userId = -1;
        String username = "";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getInt("user_id");
                    username = rs.getString("username");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        if (userId <= 0) return;

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Anonymize user account
                String anonUserSql = "UPDATE users SET full_name = '[DELETED_USER]', email = NULL, phone = NULL, " +
                        "active = 0, shift_or_plate = NULL WHERE user_id = ?;";
                try (PreparedStatement ps = conn.prepareStatement(anonUserSql)) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }

                // 2. Clear customer linked vehicles
                String clearVehiclesSql = "DELETE FROM user_vehicles WHERE user_id = ?;";
                try (PreparedStatement ps = conn.prepareStatement(clearVehiclesSql)) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }

                // 3. Mark deletion request as APPROVED
                String updateReqSql = "UPDATE deletion_requests SET status = 'APPROVED', reviewed_at = ?, reviewed_by = ? WHERE id = ?;";
                try (PreparedStatement ps = conn.prepareStatement(updateReqSql)) {
                    ps.setString(1, DateFormat.toDbString(LocalDateTime.now()));
                    ps.setString(2, adminUsername);
                    ps.setInt(3, requestId);
                    ps.executeUpdate();
                }

                conn.commit();

                auditLogService.log("CUSTOMER_DATA_ERASURE_APPROVED", "USER", String.valueOf(userId),
                        username, "[ANONYMIZED]", "Admin approved DPDP erasure request #" + requestId +
                                " (Invoices preserved per GST compliance)", "SUCCESS");
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error processing deletion request: " + e.getMessage(), e);
        }
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

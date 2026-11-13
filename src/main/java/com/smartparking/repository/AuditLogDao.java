package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.audit.AuditLogEntry;
import com.smartparking.util.DateFormat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only Data Access Object for System Audit Log.
 * In strict compliance with security standards, this DAO provides ONLY insertion and query methods.
 * No UPDATE or DELETE methods exist in this class to preserve non-repudiation.
 *
 * Implements a basic blockchain-style SHA-256 tamper-evident cryptographic hash chain.
 * Note: Designed as an educational / college-level demonstration of data integrity verification.
 */
public class AuditLogDao {

    /**
     * Records an immutable audit log entry.
     * Computes the SHA-256 hash chaining with the previous entry's hash.
     */
    public synchronized void append(Integer userId, String username, String role, String action,
                                   String entityType, String entityId, String oldValue,
                                   String newValue, String reason, String ipOrTerminal, String result) {
        String timestampStr = DateFormat.toDbString(LocalDateTime.now());
        String prevHash = getLatestRowHash();
        String rowHash = computeRowHash(prevHash, timestampStr, String.valueOf(userId), action, entityType, entityId, oldValue, newValue, reason, result);

        String sql = "INSERT INTO audit_log (timestamp, user_id, username, role, action, entity_type, " +
                "entity_id, old_value, new_value, reason, ip_or_terminal, result, prev_hash, row_hash, performed_by, ticket_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, timestampStr);
            if (userId != null) {
                ps.setInt(2, userId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, username != null ? username : "SYSTEM");
            ps.setString(4, role != null ? role : "SYSTEM");
            ps.setString(5, action);
            ps.setString(6, entityType);
            ps.setString(7, entityId);
            ps.setString(8, oldValue);
            ps.setString(9, newValue);
            ps.setString(10, reason != null ? reason : "Operational Action");
            ps.setString(11, ipOrTerminal != null ? ipOrTerminal : "LOCAL");
            ps.setString(12, result != null ? result : "SUCCESS");
            ps.setString(13, prevHash);
            ps.setString(14, rowHash);
            ps.setString(15, username != null ? username : "SYSTEM");
            ps.setString(16, entityId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("CRITICAL: Failed to write to audit_log: " + e.getMessage());
        }
    }

    /**
     * Backward-compatible overload for legacy calls.
     */
    public void logAction(String action, String performedBy, String ticketId, String oldValue, String newValue, String reason) {
        append(null, performedBy, "ADMIN", action, "TICKET", ticketId, oldValue, newValue, reason, "LOCAL", "SUCCESS");
    }

    /**
     * Retrieves the row_hash of the most recently inserted record.
     */
    public String getLatestRowHash() {
        String sql = "SELECT row_hash FROM audit_log ORDER BY rowid DESC LIMIT 1;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String hash = rs.getString("row_hash");
                if (hash != null && !hash.isBlank()) {
                    return hash;
                }
            }
        } catch (SQLException ignored) {
        }
        return "GENESIS";
    }

    /**
     * Retrieves all audit log entries chronologically.
     */
    public List<AuditLogEntry> findAll() {
        List<AuditLogEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM audit_log ORDER BY rowid DESC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                entries.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading audit logs: " + e.getMessage(), e);
        }
        return entries;
    }

    /**
     * Filtered audit search with pagination.
     */
    public List<AuditLogEntry> findFiltered(String userFilter, String actionFilter, String resultFilter,
                                           String query, int offset, int limit) {
        List<AuditLogEntry> entries = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (userFilter != null && !userFilter.isBlank() && !userFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND LOWER(username) = LOWER(?) ");
            params.add(userFilter.trim());
        }
        if (actionFilter != null && !actionFilter.isBlank() && !actionFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND LOWER(action) = LOWER(?) ");
            params.add(actionFilter.trim());
        }
        if (resultFilter != null && !resultFilter.isBlank() && !resultFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND LOWER(result) = LOWER(?) ");
            params.add(resultFilter.trim());
        }
        if (query != null && !query.isBlank()) {
            sql.append("AND (LOWER(reason) LIKE ? OR LOWER(entity_id) LIKE ? OR LOWER(entity_type) LIKE ?) ");
            String q = "%" + query.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append("ORDER BY rowid DESC LIMIT ? OFFSET ?;");
        params.add(limit);
        params.add(offset);

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error filtering audit logs: " + e.getMessage(), e);
        }
        return entries;
    }

    /**
     * Tamper-evident verification of the entire audit chain.
     * College viva demonstration: recalculates SHA-256 for every record and verifies chain continuity.
     */
    public IntegrityResult verifyIntegrity() {
        String sql = "SELECT * FROM audit_log ORDER BY rowid ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            String expectedPrevHash = "GENESIS";
            int verifiedCount = 0;

            while (rs.next()) {
                int id;
                try {
                    id = rs.getInt("log_id");
                } catch (SQLException e) {
                    id = rs.getInt("id");
                }
                String timestampStr = rs.getString("timestamp");
                int userIdInt = rs.getInt("user_id");
                String userId = rs.wasNull() ? "null" : String.valueOf(userIdInt);
                String action = rs.getString("action");
                String entityType = rs.getString("entity_type");
                String entityId = rs.getString("entity_id");
                String oldValue = rs.getString("old_value");
                String newValue = rs.getString("new_value");
                String reason = rs.getString("reason");
                String result = rs.getString("result");
                String storedPrevHash = rs.getString("prev_hash");
                String storedRowHash = rs.getString("row_hash");

                // 1. Verify prev_hash matches previous record
                if (storedPrevHash == null || !storedPrevHash.equals(expectedPrevHash)) {
                    return new IntegrityResult(false, id, "Chain continuity broken at row #" + id +
                            ". Expected prev_hash: " + expectedPrevHash + ", but found: " + storedPrevHash);
                }

                // 2. Recompute row hash
                String computed = computeRowHash(storedPrevHash, timestampStr, userId, action, entityType, entityId, oldValue, newValue, reason, result);
                if (storedRowHash == null || !storedRowHash.equalsIgnoreCase(computed)) {
                    return new IntegrityResult(false, id, "Data tampering detected at row #" + id +
                            ". Content hash mismatch.");
                }

                expectedPrevHash = storedRowHash;
                verifiedCount++;
            }

            return new IntegrityResult(true, -1, "Audit log verified OK. " + verifiedCount + " records intact with no tampering detected.");
        } catch (SQLException e) {
            return new IntegrityResult(false, -1, "Verification error: " + e.getMessage());
        }
    }

    public static String computeRowHash(String prevHash, String timestamp, String userId,
                                        String action, String entityType, String entityId,
                                        String oldValue, String newValue, String reason, String result) {
        String payload = (prevHash != null ? prevHash : "GENESIS") + "|" +
                (timestamp != null ? timestamp : "") + "|" +
                (userId != null ? userId : "") + "|" +
                (action != null ? action : "") + "|" +
                (entityType != null ? entityType : "") + "|" +
                (entityId != null ? entityId : "") + "|" +
                (oldValue != null ? oldValue : "") + "|" +
                (newValue != null ? newValue : "") + "|" +
                (reason != null ? reason : "") + "|" +
                (result != null ? result : "");
        return sha256(payload);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private AuditLogEntry mapRow(ResultSet rs) throws SQLException {
        int id;
        try {
            id = rs.getInt("log_id");
        } catch (SQLException e) {
            id = rs.getInt("id");
        }
        String timestampStr = rs.getString("timestamp");
        LocalDateTime ts;
        try {
            ts = DateFormat.parseDbDateTime(timestampStr);
        } catch (Exception e) {
            ts = LocalDateTime.now();
        }
        int uId = rs.getInt("user_id");
        Integer userId = rs.wasNull() ? null : uId;
        String username = rs.getString("username");
        String role = rs.getString("role");
        String action = rs.getString("action");
        String entityType = rs.getString("entity_type");
        String entityId = rs.getString("entity_id");
        String oldValue = rs.getString("old_value");
        String newValue = rs.getString("new_value");
        String reason = rs.getString("reason");
        String ip = rs.getString("ip_or_terminal");
        String result = rs.getString("result");
        String prevHash = rs.getString("prev_hash");
        String rowHash = rs.getString("row_hash");

        return new AuditLogEntry(id, ts, userId, username, role, action, entityType,
                entityId, oldValue, newValue, reason, ip, result, prevHash, rowHash);
    }

    public record IntegrityResult(boolean isValid, int brokenRowId, String message) {}
}

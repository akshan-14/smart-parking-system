package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.user.Admin;
import com.smartparking.model.user.Attendant;
import com.smartparking.model.user.Customer;
import com.smartparking.model.user.User;
import com.smartparking.util.DateFormat;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for User entities.
 * Handles persistence, queries, lockout tracking, and credential updates using parameterized queries.
 */
public class UserDao {

    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) return Optional.empty();
        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?);";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying user by username: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<User> findById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying user by ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY user_id ASC;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying all users: " + e.getMessage(), e);
        }
        return users;
    }

    public int save(User user) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            return save(conn, user);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving user: " + e.getMessage(), e);
        }
    }

    /** Inserts the user on the caller's connection (and transaction); returns the new id. */
    public int save(Connection conn, User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, salt, full_name, email, phone, role, " +
                "shift_or_plate, active, failed_attempts, locked_until, created_at, last_login_at, force_password_change) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getSalt() != null ? user.getSalt() : "");
            pstmt.setString(4, user.getFullName());
            pstmt.setString(5, user.getEmail());
            pstmt.setString(6, user.getPhone());
            pstmt.setString(7, user.getRole().name());

            String extra = null;
            if (user instanceof Attendant a) {
                extra = a.getShiftName();
            } else if (user instanceof Customer c) {
                extra = c.getPrimaryLicensePlate();
            }
            pstmt.setString(8, extra);
            pstmt.setInt(9, user.isActive() ? 1 : 0);
            pstmt.setInt(10, user.getFailedAttempts());
            pstmt.setString(11, user.getLockedUntil() != null ? DateFormat.toDbString(user.getLockedUntil()) : null);
            pstmt.setString(12, DateFormat.toDbString(user.getCreatedAt()));
            pstmt.setString(13, user.getLastLoginAt() != null ? DateFormat.toDbString(user.getLastLoginAt()) : null);
            pstmt.setInt(14, user.isForcePasswordChange() ? 1 : 0);

            pstmt.executeUpdate();
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error saving user: " + e.getMessage(), e);
        }
        return 0;
    }

    public void update(User user) {
        String sql = "UPDATE users SET full_name = ?, email = ?, phone = ?, active = ?, " +
                "failed_attempts = ?, locked_until = ?, last_login_at = ?, force_password_change = ? " +
                "WHERE user_id = ?;";
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getFullName());
            pstmt.setString(2, user.getEmail());
            pstmt.setString(3, user.getPhone());
            pstmt.setInt(4, user.isActive() ? 1 : 0);
            pstmt.setInt(5, user.getFailedAttempts());
            pstmt.setString(6, user.getLockedUntil() != null ? DateFormat.toDbString(user.getLockedUntil()) : null);
            pstmt.setString(7, user.getLastLoginAt() != null ? DateFormat.toDbString(user.getLastLoginAt()) : null);
            pstmt.setInt(8, user.isForcePasswordChange() ? 1 : 0);
            pstmt.setInt(9, user.getUserId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating user: " + e.getMessage(), e);
        }
    }

    public void updatePassword(int userId, String newHash) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            updatePassword(conn, userId, newHash);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating password: " + e.getMessage(), e);
        }
    }

    /**
     * Sets an administrator-chosen temporary password: the user must change it at next sign-in
     * (force_password_change = 1). On the caller's connection.
     */
    public void setTemporaryPassword(Connection conn, int userId, String newHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, force_password_change = 1 WHERE user_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newHash);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        }
    }

    /** Sets a new password hash chosen by the user and clears force_password_change, on the caller's connection. */
    public void updatePassword(Connection conn, int userId, String newHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, force_password_change = 0 WHERE user_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newHash);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        }
    }

    public void updateFailedAttempts(int userId, int failedAttempts, LocalDateTime lockedUntil) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            updateFailedAttempts(conn, userId, failedAttempts, lockedUntil);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating failed attempts: " + e.getMessage(), e);
        }
    }

    public void updateFailedAttempts(Connection conn, int userId, int failedAttempts, LocalDateTime lockedUntil) throws SQLException {
        String sql = "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE user_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, failedAttempts);
            pstmt.setString(2, lockedUntil != null ? DateFormat.toDbString(lockedUntil) : null);
            pstmt.setInt(3, userId);
            pstmt.executeUpdate();
        }
    }

    public void updateLastLogin(int userId, LocalDateTime loginTime) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            updateLastLogin(conn, userId, loginTime);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating last login: " + e.getMessage(), e);
        }
    }

    public void updateLastLogin(Connection conn, int userId, LocalDateTime loginTime) throws SQLException {
        String sql = "UPDATE users SET last_login_at = ?, failed_attempts = 0, locked_until = NULL WHERE user_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, DateFormat.toDbString(loginTime));
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        }
    }

    public void setActiveStatus(int userId, boolean active) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            setActiveStatus(conn, userId, active);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating user active status: " + e.getMessage(), e);
        }
    }

    public void setActiveStatus(Connection conn, int userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET active = ? WHERE user_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        }
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        int id = rs.getInt("user_id");
        String username = rs.getString("username");
        String hash = rs.getString("password_hash");
        String salt = rs.getString("salt");
        if (salt == null) salt = "";
        String fullName = rs.getString("full_name");
        String email = rs.getString("email");
        String phone = rs.getString("phone");
        UserRole role = UserRole.valueOf(rs.getString("role"));
        String extra = rs.getString("shift_or_plate");

        boolean active = true;
        try {
            active = rs.getInt("active") == 1;
        } catch (SQLException ignored) {
        }

        int failedAttempts = 0;
        try {
            failedAttempts = rs.getInt("failed_attempts");
        } catch (SQLException ignored) {
        }

        LocalDateTime lockedUntil = null;
        try {
            String lockedStr = rs.getString("locked_until");
            if (lockedStr != null && !lockedStr.isBlank()) {
                lockedUntil = DateFormat.parseDbDateTime(lockedStr);
            }
        } catch (SQLException ignored) {
        }

        LocalDateTime createdAt = LocalDateTime.now();
        try {
            String createdAtStr = rs.getString("created_at");
            if (createdAtStr != null && !createdAtStr.isBlank()) {
                createdAt = DateFormat.parseDbDateTime(createdAtStr);
            }
        } catch (Exception ignored) {
        }

        LocalDateTime lastLoginAt = null;
        try {
            String lastLoginStr = rs.getString("last_login_at");
            if (lastLoginStr != null && !lastLoginStr.isBlank()) {
                lastLoginAt = DateFormat.parseDbDateTime(lastLoginStr);
            }
        } catch (SQLException ignored) {
        }

        boolean forcePasswordChange = false;
        try {
            forcePasswordChange = rs.getInt("force_password_change") == 1;
        } catch (SQLException ignored) {
        }

        return switch (role) {
            case ADMIN -> new Admin(id, username, hash, salt, fullName, email, phone, active,
                    failedAttempts, lockedUntil, createdAt, lastLoginAt, forcePasswordChange);
            case ATTENDANT -> new Attendant(id, username, hash, salt, fullName, email, phone, extra,
                    active, failedAttempts, lockedUntil, createdAt, lastLoginAt, forcePasswordChange);
            case CUSTOMER -> new Customer(id, username, hash, salt, fullName, email, phone, extra,
                    active, failedAttempts, lockedUntil, createdAt, lastLoginAt, forcePasswordChange);
        };
    }
}

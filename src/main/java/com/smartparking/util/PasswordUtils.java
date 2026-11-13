package com.smartparking.util;

import com.smartparking.security.PasswordHasher;

import java.security.SecureRandom;

/**
 * Utility facade for secure password operations.
 * Upgraded in Phase 6 to use BCrypt (cost factor 12) through PasswordHasher.
 */
public final class PasswordUtils {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtils() {
        // Utility class private constructor
    }

    /**
     * Generates a 16-byte cryptographically secure salt string in hex format.
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * Hashes password using BCrypt (cost 12).
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            plainPassword = "";
        }
        return PasswordHasher.hash(plainPassword);
    }

    /**
     * Overloaded hash password. If an explicit salt is provided, uses deterministic SHA-256 for test/legacy compatibility;
     * otherwise delegates to BCrypt.
     */
    public static String hashPassword(String plainPassword, String salt) {
        if (salt != null && !salt.isEmpty()) {
            return PasswordHasher.hashSha256(plainPassword, salt);
        }
        return hashPassword(plainPassword);
    }

    /**
     * Verifies plain text password against salt and expected hash.
     */
    public static boolean verifyPassword(String plainPassword, String salt, String expectedHash) {
        return PasswordHasher.verify(plainPassword, salt, expectedHash);
    }

    /**
     * Verifies plain text password against expected hash.
     */
    public static boolean verifyPassword(String plainPassword, String expectedHash) {
        return PasswordHasher.verify(plainPassword, expectedHash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

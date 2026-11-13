package com.smartparking.security;

import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * BCrypt password hashing and verification utility.
 * Uses BCrypt with work factor (cost) 12 to provide strong cryptographic resistance
 * against dictionary and brute-force attacks.
 * Includes backward compatibility for migrating legacy SHA-256 hashes.
 */
public final class PasswordHasher {

    private static final int BCRYPT_WORK_FACTOR = 12;

    private PasswordHasher() {
        // Utility class private constructor
    }

    /**
     * Hashes a plain-text password using BCrypt with salt cost factor 12.
     * Never stores or returns plain text.
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_WORK_FACTOR));
    }

    /**
     * Verifies a candidate plain password against a stored hash.
     * Supports BCrypt hashes ($2a$, $2b$, $2y$) as well as legacy SHA-256 fallback.
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        try {
            // Check if storedHash is standard BCrypt format
            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                return BCrypt.checkpw(plainPassword, storedHash);
            }

            // Fallback for legacy SHA-256 without salt
            return verifyLegacySha256(plainPassword, "", storedHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies with optional legacy salt.
     */
    public static boolean verify(String plainPassword, String salt, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            return BCrypt.checkpw(plainPassword, storedHash);
        }

        return verifyLegacySha256(plainPassword, salt, storedHash);
    }

    public static String hashSha256(String plainPassword, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedInput = (salt != null ? salt : "") + (plainPassword != null ? plainPassword : "");
            byte[] encodedHash = digest.digest(saltedInput.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static boolean verifyLegacySha256(String plainPassword, String salt, String expectedHash) {
        String computed = hashSha256(plainPassword, salt);
        return computed.equalsIgnoreCase(expectedHash);
    }
}

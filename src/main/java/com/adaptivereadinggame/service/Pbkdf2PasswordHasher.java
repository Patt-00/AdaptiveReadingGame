package com.adaptivereadinggame.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Uses the JDK's PBKDF2-HMAC-SHA256, not a custom password algorithm.
 * Verifiers are pbkdf2-sha256$v1$iterations$base64-salt$base64-hash.
 * Version 1 accepts 600,000..1,000,000 iterations with a 16-byte salt and 32-byte hash;
 * these bounds prevent a malformed database value from requesting unlimited work.
 */
public final class Pbkdf2PasswordHasher implements PasswordHasher {
    public static final int ITERATIONS = 600_000;
    private static final int MAX_ITERATIONS = 1_000_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int MAX_VERIFIER_LENGTH = 160;
    private final SecureRandom random = new SecureRandom();

    @Override public String encode(char[] password) {
        requirePassword(password);
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        try {
            return "pbkdf2-sha256$v1$" + ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    @Override public boolean matches(char[] password, String encodedVerifier) {
        if (password == null || password.length == 0 || encodedVerifier == null
                || encodedVerifier.length() > MAX_VERIFIER_LENGTH) return false;
        String[] parts = encodedVerifier.split("\\$", -1);
        if (parts.length != 5 || !"pbkdf2-sha256".equals(parts[0]) || !"v1".equals(parts[1])
                || !parts[2].matches("[1-9][0-9]{0,6}")) return false;
        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[2]);
            if (iterations < ITERATIONS || iterations > MAX_ITERATIONS) return false;
            salt = canonicalBase64(parts[3], SALT_BYTES);
            expected = canonicalBase64(parts[4], HASH_BYTES);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        byte[] actual = derive(password, salt, iterations);
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
            Arrays.fill(expected, (byte) 0);
        }
    }

    private static byte[] canonicalBase64(String encoded, int bytes) {
        byte[] decoded = Base64.getDecoder().decode(encoded);
        if (decoded.length != bytes || !Base64.getEncoder().encodeToString(decoded).equals(encoded))
            throw new IllegalArgumentException("Invalid verifier field");
        return decoded;
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, HASH_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Password verification is unavailable.", unavailable);
        } finally {
            specification.clearPassword();
        }
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length == 0)
            throw new IllegalArgumentException("A password is required.");
    }
}

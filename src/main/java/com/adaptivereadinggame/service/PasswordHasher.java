package com.adaptivereadinggame.service;

/** Contract only; no plaintext-password or pretend authentication implementation is supplied. */
public interface PasswordHasher {
    String encode(char[] password);
    boolean matches(char[] password, String encodedVerifier);
}

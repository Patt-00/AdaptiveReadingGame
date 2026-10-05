package com.adaptivereadinggame.repository;

import java.util.Optional;
import java.util.UUID;

/** Backend-only boundary. Encoded verifier format is selected with PasswordHasher by Dev 1. */
public interface CredentialRepository {
    Optional<String> findEncodedVerifier(UUID accountId);
    void saveEncodedVerifier(UUID accountId, String encodedVerifier);
}

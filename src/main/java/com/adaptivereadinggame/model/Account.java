package com.adaptivereadinggame.model;

import java.util.Objects;
import java.util.UUID;

/** Public account data only. Credentials must not be exposed to controllers. */
public record Account(UUID id, String username, String displayName) {
    public Account {
        Objects.requireNonNull(id);
        if (username == null || username.isBlank() || displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("Username and display name required");
    }
}

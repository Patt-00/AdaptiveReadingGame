package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.PlayerAppearance;
import com.adaptivereadinggame.repository.PlayerAppearanceRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Account-scoped cosmetic selection; controllers cannot select for another learner. */
public final class PlayerAppearanceService {
    private final AuthService auth;
    private final PlayerAppearanceRepository appearances;
    public PlayerAppearanceService(AuthService auth, PlayerAppearanceRepository appearances) {
        this.auth = Objects.requireNonNull(auth);
        this.appearances = Objects.requireNonNull(appearances);
    }
    public Optional<PlayerAppearance> current() { return appearances.find(accountId()); }
    public PlayerAppearance save(PlayerAppearance appearance) {
        return appearances.save(accountId(), Objects.requireNonNull(appearance));
    }
    private UUID accountId() {
        return auth.currentAccount().orElseThrow(() -> new IllegalStateException("Sign in before choosing an appearance.")).id();
    }
}

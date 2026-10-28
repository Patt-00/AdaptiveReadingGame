package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.PlayerAppearance;
import java.util.Optional;
import java.util.UUID;

/** Stores one cosmetic appearance per account. */
public interface PlayerAppearanceRepository {
    Optional<PlayerAppearance> find(UUID accountId);
    PlayerAppearance save(UUID accountId, PlayerAppearance appearance);
}

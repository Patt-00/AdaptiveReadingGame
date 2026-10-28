package com.adaptivereadinggame.repository.memory;

import com.adaptivereadinggame.model.PlayerAppearance;
import com.adaptivereadinggame.repository.PlayerAppearanceRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPlayerAppearanceRepository implements PlayerAppearanceRepository {
    private final ConcurrentHashMap<UUID, PlayerAppearance> appearances = new ConcurrentHashMap<>();
    @Override public Optional<PlayerAppearance> find(UUID accountId) {
        return Optional.ofNullable(appearances.get(Objects.requireNonNull(accountId)));
    }
    @Override public PlayerAppearance save(UUID accountId, PlayerAppearance appearance) {
        appearances.put(Objects.requireNonNull(accountId), Objects.requireNonNull(appearance));
        return appearance;
    }
}

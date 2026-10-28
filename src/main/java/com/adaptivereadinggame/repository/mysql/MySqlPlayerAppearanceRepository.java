package com.adaptivereadinggame.repository.mysql;

import com.adaptivereadinggame.model.PlayerAppearance;
import com.adaptivereadinggame.repository.PlayerAppearanceRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MySqlPlayerAppearanceRepository implements PlayerAppearanceRepository {
    private final ConnectionProvider connections;
    public MySqlPlayerAppearanceRepository(ConnectionProvider connections) { this.connections = Objects.requireNonNull(connections); }

    @Override public Optional<PlayerAppearance> find(UUID accountId) {
        Objects.requireNonNull(accountId);
        return SqlSupport.read(connections, "player appearance lookup", connection -> {
            try (var statement = connection.prepareStatement("SELECT appearance FROM player_appearances WHERE account_id = ?")) {
                statement.setString(1, SqlSupport.id(accountId));
                try (var rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(PlayerAppearance.valueOf(rows.getString(1))) : Optional.empty();
                }
            }
        });
    }

    @Override public PlayerAppearance save(UUID accountId, PlayerAppearance appearance) {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(appearance);
        return SqlSupport.transaction(connections, "player appearance selection", connection -> {
            try (var insert = connection.prepareStatement("INSERT INTO player_appearances(account_id, appearance) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE appearance = VALUES(appearance)")) {
                insert.setString(1, SqlSupport.id(accountId));
                insert.setString(2, appearance.name());
                insert.executeUpdate();
            }
            try (var read = connection.prepareStatement("SELECT appearance FROM player_appearances WHERE account_id = ?")) {
                read.setString(1, SqlSupport.id(accountId));
                try (var rows = read.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("The selected appearance was not stored.");
                    return PlayerAppearance.valueOf(rows.getString(1));
                }
            }
        });
    }
}

package com.adaptivereadinggame.repository.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/** Simple desktop-app connection provider; a connection is not shared across operations. */
public final class DriverManagerConnectionProvider implements ConnectionProvider {
    private final MySqlConfig config;

    public DriverManagerConnectionProvider(MySqlConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override public Connection open() throws SQLException {
        return DriverManager.getConnection(config.url(), config.username(), config.password());
    }
}

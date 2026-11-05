package com.adaptivereadinggame.repository.mysql;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** MySQL settings read from environment variables; credentials are never checked in. */
public record MySqlConfig(String url, String username, String password) {
    public MySqlConfig {
        if (url == null || !url.startsWith("jdbc:mysql://"))
            throw new IllegalArgumentException("ARG_DB_URL must be a MySQL JDBC URL.");
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("ARG_DB_USER is required when MySQL is configured.");
        if (password == null)
            throw new IllegalArgumentException("ARG_DB_PASSWORD is required when MySQL is configured.");
    }

    /** No URL means the app may use its clearly-labeled in-memory development mode. */
    public static Optional<MySqlConfig> fromEnvironment() {
        return fromEnvironment(System.getenv(), MySqlConfigStore.defaultPath());
    }

    static Optional<MySqlConfig> fromEnvironment(Map<String, String> environment, Path configFile) {
        String url = environment.get("ARG_DB_URL");
        String username = environment.get("ARG_DB_USER");
        String password = environment.get("ARG_DB_PASSWORD");
        boolean any = (url != null && !url.isBlank()) || (username != null && !username.isBlank()) || password != null;
        if (!any) return MySqlConfigStore.load(configFile);
        if (url == null || url.isBlank())
            throw new IllegalStateException("ARG_DB_URL is required when any MySQL setting is provided.");
        return Optional.of(new MySqlConfig(url, username, password));
    }

    @Override public String toString() {
        return "MySqlConfig[url=" + url + ", username=" + username + ", password=<redacted>]";
    }
}

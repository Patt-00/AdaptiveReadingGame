package com.adaptivereadinggame.repository.mysql;

import java.nio.file.Path;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;

/** MySQL settings read from environment variables; credentials are never checked in. */
public record MySqlConfig(String url, String username, String password) {
    public MySqlConfig {
        if (url == null || !url.startsWith("jdbc:mysql://"))
            throw new IllegalArgumentException("ARG_DB_URL must be a MySQL JDBC URL.");
        rejectEmbeddedCredentials(url);
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

    static void rejectEmbeddedCredentials(String jdbcUrl) {
        final URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("ARG_DB_URL is not a valid MySQL JDBC URL.");
        }
        if (uri.getUserInfo() != null)
            throw new IllegalArgumentException("Do not put a username or password in ARG_DB_URL; provide them separately.");
        String query = uri.getRawQuery();
        if (query == null) return;
        Set<String> credentialProperties = Set.of("user", "username", "password", "password1", "password2", "password3");
        try {
            for (String property : query.split("[&;]")) {
                String rawName = property.split("=", 2)[0];
                String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (credentialProperties.contains(name))
                    throw new IllegalArgumentException("Do not put a username or password in ARG_DB_URL; provide them separately.");
            }
        } catch (IllegalArgumentException malformed) {
            if (malformed.getMessage() != null && malformed.getMessage().startsWith("Do not put")) throw malformed;
            throw new IllegalArgumentException("ARG_DB_URL contains an invalid query property.");
        }
    }

    @Override public String toString() {
        return "MySqlConfig[url=<redacted>, username=" + username + ", password=<redacted>]";
    }
}

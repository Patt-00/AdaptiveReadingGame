package com.adaptivereadinggame.repository.mysql;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Parsed local-only MySQL target used by first-run database provisioning. */
record MySqlLocalEndpoint(String host, int port, String database, String query) {
    static MySqlLocalEndpoint fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://"))
            throw new IllegalArgumentException("Enter a MySQL JDBC URL such as jdbc:mysql://127.0.0.1:3306/adaptive_reading.");
        final URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("The local MySQL JDBC URL is invalid.", malformed);
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("The local MySQL URL must include a host and cannot include credentials or a fragment.");
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        host = host.toLowerCase(Locale.ROOT);
        if (!(host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")))
            throw new IllegalArgumentException("Local database setup accepts only localhost, 127.0.0.1, or ::1.");

        int port = uri.getPort() < 0 ? 3306 : uri.getPort();
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("The MySQL port must be between 1 and 65535.");

        String path = uri.getPath();
        String database = path == null ? "" : path.startsWith("/") ? path.substring(1) : path;
        if (!database.matches("[A-Za-z0-9_]{1,64}"))
            throw new IllegalArgumentException("The URL must name a database using letters, digits, or underscores.");
        return new MySqlLocalEndpoint(host, port, database, uri.getRawQuery());
    }

    String administratorUrl() {
        String urlHost = host.contains(":") ? "[" + host + "]" : host;
        return "jdbc:mysql://" + urlHost + ":" + port + "/" + (query == null ? "" : "?" + query);
    }

    String accountHost() {
        return Objects.equals(host, "localhost") ? "localhost" : host;
    }
}

package com.adaptivereadinggame.repository.mysql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Applies checked-in additive schema migrations when explicitly invoked. */
public final class MySqlSchemaMigrator {
    private record Migration(int version, String description, String resource) {}
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "initial schema", "/db/migrations/V001__initial_schema.sql"),
            new Migration(2, "content feedback and assets", "/db/migrations/V002__content_feedback_and_assets.sql"),
            new Migration(3, "difficulty decision source", "/db/migrations/V003__difficulty_decision_source.sql"),
            new Migration(4, "adaptive story checkpoints and assets", "/db/migrations/V004__adaptive_story_checkpoints_and_assets.sql"),
            new Migration(5, "player appearance", "/db/migrations/V005__player_appearance.sql"),
            new Migration(6, "chapter availability for new games", "/db/migrations/V006__chapter_availability.sql"));

    private final ConnectionProvider connections;

    public MySqlSchemaMigrator(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    /** DDL is not transactional in MySQL; recorded migration versions are skipped on later runs. */
    public void migrate() {
        try (Connection connection = connections.open()) {
            createVersionTable(connection);
            for (Migration migration : MIGRATIONS) {
                if (alreadyApplied(connection, migration.version())) continue;
                apply(connection, migration);
                recordApplied(connection, migration);
            }
        } catch (SQLException failure) {
            throw new RepositoryException("Could not apply the MySQL schema migrations.", failure);
        }
    }

    private static void createVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations ("
                    + "version INT NOT NULL PRIMARY KEY, description VARCHAR(200) NOT NULL, "
                    + "applied_at DATETIME(6) NOT NULL) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4");
        }
    }

    private static boolean alreadyApplied(Connection connection, int version) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE version = ?")) {
            statement.setInt(1, version);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static void apply(Connection connection, Migration migration) throws SQLException {
        String sql = readResource(migration.resource());
        try (Statement statement = connection.createStatement()) {
            for (String part : splitStatements(sql)) statement.execute(part);
        } catch (SQLException failure) {
            throw new SQLException("Migration " + migration.version() + " failed.", failure);
        }
    }

    private static void recordApplied(Connection connection, Migration migration) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO schema_migrations(version, description, applied_at) VALUES (?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setObject(3, LocalDateTime.now(ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private static String readResource(String name) {
        try (InputStream stream = MySqlSchemaMigrator.class.getResourceAsStream(name)) {
            if (stream == null) throw new IllegalStateException("Migration resource is missing: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read a database migration.", failure);
        }
    }

    private static List<String> splitStatements(String source) {
        String withoutLineComments = source.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (left, right) -> left + right + "\n");
        return java.util.Arrays.stream(withoutLineComments.split(";"))
                .map(String::trim).filter(statement -> !statement.isEmpty()).toList();
    }

    public static void main(String[] args) {
        MySqlConfig config = MySqlConfig.fromEnvironment().orElseThrow(() ->
                new IllegalStateException("Set ARG_DB_URL, ARG_DB_USER, and ARG_DB_PASSWORD first."));
        new MySqlSchemaMigrator(new DriverManagerConnectionProvider(config)).migrate();
        System.out.println("MySQL schema migrations are up to date.");
    }
}

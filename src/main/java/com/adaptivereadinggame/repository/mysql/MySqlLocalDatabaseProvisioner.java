package com.adaptivereadinggame.repository.mysql;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Provisions one local application schema and user, then applies the project migrations. */
public final class MySqlLocalDatabaseProvisioner {
    private static final String APPLICATION_PRIVILEGES =
            "SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES";

    private MySqlLocalDatabaseProvisioner() {}

    public static MySqlConfig provision(String jdbcUrl, String administratorUsername,
            char[] administratorPassword, String applicationUsername, char[] applicationPassword,
            Path configFile) {
        MySqlLocalEndpoint endpoint = MySqlLocalEndpoint.fromJdbcUrl(jdbcUrl);
        validateUsername(administratorUsername, "administrator");
        validateUsername(applicationUsername, "application");
        if (applicationUsername.equalsIgnoreCase("root")
                || applicationUsername.equalsIgnoreCase("mysql")
                || applicationUsername.equalsIgnoreCase(administratorUsername)) {
            throw new IllegalArgumentException("Choose a separate application database username.");
        }
        if (administratorPassword == null || applicationPassword == null || applicationPassword.length == 0)
            throw new IllegalArgumentException("Database passwords cannot be null, and the application password cannot be empty.");

        String administratorSecret = new String(administratorPassword);
        String applicationSecret = new String(applicationPassword);
        MySqlConfig applicationConfig = new MySqlConfig(jdbcUrl, applicationUsername, applicationSecret);
        boolean applicationAccountCreated = false;
        try {
            executeJdbcProvisioning(endpoint, administratorUsername, administratorSecret,
                    applicationUsername, applicationSecret);
            applicationAccountCreated = true;
            verifyApplicationConnection(applicationConfig);
            new MySqlSchemaMigrator(new DriverManagerConnectionProvider(applicationConfig)).migrate();
            MySqlConfigStore.save(configFile, applicationConfig);
            return applicationConfig;
        } catch (SQLException | RuntimeException failure) {
            if (applicationAccountCreated) {
                try {
                    dropJdbcApplicationAccount(endpoint, administratorUsername, administratorSecret, applicationUsername);
                } catch (SQLException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (failure instanceof IllegalArgumentException invalid) throw invalid;
            if (failure instanceof RepositoryException migrationFailure) throw migrationFailure;
            throw new IllegalStateException(
                    "Could not configure the local database. Check the server, administrator credentials, and permissions.",
                    failure);
        } finally {
            Arrays.fill(administratorPassword, '\0');
            Arrays.fill(applicationPassword, '\0');
        }
    }

    public static MySqlConfig provisionUsingSocket(String jdbcUrl, String applicationUsername,
            char[] applicationPassword, Path configFile) {
        MySqlLocalEndpoint endpoint = MySqlLocalEndpoint.fromJdbcUrl(jdbcUrl);
        validateUsername(applicationUsername, "application");
        if (applicationUsername.equalsIgnoreCase("root") || applicationUsername.equalsIgnoreCase("mysql")
                || applicationPassword == null || applicationPassword.length == 0)
            throw new IllegalArgumentException("Choose a separate application username and a non-empty password.");

        String applicationSecret = new String(applicationPassword);
        MySqlConfig applicationConfig = new MySqlConfig(jdbcUrl, applicationUsername, applicationSecret);
        boolean applicationAccountCreated = false;
        try {
            String sqlMode = querySocketSqlMode();
            ensureSocketApplicationAccountDoesNotExist(endpoint, applicationUsername, sqlMode);
            List<String> statements = provisioningStatements(endpoint, applicationUsername, applicationSecret, sqlMode);
            runLocalSocketClient(statements.get(0) + ";\n");
            applicationAccountCreated = true;
            runLocalSocketClient(String.join(";\n", statements.subList(1, statements.size())) + ";\n");
            verifyApplicationConnection(applicationConfig);
            new MySqlSchemaMigrator(new DriverManagerConnectionProvider(applicationConfig)).migrate();
            MySqlConfigStore.save(configFile, applicationConfig);
            return applicationConfig;
        } catch (SQLException | IOException | RuntimeException failure) {
            if (applicationAccountCreated) {
                try {
                    dropSocketApplicationAccount(endpoint, applicationUsername, "");
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (failure instanceof IllegalArgumentException invalid) throw invalid;
            if (failure instanceof RepositoryException migrationFailure) throw migrationFailure;
            throw new IllegalStateException("Could not configure the local database through its local socket.", failure);
        } finally {
            Arrays.fill(applicationPassword, '\0');
        }
    }

    private static void executeJdbcProvisioning(MySqlLocalEndpoint endpoint, String administratorUsername,
            String administratorPassword, String applicationUsername, String applicationPassword) throws SQLException {
        try (Connection connection = DriverManager.getConnection(endpoint.administratorUrl(), administratorUsername,
                administratorPassword); Statement statement = connection.createStatement()) {
            String sqlMode;
            try (var rows = statement.executeQuery("SELECT @@SESSION.sql_mode")) {
                sqlMode = rows.next() ? rows.getString(1) : "";
            }
            List<String> statements = provisioningStatements(endpoint, applicationUsername, applicationPassword, sqlMode);
            try {
                statement.execute(statements.get(0));
            } catch (SQLException duplicateOrInvalid) {
                if (duplicateOrInvalid.getErrorCode() == 1396) {
                    throw new IllegalArgumentException(
                            "The application account for this local host already exists. Choose a new username; setup will not modify an existing account.",
                            duplicateOrInvalid);
                }
                throw duplicateOrInvalid;
            }

            try {
                for (String sql : statements.subList(1, statements.size())) statement.execute(sql);
            } catch (SQLException | RuntimeException failure) {
                try { statement.execute("DROP USER IF EXISTS " + applicationAccount(endpoint, applicationUsername, sqlMode)); }
                catch (SQLException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }
    }

    static List<String> provisioningStatements(MySqlLocalEndpoint endpoint, String applicationUsername,
            String applicationPassword, String sqlMode) {
        validateUsername(applicationUsername, "application");
        if (applicationUsername.equalsIgnoreCase("root"))
            throw new IllegalArgumentException("Choose a separate application database username.");
        String database = quoteIdentifier(endpoint.database());
        String appAccount = applicationAccount(endpoint, applicationUsername, sqlMode);
        String password = quoteString(applicationPassword, sqlMode);
        return List.of(
                "CREATE USER " + appAccount + " IDENTIFIED BY " + password,
                "CREATE DATABASE IF NOT EXISTS " + database + " CHARACTER SET utf8mb4",
                "GRANT " + APPLICATION_PRIVILEGES + " ON " + database + ".* TO " + appAccount);
    }

    private static String applicationAccount(MySqlLocalEndpoint endpoint, String username, String sqlMode) {
        return quoteString(username, sqlMode) + "@" + quoteString(endpoint.accountHost(), sqlMode);
    }

    private static void ensureSocketApplicationAccountDoesNotExist(
            MySqlLocalEndpoint endpoint, String username, String sqlMode) throws IOException {
        String query = "SELECT COUNT(*) FROM mysql.user WHERE User = " + quoteString(username, sqlMode)
                + " AND Host = " + quoteString(endpoint.accountHost(), sqlMode) + ";\n";
        String count = runLocalSocketClient(query).trim();
        if (!"0".equals(count))
            throw new IllegalArgumentException(
                    "The application account for this local host already exists. Choose a new username; setup will not modify an existing account.");
    }

    private static void dropSocketApplicationAccount(
            MySqlLocalEndpoint endpoint, String username, String sqlMode) throws IOException {
        runLocalSocketClient("DROP USER IF EXISTS " + applicationAccount(endpoint, username, sqlMode) + ";\n");
    }

    private static void dropJdbcApplicationAccount(MySqlLocalEndpoint endpoint, String administratorUsername,
            String administratorPassword, String applicationUsername) throws SQLException {
        try (Connection connection = DriverManager.getConnection(endpoint.administratorUrl(),
                administratorUsername, administratorPassword); Statement statement = connection.createStatement()) {
            String sqlMode;
            try (var rows = statement.executeQuery("SELECT @@SESSION.sql_mode")) {
                sqlMode = rows.next() ? rows.getString(1) : "";
            }
            statement.execute("DROP USER IF EXISTS " + applicationAccount(endpoint, applicationUsername, sqlMode));
        }
    }

    private static String querySocketSqlMode() throws IOException {
        return runLocalSocketClient("SELECT @@SESSION.sql_mode;\n").trim();
    }

    private static String runLocalSocketClient(String sql) throws IOException {
        String client = System.getenv().getOrDefault("ARG_DB_ADMIN_CLIENT", "mariadb");
        if (!client.equals("mariadb") && !client.equals("mysql"))
            throw new IllegalArgumentException("ARG_DB_ADMIN_CLIENT must be mariadb or mysql.");
        List<String> command = new ArrayList<>();
        if (Boolean.parseBoolean(System.getenv().getOrDefault("ARG_DB_ADMIN_USE_SUDO", "false")))
            command.add("sudo");
        command.add(client);
        command.addAll(List.of("--no-defaults", "--protocol=socket", "--user=root", "--batch", "--skip-column-names"));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true).start();
        try (OutputStream input = process.getOutputStream()) {
            input.write(sql.getBytes(StandardCharsets.UTF_8));
        }
        final boolean completed;
        try {
            completed = process.waitFor(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Waiting for the local MariaDB socket client was interrupted.", interrupted);
        }
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("The local MariaDB socket client timed out.");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0)
            throw new IOException("The local MariaDB socket client failed with exit code " + process.exitValue() + ".");
        return output.lines().filter(line -> !line.isBlank()).reduce((first, second) -> second).orElse("");
    }

    private static void verifyApplicationConnection(MySqlConfig config) throws SQLException {
        try (Connection connection = new DriverManagerConnectionProvider(config).open();
             Statement statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT 1")) {
            if (!rows.next() || rows.getInt(1) != 1)
                throw new SQLException("The application database connection did not validate.");
        }
    }

    private static void validateUsername(String username, String kind) {
        if (username == null || !username.matches("[A-Za-z0-9_]{1,32}"))
            throw new IllegalArgumentException("The " + kind + " username must contain 1 to 32 letters, digits, or underscores.");
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_]{1,64}"))
            throw new IllegalArgumentException("The database name must contain 1 to 64 letters, digits, or underscores.");
        return "`" + identifier + "`";
    }

    static String quoteString(String value, String sqlMode) {
        if (value.indexOf('\0') >= 0 || value.indexOf('\u001a') >= 0)
            throw new IllegalArgumentException("Database credentials cannot contain NUL or substitute characters.");
        boolean noBackslashEscapes = sqlMode != null && sqlMode.toUpperCase(Locale.ROOT).contains("NO_BACKSLASH_ESCAPES");
        String escaped;
        if (noBackslashEscapes) {
            escaped = value.replace("'", "''");
        } else {
            escaped = value.replace("\\", "\\\\").replace("'", "\\'")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("\b", "\\b");
        }
        return "'" + escaped + "'";
    }
}

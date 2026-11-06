package com.adaptivereadinggame.repository.mysql;

import java.io.Console;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/** First-run wizard for creating and migrating a per-device local database. */
public final class MySqlLocalDatabaseSetup {
    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3306/adaptive_reading";
    private static final String DEFAULT_ADMIN_USER = "root";
    private static final String DEFAULT_APPLICATION_USER = "adaptive_game";
    private static final SecureRandom CREDENTIAL_RANDOM = new SecureRandom();
    static record GeneratedApplicationCredentials(String username, char[] password) {
        @Override public String toString() {
            return "GeneratedApplicationCredentials[username=" + username + ", password=<redacted>]";
        }
    }

    private MySqlLocalDatabaseSetup() {}

    public static String usage() {
        return "Usage: MySqlLocalDatabaseSetup [--automatic] [--reconfigure | --check-only] [--help]\n"
                + "Create a local MySQL database, application user, and schema.\n"
                + "--automatic uses the local default URL and generates the app username and password.";
    }

    public static void main(String[] args) {
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        int status;
        try (PrintStream error = new PrintStream(errorOutput, true, StandardCharsets.UTF_8)) {
            status = run(args, System.console(), System.out, error);
        }
        if (status != 0) {
            String detail = errorOutput.toString(StandardCharsets.UTF_8).trim();
            throw new IllegalStateException(detail.isEmpty()
                    ? "Local database setup failed with exit code " + status + "."
                    : detail);
        }
    }

    static int run(String[] args, Console console, PrintStream output, PrintStream error) {
        return run(args, console, output, error, MySqlConfigStore.defaultPath(), System.getenv());
    }

    static int run(String[] args, Console console, PrintStream output, PrintStream error,
            Path configFile, Map<String, String> environment) {
        boolean reconfigure = false;
        boolean checkOnly = false;
        boolean automatic = false;
        for (String argument : args) {
            if ("--help".equals(argument) || "-h".equals(argument)) {
                output.println(usage());
                return 0;
            }
            if ("--reconfigure".equals(argument)) {
                reconfigure = true;
                continue;
            }
            if ("--check-only".equals(argument)) {
                checkOnly = true;
                continue;
            }
            if ("--automatic".equals(argument)) {
                automatic = true;
                continue;
            }
            error.println(usage());
            return 2;
        }
        if (reconfigure && checkOnly) {
            error.println("Choose either --reconfigure or --check-only, not both.");
            return 2;
        }
        if (reconfigure && hasDatabaseEnvironment(environment)) {
            error.println("Unset ARG_DB_URL, ARG_DB_USER, and ARG_DB_PASSWORD before reconfiguring; they override saved settings.");
            return 2;
        }

        try {
            if (checkOnly) {
                var configured = MySqlConfig.fromEnvironment(environment, configFile);
                if (configured.isPresent()) {
                    validateLocalConfiguration(configured.orElseThrow());
                    output.println("Persistent local database settings are configured.");
                } else {
                    output.println("No local database settings found; the game will use temporary in-memory storage.");
                }
                return 0;
            }
            if (!reconfigure && hasDatabaseEnvironment(environment)) {
                MySqlConfig config = MySqlConfig.fromEnvironment(environment, configFile).orElseThrow();
                migrate(config);
                MySqlConfigStore.save(configFile, config);
                output.println("Local database connection was checked and migrations are current.");
                return 0;
            }
            if (!reconfigure && Files.isRegularFile(configFile)) {
                MySqlConfig config = MySqlConfigStore.load(configFile).orElseThrow();
                migrate(config);
                output.println("Saved local database connection is ready and migrations are current.");
                return 0;
            }
            if (automatic) {
                configureAutomatically(output, configFile, environment, console);
                return 0;
            }
            if (console == null)
                throw new IllegalStateException("Run setup in an interactive terminal to enter database credentials.");
            configure(console, output, configFile, environment);
            return 0;
        } catch (RuntimeException failure) {
            printSafeFailure(error, failure);
            return 1;
        }
    }

    static void printSafeFailure(PrintStream error, RuntimeException failure) {
        String message = failure.getMessage();
        error.println("Local database setup failed: "
                + (message == null || message.isBlank() ? failure.getClass().getSimpleName() : message));
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                String sqlState = sql.getSQLState() == null ? "unknown" : sql.getSQLState();
                error.println("Database server response: SQLState " + sqlState
                        + ", error code " + sql.getErrorCode() + ".");
                if ("28000".equals(sqlState) && sql.getErrorCode() == 1045) {
                    error.println("MySQL did not accept the administrator credentials. Check the entered username and password.");
                }
                return;
            }
            if (cause instanceof IOException) {
                error.println("A local setup file could not be read or written.");
                return;
            }
        }
    }

    static GeneratedApplicationCredentials generateApplicationCredentials() {
        byte[] usernameEntropy = new byte[8];
        byte[] passwordEntropy = new byte[24];
        CREDENTIAL_RANDOM.nextBytes(usernameEntropy);
        CREDENTIAL_RANDOM.nextBytes(passwordEntropy);
        try {
            String username = "adaptive_game_" + HexFormat.of().formatHex(usernameEntropy);
            char[] password = Base64.getUrlEncoder().withoutPadding().encodeToString(passwordEntropy).toCharArray();
            return new GeneratedApplicationCredentials(username, password);
        } finally {
            Arrays.fill(usernameEntropy, (byte) 0);
            Arrays.fill(passwordEntropy, (byte) 0);
        }
    }

    private static void configureAutomatically(PrintStream output, Path configFile,
            Map<String, String> environment, Console console) {
        String jdbcUrl = environment.getOrDefault("ARG_DB_SETUP_URL", DEFAULT_URL);
        MySqlLocalEndpoint.fromJdbcUrl(jdbcUrl);
        GeneratedApplicationCredentials generated = generateApplicationCredentials();
        char[] applicationPassword = generated.password();
        try {
            if ("socket".equalsIgnoreCase(environment.get("ARG_DB_ADMIN_MODE"))) {
                MySqlLocalDatabaseProvisioner.provisionUsingSocket(
                        jdbcUrl, generated.username(), applicationPassword, configFile);
            } else {
                String administratorUser = environment.getOrDefault("ARG_DB_ADMIN_USER", DEFAULT_ADMIN_USER);
                String passwordOverride = environment.get("ARG_DB_ADMIN_PASSWORD");
                char[] administratorPassword = passwordOverride == null
                        ? console == null ? null : console.readPassword("Local MySQL administrator password: ")
                        : passwordOverride.toCharArray();
                if (administratorPassword == null)
                    throw new IllegalStateException("Run setup in an interactive terminal to enter the MySQL administrator password.");
                try {
                    MySqlLocalDatabaseProvisioner.provision(jdbcUrl, administratorUser, administratorPassword,
                            generated.username(), applicationPassword, configFile);
                } finally {
                    Arrays.fill(administratorPassword, '\0');
                }
            }
            output.println("The game database user was generated and its connection settings were saved locally.");
        } finally {
            Arrays.fill(applicationPassword, '\0');
        }
    }

    private static boolean hasDatabaseEnvironment(Map<String, String> environment) {
        return environment.keySet().stream().anyMatch(name ->
                name.equals("ARG_DB_URL") || name.equals("ARG_DB_USER") || name.equals("ARG_DB_PASSWORD"));
    }

    private static void configure(Console console, PrintStream output, Path configFile, Map<String, String> environment) {
        output.println("Database setup creates a local schema and a separate application user.");
        String jdbcUrl = readLine(console, "Local JDBC URL", DEFAULT_URL);
        String applicationUser = readLine(console, "Application database username", DEFAULT_APPLICATION_USER);
        char[] applicationPassword = console.readPassword("Application database password: ");
        if (applicationPassword == null || applicationPassword.length == 0) {
            if (applicationPassword != null) Arrays.fill(applicationPassword, '\0');
            throw new IllegalArgumentException("The application database password cannot be empty.");
        }

        if ("socket".equalsIgnoreCase(environment.get("ARG_DB_ADMIN_MODE"))) {
            MySqlLocalDatabaseProvisioner.provisionUsingSocket(jdbcUrl, applicationUser, applicationPassword, configFile);
        } else {
            String administratorDefault = environment.getOrDefault("ARG_DB_ADMIN_USER", DEFAULT_ADMIN_USER);
            String administratorUser = readLine(console, "Local MySQL administrator username", administratorDefault);
            String administratorPasswordOverride = environment.get("ARG_DB_ADMIN_PASSWORD");
            char[] administratorPassword = administratorPasswordOverride == null
                    ? console.readPassword("Local MySQL administrator password (blank if not set): ")
                    : administratorPasswordOverride.toCharArray();
            if (administratorPassword == null) {
                Arrays.fill(applicationPassword, '\0');
                throw new IllegalStateException("No administrator password was entered.");
            }
            MySqlLocalDatabaseProvisioner.provision(
                    jdbcUrl, administratorUser, administratorPassword, applicationUser, applicationPassword, configFile);
        }
        output.println("Local database and schema are ready.");
        output.println("Application connection settings were saved to " + configFile + ".");
    }

    private static String readLine(Console console, String prompt, String defaultValue) {
        String value = console.readLine("%s [%s]: ", prompt, defaultValue);
        if (value == null) throw new IllegalStateException("Setup input ended before the database settings were complete.");
        return value.isBlank() ? defaultValue : value.trim();
    }

    private static void migrate(MySqlConfig config) {
        validateLocalConfiguration(config);
        new MySqlSchemaMigrator(new DriverManagerConnectionProvider(config)).migrate();
    }

    static void validateLocalConfiguration(MySqlConfig config) {
        MySqlLocalEndpoint.fromJdbcUrl(config.url());
    }
}

package com.adaptivereadinggame.repository.mysql;

import java.io.Console;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/** First-run wizard for creating and migrating a per-device local database. */
public final class MySqlLocalDatabaseSetup {
    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3306/adaptive_reading";
    private static final String DEFAULT_ADMIN_USER = "root";
    private static final String DEFAULT_APPLICATION_USER = "adaptive_game";

    private MySqlLocalDatabaseSetup() {}

    public static String usage() {
        return "Usage: MySqlLocalDatabaseSetup [--reconfigure | --check-only] [--help]\n"
                + "Create a local MySQL database, application user, and schema.\n"
                + "The wizard prompts for the local URL, usernames, and hidden passwords.";
    }

    public static void main(String[] args) {
        int status = run(args, System.console(), System.out, System.err);
        if (status != 0) System.exit(status);
    }

    static int run(String[] args, Console console, PrintStream output, PrintStream error) {
        return run(args, console, output, error, MySqlConfigStore.defaultPath(), System.getenv());
    }

    static int run(String[] args, Console console, PrintStream output, PrintStream error,
            Path configFile, Map<String, String> environment) {
        boolean reconfigure = false;
        boolean checkOnly = false;
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
                if (MySqlConfig.fromEnvironment(environment, configFile).isPresent()) {
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
            if (console == null)
                throw new IllegalStateException("Run setup in an interactive terminal to enter database credentials.");
            configure(console, output, configFile, environment);
            return 0;
        } catch (RuntimeException failure) {
            error.println("Local database setup failed: " + failure.getMessage());
            return 1;
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
            String administratorUser = readLine(console, "Local MySQL administrator username", DEFAULT_ADMIN_USER);
            char[] administratorPassword = console.readPassword("Local MySQL administrator password (blank if not set): ");
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
        new MySqlSchemaMigrator(new DriverManagerConnectionProvider(config)).migrate();
    }
}

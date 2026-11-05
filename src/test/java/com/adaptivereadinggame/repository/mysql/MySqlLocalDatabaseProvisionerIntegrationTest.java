package com.adaptivereadinggame.repository.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "arg.mysqlLocalSetupTests", matches = "true")
class MySqlLocalDatabaseProvisionerIntegrationTest {
    @TempDir Path temporary;

    @AfterEach void removeDisposableSchemaAndUser() throws Exception {
        String url = System.getenv("ARG_LOCAL_DB_URL");
        String appUser = System.getenv("ARG_LOCAL_DB_APP_USER");
        if (url == null || appUser == null) return;
        MySqlLocalEndpoint endpoint = MySqlLocalEndpoint.fromJdbcUrl(url);
        if (!endpoint.database().endsWith("_test")) return;

        String account = MySqlLocalDatabaseProvisioner.quoteString(appUser, "") + "@"
                + MySqlLocalDatabaseProvisioner.quoteString(endpoint.accountHost(), "");
        String sql = "DROP USER IF EXISTS " + account + ";\nDROP DATABASE IF EXISTS `"
                + endpoint.database() + "`;\n";
        if ("socket".equalsIgnoreCase(System.getenv("ARG_DB_ADMIN_MODE"))) {
            removeWithLocalSocket(sql);
        } else {
            try (var connection = DriverManager.getConnection(endpoint.administratorUrl(),
                    required("ARG_LOCAL_DB_ADMIN_USER"), System.getenv().getOrDefault("ARG_LOCAL_DB_ADMIN_PASSWORD", ""));
                 var statement = connection.createStatement()) {
                statement.execute("DROP USER IF EXISTS " + account);
                statement.execute("DROP DATABASE IF EXISTS `" + endpoint.database() + "`");
            }
        }
    }

    @Test void createsDatabaseUserAppliesMigrationsAndStoresAppCredentials() throws Exception {
        String url = required("ARG_LOCAL_DB_URL");
        MySqlLocalEndpoint endpoint = MySqlLocalEndpoint.fromJdbcUrl(url);
        assertTrue(endpoint.database().endsWith("_test"), "The setup integration test requires a disposable _test database.");

        Path configFile = temporary.resolve("database.properties");
        MySqlConfig app = provision(url, configFile);

        assertEquals(app, MySqlConfigStore.load(temporary.resolve("database.properties")).orElseThrow());
        try (var connection = DriverManager.getConnection(app.url(), app.username(), app.password());
             var statement = connection.createStatement();
             var results = statement.executeQuery("SELECT COUNT(*) FROM schema_migrations")) {
            assertTrue(results.next());
            assertEquals(6, results.getInt(1));
        }

        assertThrows(IllegalArgumentException.class,
                () -> provision(url, configFile, "different-application-password"));
        try (var connection = DriverManager.getConnection(app.url(), app.username(), app.password());
             var statement = connection.createStatement();
             var results = statement.executeQuery("SELECT 1")) {
            assertTrue(results.next(), "A duplicate username must not reset the existing application password.");
        }
    }

    private MySqlConfig provision(String url, Path configFile) {
        return provision(url, configFile, required("ARG_LOCAL_DB_APP_PASSWORD"));
    }

    private MySqlConfig provision(String url, Path configFile, String applicationPasswordValue) {
        String applicationUser = required("ARG_LOCAL_DB_APP_USER");
        char[] applicationPassword = applicationPasswordValue.toCharArray();
        if ("socket".equalsIgnoreCase(System.getenv("ARG_DB_ADMIN_MODE")))
            return MySqlLocalDatabaseProvisioner.provisionUsingSocket(url, applicationUser, applicationPassword, configFile);
        return MySqlLocalDatabaseProvisioner.provision(url, required("ARG_LOCAL_DB_ADMIN_USER"),
                System.getenv().getOrDefault("ARG_LOCAL_DB_ADMIN_PASSWORD", "").toCharArray(),
                applicationUser, applicationPassword, configFile);
    }

    private static void removeWithLocalSocket(String sql) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("mariadb", "--no-defaults", "--protocol=socket", "--user=root", "--batch")
                .redirectErrorStream(true).start();
        try (OutputStream input = process.getOutputStream()) {
            input.write(sql.getBytes(StandardCharsets.UTF_8));
        }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Timed out while removing the disposable local database test schema.");
        }
        if (process.exitValue() != 0)
            throw new IOException("Could not remove the disposable local database test schema.");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name + " for this disposable integration test.");
        return value;
    }
}

package com.adaptivereadinggame.repository.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MySqlLocalDatabaseSetupTest {
    @TempDir Path temporary;

    @Test void helpDocumentsFirstRunAndReconfigureOptions() {
        String usage = MySqlLocalDatabaseSetup.usage();

        assertTrue(usage.contains("--reconfigure"));
        assertTrue(usage.contains("--check-only"));
        assertTrue(usage.contains("local MySQL"));
        assertTrue(usage.contains("password"));
    }

    @Test void checkOnlyReportsTemporaryModeWithoutPromptingWhenNoSettingsExist() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int status = MySqlLocalDatabaseSetup.run(new String[] {"--check-only"}, null,
                new PrintStream(output), new PrintStream(error), temporary.resolve("missing.properties"), Map.of());

        assertEquals(0, status);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("temporary in-memory storage"));
        assertEquals("", error.toString(StandardCharsets.UTF_8));
    }

    @Test void checkOnlyFindsSavedPersistentSettingsWithoutPrintingThePassword() {
        Path file = temporary.resolve("database.properties");
        MySqlConfigStore.save(file,
                new MySqlConfig("jdbc:mysql://127.0.0.1:3306/adaptive_reading", "adaptive_game", "hidden-secret"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int status = MySqlLocalDatabaseSetup.run(new String[] {"--check-only"}, null,
                new PrintStream(output), new PrintStream(error), file, Map.of());

        assertEquals(0, status);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Persistent local database settings"));
        assertTrue(!output.toString(StandardCharsets.UTF_8).contains("hidden-secret"));
        assertEquals("", error.toString(StandardCharsets.UTF_8));
    }

    @Test void reconfigureRejectsEnvironmentOverridesThatWouldHideTheSavedSettings() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int status = MySqlLocalDatabaseSetup.run(new String[] {"--reconfigure"}, null,
                new PrintStream(output), new PrintStream(error), temporary.resolve("database.properties"),
                Map.of("ARG_DB_URL", "jdbc:mysql://127.0.0.1:3306/adaptive_reading"));

        assertEquals(2, status);
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("Unset ARG_DB_URL"));
    }

    @Test void setupRejectsRemoteDatabaseTargetsBeforeConnecting() {
        MySqlConfig remote = new MySqlConfig("jdbc:mysql://database.example:3306/adaptive_reading",
                "adaptive_game", "local-secret");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> MySqlLocalDatabaseSetup.validateLocalConfiguration(remote));

        assertTrue(failure.getMessage().contains("only localhost"));
    }

    @Test void safeFailureReportIncludesMySqlErrorCodeWithoutLeakingTheCauseMessage() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RuntimeException failure = new IllegalStateException("Could not configure the local database.",
                new SQLException("Access denied; password=private-value", "28000", 1045));

        MySqlLocalDatabaseSetup.printSafeFailure(new PrintStream(output), failure);

        String report = output.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("SQLState 28000"));
        assertTrue(report.contains("error code 1045"));
        assertTrue(report.contains("administrator credentials"));
        assertFalse(report.contains("private-value"));
        assertFalse(report.contains("Access denied;"));
    }

    @Test void checkOnlyRejectsAStoredRemoteDatabaseTarget() throws Exception {
        Path file = temporary.resolve("remote.properties");
        MySqlConfigStore.save(file,
                new MySqlConfig("jdbc:mysql://database.example:3306/adaptive_reading", "adaptive_game", "local-secret"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int status = MySqlLocalDatabaseSetup.run(new String[] {"--check-only"}, null,
                new PrintStream(output), new PrintStream(error), file, Map.of());

        assertEquals(1, status);
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("only localhost"));
        assertTrue(!output.toString(StandardCharsets.UTF_8).contains("local-secret"));
    }
}

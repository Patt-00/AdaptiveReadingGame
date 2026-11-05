package com.adaptivereadinggame.repository.mysql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlLocalDatabaseProvisionerTest {
    @Test void escapesBackslashesAndQuotesInDefaultMysqlSqlMode() {
        assertEquals("'slash\\\\and\\'quote'",
                MySqlLocalDatabaseProvisioner.quoteString("slash\\and'quote", "STRICT_TRANS_TABLES"));
    }

    @Test void doublesQuotesWhenBackslashEscapesAreDisabled() {
        assertEquals("'slash\\and''quote'",
                MySqlLocalDatabaseProvisioner.quoteString("slash\\and'quote", "NO_BACKSLASH_ESCAPES"));
    }

    @Test void provisioningStatementsCreateOnlyLocalDatabaseAccounts() {
        MySqlLocalEndpoint endpoint = MySqlLocalEndpoint.fromJdbcUrl("jdbc:mysql://127.0.0.1:3306/adaptive_reading");
        List<String> statements = MySqlLocalDatabaseProvisioner.provisioningStatements(
                endpoint, "adaptive_game", "local-secret", "STRICT_TRANS_TABLES");

        assertEquals(3, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE USER 'adaptive_game'@'127.0.0.1'"));
        assertTrue(statements.get(1).contains("CREATE DATABASE IF NOT EXISTS `adaptive_reading`"));
        assertTrue(statements.get(2).contains("ON `adaptive_reading`.* TO 'adaptive_game'@'127.0.0.1'"));
        assertFalse(statements.stream().anyMatch(sql -> sql.startsWith("ALTER USER")
                || sql.startsWith("CREATE USER IF NOT EXISTS")));
    }
}

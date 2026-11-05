package com.adaptivereadinggame.repository.mysql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MySqlLocalEndpointTest {
    @Test void parsesLoopbackJdbcUrlAndBuildsAdministratorUrl() {
        MySqlLocalEndpoint target = MySqlLocalEndpoint.fromJdbcUrl(
                "jdbc:mysql://127.0.0.1:3307/adaptive_reading?useSSL=false&serverTimezone=UTC");

        assertEquals("127.0.0.1", target.host());
        assertEquals(3307, target.port());
        assertEquals("adaptive_reading", target.database());
        assertEquals("jdbc:mysql://127.0.0.1:3307/?useSSL=false&serverTimezone=UTC", target.administratorUrl());
    }

    @Test void acceptsTheDefaultLocalHostUrl() {
        MySqlLocalEndpoint target = MySqlLocalEndpoint.fromJdbcUrl("jdbc:mysql://localhost/adaptive_reading");

        assertEquals(3306, target.port());
        assertEquals("localhost", target.accountHost());
        assertEquals("jdbc:mysql://localhost:3306/", target.administratorUrl());
    }

    @Test void acceptsIpv6Loopback() {
        MySqlLocalEndpoint target = MySqlLocalEndpoint.fromJdbcUrl("jdbc:mysql://[::1]:3306/adaptive_reading");

        assertEquals("::1", target.host());
        assertEquals("::1", target.accountHost());
        assertEquals("jdbc:mysql://[::1]:3306/", target.administratorUrl());
    }

    @Test void rejectsRemoteHostsAndInvalidDatabaseNames() {
        assertThrows(IllegalArgumentException.class,
                () -> MySqlLocalEndpoint.fromJdbcUrl("jdbc:mysql://example.com:3306/adaptive_reading"));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlLocalEndpoint.fromJdbcUrl("jdbc:mysql://127.0.0.1:3306/adaptive-reading"));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlLocalEndpoint.fromJdbcUrl("jdbc:mysql://user:pass@127.0.0.1:3306/adaptive_reading"));
    }
}

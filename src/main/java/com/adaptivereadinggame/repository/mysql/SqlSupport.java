package com.adaptivereadinggame.repository.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

final class SqlSupport {
    @FunctionalInterface interface Work<T> { T run(Connection connection) throws SQLException; }

    private SqlSupport() {}

    static <T> T read(ConnectionProvider provider, String operation, Work<T> work) {
        try (Connection connection = provider.open()) {
            return work.run(connection);
        } catch (SQLException failure) {
            throw new RepositoryException("MySQL " + operation + " failed.", failure);
        }
    }

    static <T> T transaction(ConnectionProvider provider, String operation, Work<T> work) {
        try (Connection connection = provider.open()) {
            connection.setAutoCommit(false);
            try {
                T value = work.run(connection);
                connection.commit();
                return value;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new RepositoryException("MySQL " + operation + " failed.", failure);
            }
        } catch (SQLException failure) {
            throw new RepositoryException("MySQL " + operation + " failed.", failure);
        }
    }

    static boolean isConstraintViolation(SQLException failure) {
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            if (current instanceof SQLIntegrityConstraintViolationException
                    || "23000".equals(current.getSQLState())) return true;
        }
        return false;
    }

    static String id(UUID id) { return id.toString(); }
    static UUID uuid(String value) { return UUID.fromString(value); }
    static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }
    static Instant instant(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC);
    }
}

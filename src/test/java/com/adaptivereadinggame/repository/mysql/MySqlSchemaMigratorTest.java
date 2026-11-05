package com.adaptivereadinggame.repository.mysql;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlSchemaMigratorTest {
    @Test void reportsTheMigrationVersionAndSqlReasonWhenAnApplyStepFails() {
        AtomicInteger executedStatements = new AtomicInteger();
        Connection connection = proxy(Connection.class, (instance, method, arguments) -> switch (method.getName()) {
            case "createStatement" -> proxy(Statement.class, (statement, statementMethod, statementArguments) -> {
                if (statementMethod.getName().equals("execute") && executedStatements.incrementAndGet() == 2)
                    throw new SQLException("fixture permission failure");
                return defaultValue(statementMethod.getReturnType());
            });
            case "prepareStatement" -> proxy(PreparedStatement.class,
                    (prepared, preparedMethod, preparedArguments) -> switch (preparedMethod.getName()) {
                        case "executeQuery" -> proxy(ResultSet.class, (rows, resultMethod, resultArguments) ->
                                resultMethod.getName().equals("next") ? false : defaultValue(resultMethod.getReturnType()));
                        default -> defaultValue(preparedMethod.getReturnType());
                    });
            default -> defaultValue(method.getReturnType());
        });

        RepositoryException failure = assertThrows(RepositoryException.class,
                () -> new MySqlSchemaMigrator(() -> connection).migrate());

        assertTrue(failure.getMessage().contains("Migration 1 failed"));
        assertTrue(failure.getMessage().contains("fixture permission failure"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        if (type == char.class) return '\0';
        return null;
    }
}

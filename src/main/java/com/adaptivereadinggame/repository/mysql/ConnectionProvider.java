package com.adaptivereadinggame.repository.mysql;

import java.sql.Connection;
import java.sql.SQLException;

/** Supplies a fresh JDBC connection for one repository operation. */
@FunctionalInterface
public interface ConnectionProvider {
    Connection open() throws SQLException;
}

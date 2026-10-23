package com.adaptivereadinggame.repository.mysql;

import com.adaptivereadinggame.model.Account;
import com.adaptivereadinggame.model.ReadingLevel;
import com.adaptivereadinggame.model.Student;
import com.adaptivereadinggame.repository.AccountRepository;
import com.adaptivereadinggame.repository.CredentialRepository;
import com.adaptivereadinggame.repository.RegistrationRepository;
import com.adaptivereadinggame.repository.StudentRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Account/profile/credential adapter with an atomic account registration boundary. */
public final class MySqlAccountRepository implements AccountRepository, CredentialRepository,
        RegistrationRepository, StudentRepository {
    private final ConnectionProvider connections;

    public MySqlAccountRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override public Optional<Account> findAccount(UUID id) {
        Objects.requireNonNull(id);
        return SqlSupport.read(connections, "account lookup", connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT id, username, display_name FROM accounts WHERE id = ?")) {
                statement.setString(1, SqlSupport.id(id));
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(readAccount(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override public Optional<Account> findByUsername(String normalizedUsername) {
        Objects.requireNonNull(normalizedUsername);
        if (!normalizedUsername.equals(normalizedUsername.trim().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Username lookup requires the normalized username.");
        return SqlSupport.read(connections, "username lookup", connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT id, username, display_name FROM accounts WHERE username = ?")) {
                statement.setString(1, normalizedUsername);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(readAccount(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override public Account create(Account account) {
        validateAccount(account);
        try {
            return SqlSupport.transaction(connections, "account creation", connection -> {
                insertAccount(connection, account);
                return account;
            });
        } catch (RuntimeException failure) {
            if (isConstraint(failure)) throw new IllegalArgumentException("The account ID or username is already used.", failure);
            throw failure;
        }
    }

    @Override public Optional<String> findEncodedVerifier(UUID accountId) {
        Objects.requireNonNull(accountId);
        return SqlSupport.read(connections, "credential lookup", connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT encoded_verifier FROM credentials WHERE account_id = ?")) {
                statement.setString(1, SqlSupport.id(accountId));
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(rows.getString(1)) : Optional.empty();
                }
            }
        });
    }

    @Override public void saveEncodedVerifier(UUID accountId, String encodedVerifier) {
        Objects.requireNonNull(accountId);
        requireVerifier(encodedVerifier);
        SqlSupport.transaction(connections, "credential write", connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO credentials(account_id, encoded_verifier) VALUES (?, ?) "
                            + "ON DUPLICATE KEY UPDATE encoded_verifier = VALUES(encoded_verifier)")) {
                statement.setString(1, SqlSupport.id(accountId));
                statement.setString(2, encodedVerifier);
                statement.executeUpdate();
                return null;
            }
        });
    }

    @Override public Optional<Student> findById(UUID id) {
        Objects.requireNonNull(id);
        return SqlSupport.read(connections, "student lookup", connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT id, display_name, reading_level, completed_attempts, accuracy FROM students WHERE id = ?")) {
                statement.setString(1, SqlSupport.id(id));
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(readStudent(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override public Student save(Student student) {
        validateStudent(student);
        return SqlSupport.transaction(connections, "student write", connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO students(id, display_name, reading_level, completed_attempts, accuracy) "
                            + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), "
                            + "reading_level = VALUES(reading_level), completed_attempts = VALUES(completed_attempts), "
                            + "accuracy = VALUES(accuracy)")) {
                bindStudent(statement, student);
                statement.executeUpdate();
                return student;
            }
        });
    }

    @Override public Account register(Account account, String encodedVerifier, Student student) {
        validateAccount(account);
        requireVerifier(encodedVerifier);
        validateStudent(student);
        if (!account.id().equals(student.id()) || !account.displayName().equals(student.displayName()))
            throw new IllegalArgumentException("Account and student must describe the same reader.");
        try {
            return SqlSupport.transaction(connections, "account registration", connection -> {
                insertAccount(connection, account);
                try (var credential = connection.prepareStatement(
                        "INSERT INTO credentials(account_id, encoded_verifier) VALUES (?, ?)")) {
                    credential.setString(1, SqlSupport.id(account.id()));
                    credential.setString(2, encodedVerifier);
                    credential.executeUpdate();
                }
                try (var profile = connection.prepareStatement(
                        "INSERT INTO students(id, display_name, reading_level, completed_attempts, accuracy) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    bindStudent(profile, student);
                    profile.executeUpdate();
                }
                return account;
            });
        } catch (RuntimeException failure) {
            if (isConstraint(failure)) throw new IllegalArgumentException("The account ID or username is already used.", failure);
            throw failure;
        }
    }

    private static void insertAccount(java.sql.Connection connection, Account account) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO accounts(id, username, display_name, created_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, SqlSupport.id(account.id()));
            statement.setString(2, account.username());
            statement.setString(3, account.displayName());
            statement.setObject(4, SqlSupport.utc(java.time.Instant.now()));
            statement.executeUpdate();
        }
    }

    private static Account readAccount(ResultSet rows) throws SQLException {
        return new Account(SqlSupport.uuid(rows.getString("id")), rows.getString("username"),
                rows.getString("display_name"));
    }

    private static Student readStudent(ResultSet rows) throws SQLException {
        return new Student(SqlSupport.uuid(rows.getString("id")), rows.getString("display_name"),
                ReadingLevel.valueOf(rows.getString("reading_level")), rows.getInt("completed_attempts"),
                rows.getDouble("accuracy"));
    }

    private static void bindStudent(java.sql.PreparedStatement statement, Student student) throws SQLException {
        statement.setString(1, SqlSupport.id(student.id()));
        statement.setString(2, student.displayName());
        statement.setString(3, student.readingLevel().name());
        statement.setInt(4, student.completedAttempts());
        statement.setDouble(5, student.accuracy());
    }

    private static void validateAccount(Account account) {
        Objects.requireNonNull(account);
        if (!account.username().equals(account.username().trim().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("The username must already be normalized.");
        if (account.username().length() > 50 || account.displayName().length() > 100)
            throw new IllegalArgumentException("Account fields are too long.");
    }

    private static void validateStudent(Student student) {
        Objects.requireNonNull(student);
        Objects.requireNonNull(student.id());
        Objects.requireNonNull(student.readingLevel());
        if (student.completedAttempts() < 0 || !Double.isFinite(student.accuracy()))
            throw new IllegalArgumentException("Invalid student progress.");
        if (student.displayName().length() > 100) throw new IllegalArgumentException("Display name is too long.");
    }

    private static void requireVerifier(String encodedVerifier) {
        if (encodedVerifier == null || encodedVerifier.isBlank() || encodedVerifier.length() > 255)
            throw new IllegalArgumentException("A valid encoded password verifier is required.");
    }

    private static boolean isConstraint(RuntimeException failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && SqlSupport.isConstraintViolation(sql)) return true;
        }
        return false;
    }
}

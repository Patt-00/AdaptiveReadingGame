package com.adaptivereadinggame.repository.memory;

import com.adaptivereadinggame.model.Account;
import com.adaptivereadinggame.model.Student;
import com.adaptivereadinggame.repository.AccountRepository;
import com.adaptivereadinggame.repository.CredentialRepository;
import com.adaptivereadinggame.repository.RegistrationRepository;
import com.adaptivereadinggame.repository.StudentRepository;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Temporary adapter: all records disappear on JVM exit. Registration is one synchronized
 * operation so either account, encoded credential, and student all appear, or none do.
 */
public final class InMemoryAccountRepository implements AccountRepository, CredentialRepository,
        StudentRepository, RegistrationRepository {
    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Map<String, UUID> usernames = new HashMap<>();
    private final Map<UUID, String> credentials = new HashMap<>();
    private final Map<UUID, Student> students = new HashMap<>();

    @Override public synchronized Optional<Account> findAccount(UUID id) {
        return Optional.ofNullable(accounts.get(Objects.requireNonNull(id)));
    }
    @Override public synchronized Optional<Account> findByUsername(String normalizedUsername) {
        UUID accountId = usernames.get(Objects.requireNonNull(normalizedUsername));
        return accountId == null ? Optional.empty() : Optional.of(accounts.get(accountId));
    }
    @Override public synchronized Account create(Account account) {
        validateNewAccount(account);
        accounts.put(account.id(), account);
        usernames.put(account.username(), account.id());
        return account;
    }
    @Override public synchronized Optional<String> findEncodedVerifier(UUID accountId) {
        return Optional.ofNullable(credentials.get(Objects.requireNonNull(accountId)));
    }
    @Override public synchronized void saveEncodedVerifier(UUID accountId, String encodedVerifier) {
        Objects.requireNonNull(accountId);
        requireVerifier(encodedVerifier);
        if (!accounts.containsKey(accountId)) throw new IllegalArgumentException("Unknown account.");
        credentials.put(accountId, encodedVerifier);
    }
    @Override public synchronized Optional<Student> findById(UUID id) {
        return Optional.ofNullable(students.get(Objects.requireNonNull(id)));
    }
    @Override public synchronized Student save(Student student) {
        validateStudent(student);
        students.put(student.id(), student);
        return student;
    }
    @Override public synchronized Account register(Account account, String encodedVerifier, Student student) {
        validateNewAccount(account);
        requireVerifier(encodedVerifier);
        validateStudent(student);
        if (!account.id().equals(student.id()) || !account.displayName().equals(student.displayName()))
            throw new IllegalArgumentException("Account and student must describe the same reader.");
        if (credentials.containsKey(account.id()) || students.containsKey(account.id()))
            throw new IllegalArgumentException("The account ID is already used.");
        // Every validation above occurs before the first write.
        accounts.put(account.id(), account);
        usernames.put(account.username(), account.id());
        credentials.put(account.id(), encodedVerifier);
        students.put(student.id(), student);
        return account;
    }

    private void validateNewAccount(Account account) {
        Objects.requireNonNull(account);
        if (!account.username().equals(account.username().trim().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("The username must already be normalized.");
        if (accounts.containsKey(account.id()) || usernames.containsKey(account.username()))
            throw new IllegalArgumentException("The account ID or username is already used.");
    }
    private static void requireVerifier(String encodedVerifier) {
        if (encodedVerifier == null || encodedVerifier.isBlank())
            throw new IllegalArgumentException("An encoded password verifier is required.");
    }
    private static void validateStudent(Student student) {
        Objects.requireNonNull(student);
        Objects.requireNonNull(student.id());
        Objects.requireNonNull(student.readingLevel());
        if (student.completedAttempts() < 0 || !Double.isFinite(student.accuracy()))
            throw new IllegalArgumentException("Invalid student progress.");
    }
}

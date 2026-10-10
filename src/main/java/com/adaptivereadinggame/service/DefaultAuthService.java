package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.Account;
import com.adaptivereadinggame.model.ReadingLevel;
import com.adaptivereadinggame.model.Student;
import com.adaptivereadinggame.repository.AccountRepository;
import com.adaptivereadinggame.repository.CredentialRepository;
import com.adaptivereadinggame.repository.RegistrationRepository;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One application's signed-in account. Passwords never enter Account or Student. */
public final class DefaultAuthService implements AuthService {
    private final AccountRepository accounts;
    private final CredentialRepository credentials;
    private final RegistrationRepository registrations;
    private final PasswordHasher passwordHasher;
    private final String dummyVerifier;
    private Account current;

    public DefaultAuthService(AccountRepository accounts, CredentialRepository credentials,
                              RegistrationRepository registrations, PasswordHasher passwordHasher) {
        this.accounts = Objects.requireNonNull(accounts);
        this.credentials = Objects.requireNonNull(credentials);
        this.registrations = Objects.requireNonNull(registrations);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        char[] dummyPassword = UUID.randomUUID().toString().toCharArray();
        try {
            dummyVerifier = passwordHasher.encode(dummyPassword);
        } finally {
            Arrays.fill(dummyPassword, '\0');
        }
    }

    @Override public synchronized Account signup(String username, String displayName, char[] password) {
        requireSignedOut();
        String normalizedUsername = normalizeUsername(username);
        if (displayName == null || displayName.trim().isBlank())
            throw validation("A display name is required.");
        requirePassword(password);
        if (accounts.findByUsername(normalizedUsername).isPresent()) throw usernameUnavailable();

        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, normalizedUsername, displayName.trim());
        Student student = new Student(accountId, account.displayName(), ReadingLevel.BEGINNER, 0, 0);
        char[] privatePassword = password.clone();
        String encodedVerifier;
        try {
            encodedVerifier = passwordHasher.encode(privatePassword);
        } finally {
            Arrays.fill(privatePassword, '\0');
        }
        try {
            Account registered = registrations.register(account, encodedVerifier, student);
            if (!account.equals(registered))
                throw new IllegalStateException("Registration did not return the requested account.");
            return registered;
        } catch (IllegalArgumentException rejected) {
            // Another registration may have won after the availability check.
            if (accounts.findByUsername(normalizedUsername).isPresent()) throw usernameUnavailable();
            throw rejected;
        }
    }

    @Override public synchronized Account login(String username, char[] password) {
        requireSignedOut();
        String normalizedUsername;
        try {
            normalizedUsername = normalizeUsername(username);
            requirePassword(password);
        } catch (AuthException invalidInput) {
            throw invalidCredentials();
        }
        Account account = accounts.findByUsername(normalizedUsername).orElse(null);
        String storedVerifier = account == null ? null
                : credentials.findEncodedVerifier(account.id()).orElse(null);
        // Unknown users still incur a password check; all credential failures have one message.
        char[] privatePassword = password.clone();
        boolean matches;
        try {
            matches = passwordHasher.matches(privatePassword,
                    storedVerifier == null ? dummyVerifier : storedVerifier);
        } finally {
            Arrays.fill(privatePassword, '\0');
        }
        if (account == null || storedVerifier == null || !matches) throw invalidCredentials();
        current = account;
        return account;
    }

    @Override public synchronized Optional<Account> currentAccount() {
        return Optional.ofNullable(current);
    }

    @Override public synchronized void logout() { current = null; }

    private void requireSignedOut() {
        if (current != null)
            throw new AuthException(AuthException.Code.ALREADY_SIGNED_IN,
                    "Log out before signing in or creating another account.");
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.trim().isBlank()) throw validation("A username is required.");
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length == 0) throw validation("A password is required.");
        for (char character : password) if (!Character.isWhitespace(character)) return;
        throw validation("A password is required.");
    }

    private static AuthException validation(String message) {
        return new AuthException(AuthException.Code.VALIDATION, message);
    }
    private static AuthException usernameUnavailable() {
        return new AuthException(AuthException.Code.USERNAME_UNAVAILABLE, "That username is unavailable.");
    }
    private static AuthException invalidCredentials() {
        return new AuthException(AuthException.Code.INVALID_CREDENTIALS, "The username or password is incorrect.");
    }
}

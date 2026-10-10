package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.Account;
import com.adaptivereadinggame.model.ReadingLevel;
import com.adaptivereadinggame.model.Student;
import com.adaptivereadinggame.repository.memory.InMemoryAccountRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultAuthServiceTest {
    private InMemoryAccountRepository repository;
    private DefaultAuthService auth;

    /** Deliberately fast and NOT suitable for real credentials. Only injected by tests. */
    private static final class TestHasher implements PasswordHasher {
        @Override public String encode(char[] password) {
            return "test-only:" + Integer.toHexString(Arrays.hashCode(password));
        }
        @Override public boolean matches(char[] password, String verifier) {
            return encode(password).equals(verifier);
        }
    }

    @BeforeEach void setup() {
        repository = new InMemoryAccountRepository();
        auth = new DefaultAuthService(repository, repository, repository, new TestHasher());
    }

    @Test void signupNormalizesUserAndCreatesOneLinkedBeginnerWithoutLoggingIn() {
        Account account = auth.signup("  READER  ", "  Mia  ", "  My password  ".toCharArray());
        assertEquals("reader", account.username());
        assertEquals("Mia", account.displayName());
        assertEquals(account, repository.findAccount(account.id()).orElseThrow());
        Student student = repository.findById(account.id()).orElseThrow();
        assertEquals(account.id(), student.id());
        assertEquals("Mia", student.displayName());
        assertEquals(ReadingLevel.BEGINNER, student.readingLevel());
        assertEquals(0, student.completedAttempts());
        assertEquals(0, student.accuracy());
        assertTrue(auth.currentAccount().isEmpty());
        assertFalse(repository.findEncodedVerifier(account.id()).orElseThrow().contains("My password"));
    }

    @Test void loginWorksWithNormalizedUsernameAndDoesNotTrimPassword() {
        char[] password = "  correct password  ".toCharArray();
        Account account = auth.signup("Reader", "Reader", password);
        assertEquals(AuthException.Code.INVALID_CREDENTIALS,
                assertThrows(AuthException.class, () -> auth.login("reader", "correct password".toCharArray())).code());
        assertTrue(auth.currentAccount().isEmpty());
        assertEquals(account, auth.login(" READER ", password));
        assertEquals(account, auth.currentAccount().orElseThrow());
    }

    @Test void normalizationDoesNotDependOnComputerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Account account = auth.signup("  IVAN  ", "Ivan", "password".toCharArray());
            assertEquals("ivan", account.username());
            assertEquals(account, auth.login("IVAN", "password".toCharArray()));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test void duplicateNormalizedUserKeepsOriginalAccountStudentAndCredential() {
        Account first = auth.signup("reader", "First", "password one".toCharArray());
        String verifier = repository.findEncodedVerifier(first.id()).orElseThrow();
        assertEquals(AuthException.Code.USERNAME_UNAVAILABLE,
                assertThrows(AuthException.class,
                        () -> auth.signup(" READER ", "Other", "password two".toCharArray())).code());
        assertEquals(first, repository.findByUsername("reader").orElseThrow());
        assertEquals("First", repository.findById(first.id()).orElseThrow().displayName());
        assertEquals(verifier, repository.findEncodedVerifier(first.id()).orElseThrow());
    }

    @Test void allCredentialFailuresHaveTheSameSafeMessage() {
        Account account = auth.signup("reader", "Reader", "password".toCharArray());
        String knownMessage = assertThrows(AuthException.class,
                () -> auth.login("reader", "wrong".toCharArray())).getMessage();
        assertEquals(knownMessage, assertThrows(AuthException.class,
                () -> auth.login("nobody", "password".toCharArray())).getMessage());
        assertEquals(knownMessage, assertThrows(AuthException.class,
                () -> auth.login(null, null)).getMessage());
        Account noCredential = new Account(UUID.randomUUID(), "no-credential", "Other");
        repository.create(noCredential);
        assertEquals(knownMessage, assertThrows(AuthException.class,
                () -> auth.login(noCredential.username(), "password".toCharArray())).getMessage());
        repository.saveEncodedVerifier(account.id(), "malformed");
        assertEquals(knownMessage, assertThrows(AuthException.class,
                () -> auth.login("reader", "password".toCharArray())).getMessage());
        assertTrue(auth.currentAccount().isEmpty());
    }

    @Test void activeAccountCannotBeChangedByLoginSignupOrFailedLogin() {
        Account first = auth.signup("reader", "Reader", "password".toCharArray());
        auth.signup("other", "Other", "other password".toCharArray());
        auth.login("reader", "password".toCharArray());
        assertEquals(AuthException.Code.ALREADY_SIGNED_IN,
                assertThrows(AuthException.class, () -> auth.login("other", "other password".toCharArray())).code());
        assertEquals(AuthException.Code.ALREADY_SIGNED_IN,
                assertThrows(AuthException.class, () -> auth.login("reader", "wrong".toCharArray())).code());
        assertEquals(AuthException.Code.ALREADY_SIGNED_IN,
                assertThrows(AuthException.class, () -> auth.signup("third", "Third", "password".toCharArray())).code());
        assertEquals(first, auth.currentAccount().orElseThrow());
        assertTrue(repository.findByUsername("third").isEmpty());
        auth.logout();
        auth.logout();
        assertTrue(auth.currentAccount().isEmpty());
        assertEquals("other", auth.login("other", "other password".toCharArray()).username());
    }

    @Test void requiredFieldsAreRejectedBeforeAnyRecordIsWritten() {
        assertEquals(AuthException.Code.VALIDATION, assertThrows(AuthException.class,
                () -> auth.signup(" ", "Reader", "password".toCharArray())).code());
        assertThrows(AuthException.class, () -> auth.signup("reader", " ", "password".toCharArray()));
        assertThrows(AuthException.class, () -> auth.signup("reader", "Reader", null));
        assertThrows(AuthException.class, () -> auth.signup("reader", "Reader", new char[0]));
        assertThrows(AuthException.class, () -> auth.signup("reader", "Reader", "\t \n".toCharArray()));
        assertTrue(repository.findByUsername("reader").isEmpty());
    }

    @Test void callerPasswordArrayIsPreservedEvenIfInjectedHasherChangesItsInput() {
        PasswordHasher changingHasher = new PasswordHasher() {
            @Override public String encode(char[] password) {
                String result = new TestHasher().encode(password);
                Arrays.fill(password, 'x');
                return result;
            }
            @Override public boolean matches(char[] password, String verifier) {
                boolean result = new TestHasher().matches(password, verifier);
                Arrays.fill(password, 'x');
                return result;
            }
        };
        auth = new DefaultAuthService(repository, repository, repository, changingHasher);
        char[] password = " password ".toCharArray();
        char[] original = password.clone();
        auth.signup("reader", "Reader", password);
        assertArrayEquals(original, password);
        auth.login("reader", password);
        assertArrayEquals(original, password);
    }

    @Test void registrationFailureDoesNotCreatePartialRecordsOrSignIn() {
        auth = new DefaultAuthService(repository, repository,
                (account, verifier, student) -> { throw new IllegalStateException("Storage unavailable"); },
                new TestHasher());
        assertThrows(IllegalStateException.class,
                () -> auth.signup("reader", "Reader", "password".toCharArray()));
        assertTrue(repository.findByUsername("reader").isEmpty());
        assertTrue(auth.currentAccount().isEmpty());
    }

    @Test void registrationCannotReportAnotherAccountOrNullAsSuccess() {
        for (boolean returnNull : List.of(true, false)) {
            auth = new DefaultAuthService(repository, repository,
                    (account, verifier, student) -> returnNull ? null
                            : new Account(UUID.randomUUID(), "other", "Other"), new TestHasher());
            assertThrows(IllegalStateException.class,
                    () -> auth.signup("reader", "Reader", "password".toCharArray()));
            assertTrue(auth.currentAccount().isEmpty());
        }
    }

    @Test void parallelSignupFromSeparateServicesCannotCreateDuplicateUsers() throws Exception {
        int count = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(count)) {
            for (int index = 0; index < count; index++) {
                DefaultAuthService service = new DefaultAuthService(repository, repository, repository, new TestHasher());
                attempts.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try {
                        service.signup(" READER ", "Reader", "password".toCharArray());
                        return true;
                    } catch (AuthException unavailable) {
                        assertEquals(AuthException.Code.USERNAME_UNAVAILABLE, unavailable.code());
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> attempt : attempts) if (attempt.get(20, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        }
        Account account = repository.findByUsername("reader").orElseThrow();
        assertTrue(repository.findById(account.id()).isPresent());
        assertTrue(repository.findEncodedVerifier(account.id()).isPresent());
    }
}

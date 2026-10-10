package com.adaptivereadinggame.repository.memory;

import com.adaptivereadinggame.model.Account;
import com.adaptivereadinggame.model.ReadingLevel;
import com.adaptivereadinggame.model.Student;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryAccountRepositoryTest {
    @Test void mismatchedStudentCannotLeaveAccountOrCredentialBehind() {
        var repository = new InMemoryAccountRepository();
        Account account = new Account(UUID.randomUUID(), "reader", "Reader");
        Student wrongStudent = new Student(UUID.randomUUID(), "Reader", ReadingLevel.BEGINNER, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> repository.register(account, "test-only:encoded", wrongStudent));
        assertTrue(repository.findAccount(account.id()).isEmpty());
        assertTrue(repository.findByUsername(account.username()).isEmpty());
        assertTrue(repository.findEncodedVerifier(account.id()).isEmpty());
        assertTrue(repository.findById(wrongStudent.id()).isEmpty());
    }

    @Test void invalidVerifierStudentAndNameAreValidatedBeforeWrites() {
        var repository = new InMemoryAccountRepository();
        Account account = new Account(UUID.randomUUID(), "reader", "Reader");
        Student student = new Student(account.id(), "Reader", ReadingLevel.BEGINNER, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> repository.register(account, " ", student));
        assertThrows(IllegalArgumentException.class, () -> repository.register(account, "test-only:encoded",
                new Student(account.id(), "Other", ReadingLevel.BEGINNER, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> repository.register(account, "test-only:encoded",
                new Student(account.id(), "Reader", ReadingLevel.BEGINNER, -1, 0)));
        assertTrue(repository.findAccount(account.id()).isEmpty());
        assertTrue(repository.findEncodedVerifier(account.id()).isEmpty());
        assertTrue(repository.findById(account.id()).isEmpty());
    }

    @Test void duplicateUsernameAndDuplicateIdCannotChangeOriginalRecords() {
        var repository = new InMemoryAccountRepository();
        Account account = new Account(UUID.randomUUID(), "reader", "First");
        Student student = new Student(account.id(), "First", ReadingLevel.BEGINNER, 0, 0);
        repository.register(account, "test-only:first", student);
        Account sameUsername = new Account(UUID.randomUUID(), "reader", "Other");
        assertThrows(IllegalArgumentException.class, () -> repository.register(sameUsername, "test-only:other",
                new Student(sameUsername.id(), "Other", ReadingLevel.BEGINNER, 0, 0)));
        Account sameId = new Account(account.id(), "other", "Other");
        assertThrows(IllegalArgumentException.class, () -> repository.register(sameId, "test-only:other",
                new Student(sameId.id(), "Other", ReadingLevel.BEGINNER, 0, 0)));
        assertEquals(account, repository.findAccount(account.id()).orElseThrow());
        assertEquals("test-only:first", repository.findEncodedVerifier(account.id()).orElseThrow());
        assertEquals(student, repository.findById(account.id()).orElseThrow());
        assertTrue(repository.findAccount(sameUsername.id()).isEmpty());
        assertTrue(repository.findByUsername("other").isEmpty());
    }

    @Test void existingStudentIdCannotBeOverwrittenByRegistration() {
        var repository = new InMemoryAccountRepository();
        UUID id = UUID.randomUUID();
        Student original = new Student(id, "Original", ReadingLevel.DEVELOPING, 1, 0.5);
        repository.save(original);
        Account account = new Account(id, "reader", "Reader");
        assertThrows(IllegalArgumentException.class, () -> repository.register(account, "test-only:encoded",
                new Student(id, "Reader", ReadingLevel.BEGINNER, 0, 0)));
        assertTrue(repository.findAccount(id).isEmpty());
        assertTrue(repository.findEncodedVerifier(id).isEmpty());
        assertEquals(original, repository.findById(id).orElseThrow());
    }

    @Test void repositoryRejectsNonNormalizedAccountsAndOrphanCredentials() {
        var repository = new InMemoryAccountRepository();
        assertThrows(IllegalArgumentException.class,
                () -> repository.create(new Account(UUID.randomUUID(), " READER ", "Reader")));
        assertThrows(IllegalArgumentException.class,
                () -> repository.saveEncodedVerifier(UUID.randomUUID(), "test-only:encoded"));
        assertTrue(repository.findByUsername("reader").isEmpty());
    }
}

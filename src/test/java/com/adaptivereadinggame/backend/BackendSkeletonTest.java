package com.adaptivereadinggame.backend;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BackendSkeletonTest {
    private BackendContext backend;
    private UUID student;
    private Chapter chapter;
    @BeforeEach void setup() {
        backend = BackendContext.inMemory(); student = addStudent("Reader");
        chapter = new Chapter(UUID.randomUUID(), "Garden", "Mia planted a seed.", Difficulty.MEDIUM,
                List.of(new Question(UUID.randomUUID(), "What was planted?", List.of("Seed", "Book"), 0)));
        backend.chapters().saveChapter(chapter);
    }
    private UUID addStudent(String name) {
        UUID id = UUID.randomUUID(); backend.students().save(new Student(id, name, ReadingLevel.BEGINNER, 0, 0)); return id;
    }
    @Test void completeScoresInJavaAndStoresHistory() {
        UUID session = backend.assessmentService().start(student, chapter.id());
        assertTrue(backend.assessmentService().submit(session, chapter.questions().getFirst().id(), 0));
        var result = backend.assessmentService().complete(session);
        assertEquals(1.0, result.attempt().score()); assertEquals(Difficulty.HARD, result.nextDifficulty());
        assertEquals(List.of(result), backend.progressService().history(student));
    }
    @Test void retryCannotReplaceWrongFirstAnswer() {
        UUID session = backend.assessmentService().start(student, chapter.id());
        UUID question = chapter.questions().getFirst().id();
        assertFalse(backend.assessmentService().submit(session, question, 1));
        assertFalse(backend.assessmentService().submit(session, question, 0));
        assertEquals(0, backend.assessmentService().complete(session).attempt().correctAnswers());
    }
    @Test void incompleteAssessmentCannotComplete() {
        UUID session = backend.assessmentService().start(student, chapter.id());
        assertThrows(IllegalStateException.class, () -> backend.assessmentService().complete(session));
        assertTrue(backend.progressService().history(student).isEmpty());
    }
    @Test void repeatedCompletionIsIdempotent() {
        UUID session = backend.assessmentService().start(student, chapter.id());
        backend.assessmentService().submit(session, chapter.questions().getFirst().id(), 0);
        var result = backend.assessmentService().complete(session);
        assertEquals(result, backend.assessmentService().complete(session));
        assertEquals(1, backend.progressService().history(student).size());
        assertThrows(IllegalStateException.class, () -> backend.assessmentService().submit(session, chapter.questions().getFirst().id(), 1));
    }
    @Test void chapterReplayDoesNotOverwriteOriginalScore() {
        UUID first = backend.assessmentService().start(student, chapter.id());
        backend.assessmentService().submit(first, chapter.questions().getFirst().id(), 1);
        var original = backend.assessmentService().complete(first);
        UUID replay = backend.assessmentService().start(student, chapter.id());
        backend.assessmentService().submit(replay, chapter.questions().getFirst().id(), 0);
        assertEquals(original, backend.assessmentService().complete(replay));
        assertEquals(1, backend.progressService().history(student).size());
    }
    @Test void rejectsUnknownStudentChapterQuestionAndChoice() {
        assertThrows(IllegalArgumentException.class, () -> backend.assessmentService().start(UUID.randomUUID(), chapter.id()));
        assertThrows(IllegalArgumentException.class, () -> backend.assessmentService().start(student, UUID.randomUUID()));
        UUID session = backend.assessmentService().start(student, chapter.id());
        assertThrows(IllegalArgumentException.class, () -> backend.assessmentService().submit(session, UUID.randomUUID(), 0));
        assertThrows(IllegalArgumentException.class, () -> backend.assessmentService().submit(session, chapter.questions().getFirst().id(), 2));
    }
    @Test void historiesAreScopedByStudent() {
        UUID second = addStudent("Other");
        UUID session = backend.assessmentService().start(student, chapter.id());
        backend.assessmentService().submit(session, chapter.questions().getFirst().id(), 0);
        backend.assessmentService().complete(session);
        assertTrue(backend.progressService().history(second).isEmpty());
    }
    @Test void slotsAreScopedByStudentAndOverwritesAreExplicit() {
        UUID second = addStudent("Other");
        backend.progressService().save(student, 1, chapter.id(), "opening");
        assertTrue(backend.progressService().load(second, 1).isEmpty());
        backend.progressService().save(student, 1, chapter.id(), "ending");
        assertEquals("ending", backend.progressService().load(student, 1).orElseThrow().storyNodeId());
        assertEquals(1, backend.progressService().slots(student).size());
    }
    @Test void invalidSlotsAndUnknownSavedChapterAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> backend.progressService().save(student, 0, chapter.id(), "opening"));
        assertThrows(IllegalArgumentException.class, () -> backend.progressService().load(student, 4));
        assertThrows(IllegalArgumentException.class, () -> backend.progressService().save(student, 1, UUID.randomUUID(), "opening"));
    }
    @Test void logoutCleanupRemovesOnlyThatStudentsSessions() {
        UUID second = addStudent("Other");
        UUID firstSession = backend.assessmentService().start(student, chapter.id());
        UUID secondSession = backend.assessmentService().start(second, chapter.id());
        backend.assessmentService().clearSessionsFor(student);
        assertThrows(IllegalArgumentException.class, () -> backend.assessmentService().questions(firstSession));
        assertEquals(1, backend.assessmentService().questions(secondSession).size());
    }
    @Test void baselineMatchesFiveQuestionRulesAndBounds() {
        var policy = new BaselineDifficultyPolicy();
        assertEquals(Difficulty.HARD, policy.nextDifficulty(Difficulty.MEDIUM, 4,5));
        assertEquals(Difficulty.MEDIUM, policy.nextDifficulty(Difficulty.MEDIUM, 3,5));
        assertEquals(Difficulty.EASY, policy.nextDifficulty(Difficulty.MEDIUM, 2,5));
        assertEquals(Difficulty.HARD, policy.nextDifficulty(Difficulty.HARD, 5,5));
        assertEquals(Difficulty.EASY, policy.nextDifficulty(Difficulty.EASY, 0,5));
    }
    @Test void invalidScoreAndLargeDifficultyJumpAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Attempt(UUID.randomUUID(), student, chapter.id(), 2,1, java.time.Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> Difficulty.EASY.move(2));
        assertThrows(IllegalArgumentException.class, () -> new BaselineDifficultyPolicy().nextDifficulty(Difficulty.MEDIUM, 0,0));
    }
    @Test void injectedPolicyCannotSkipALevel() {
        var easy = new Chapter(UUID.randomUUID(), "Easy", "A seed.", Difficulty.EASY, chapter.questions());
        backend.chapters().saveChapter(easy);
        var service = new AssessmentService(backend.students(), backend.chapters(), backend.assessments(),
                context -> Difficulty.HARD, java.time.Clock.systemUTC());
        UUID session = service.start(student, easy.id()); service.submit(session, easy.questions().getFirst().id(), 0);
        assertThrows(IllegalStateException.class, () -> service.complete(session));
    }
    @Test void duplicateQuestionIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Chapter(UUID.randomUUID(), "Bad", "Text", Difficulty.EASY,
                List.of(chapter.questions().getFirst(), chapter.questions().getFirst())));
    }
    @Test void repositoryReturnsOriginalCompletionOnDuplicateInsert() {
        var first = new AssessmentResult(new Attempt(UUID.randomUUID(), student, chapter.id(), 0,1,
                java.time.Instant.now()), Difficulty.MEDIUM, Difficulty.EASY);
        var replacement = new AssessmentResult(new Attempt(UUID.randomUUID(), student, chapter.id(), 1,1,
                java.time.Instant.now()), Difficulty.MEDIUM, Difficulty.HARD);
        assertEquals(first, backend.assessments().saveCompleted(first));
        assertEquals(first, backend.assessments().saveCompleted(replacement));
        assertEquals(List.of(first), backend.assessments().historyFor(student));
    }
}

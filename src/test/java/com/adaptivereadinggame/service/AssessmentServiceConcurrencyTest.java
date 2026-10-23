package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.Chapter;
import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.Question;
import com.adaptivereadinggame.model.ReadingLevel;
import com.adaptivereadinggame.model.Student;
import com.adaptivereadinggame.repository.memory.InMemoryAccountRepository;
import com.adaptivereadinggame.repository.memory.InMemoryGameRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentServiceConcurrencyTest {
    @Test void slowPolicyForOneSessionDoesNotLockOtherSessions() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID firstChapterId = UUID.randomUUID();
        UUID secondChapterId = UUID.randomUUID();
        var students = new InMemoryAccountRepository();
        students.save(new Student(studentId, "Concurrent Reader", ReadingLevel.BEGINNER, 0, 0));
        var repository = new InMemoryGameRepository();
        repository.saveChapter(chapter(firstChapterId));
        repository.saveChapter(chapter(secondChapterId));
        var modelStarted = new CountDownLatch(1);
        var finishModel = new CountDownLatch(1);
        var policy = (DifficultyPolicy) context -> {
            if (context.chapterId().equals(firstChapterId)) {
                modelStarted.countDown();
                try {
                    if (!finishModel.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return context.current();
        };
        var service = new AssessmentService(students, repository, repository, policy, Clock.systemUTC());
        UUID firstSession = startAnswered(service, studentId, firstChapterId);
        UUID secondSession = startAnswered(service, studentId, secondChapterId);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> slowCompletion = workers.submit(() -> service.complete(firstSession));
            assertTrue(modelStarted.await(2, TimeUnit.SECONDS));
            Future<List<AssessmentService.QuestionPrompt>> otherQuestions = workers.submit(
                    () -> service.questions(secondSession));
            assertEquals(1, otherQuestions.get(500, TimeUnit.MILLISECONDS).size());
            Future<Boolean> otherAnswer = workers.submit(() ->
                    service.submit(secondSession, repository.findChapter(secondChapterId).orElseThrow()
                            .questions().getFirst().id(), 0));
            assertTrue(otherAnswer.get(500, TimeUnit.MILLISECONDS));
            finishModel.countDown();
            slowCompletion.get(2, TimeUnit.SECONDS);
        } finally {
            finishModel.countDown();
            workers.shutdownNow();
        }
    }

    @Test void concurrentCompletionRequestsShareOnePolicyDecisionAndResult() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        var students = new InMemoryAccountRepository();
        students.save(new Student(studentId, "Concurrent Reader", ReadingLevel.BEGINNER, 0, 0));
        var repository = new InMemoryGameRepository();
        repository.saveChapter(chapter(chapterId));
        var modelStarted = new CountDownLatch(1);
        var finishModel = new CountDownLatch(1);
        var calls = new AtomicInteger();
        DifficultyPolicy policy = context -> {
            calls.incrementAndGet();
            modelStarted.countDown();
            try {
                if (!finishModel.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return context.current();
        };
        var service = new AssessmentService(students, repository, repository, policy, Clock.systemUTC());
        UUID sessionId = startAnswered(service, studentId, chapterId);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> service.complete(sessionId));
            assertTrue(modelStarted.await(2, TimeUnit.SECONDS));
            Future<?> second = workers.submit(() -> service.complete(sessionId));
            assertFalse(second.isDone(), "the second caller waits for the one in-flight completion");
            finishModel.countDown();
            Object firstResult = first.get(2, TimeUnit.SECONDS);
            Object secondResult = second.get(2, TimeUnit.SECONDS);
            assertSame(firstResult, secondResult);
            assertEquals(1, calls.get());
        } finally {
            finishModel.countDown();
            workers.shutdownNow();
        }
    }

    private static UUID startAnswered(AssessmentService service, UUID studentId, UUID chapterId) {
        UUID sessionId = service.start(studentId, chapterId);
        var question = service.questions(sessionId).getFirst();
        assertTrue(service.submit(sessionId, question.id(), 0));
        return sessionId;
    }

    private static Chapter chapter(UUID id) {
        return new Chapter(id, "Concurrency fixture", "A short fixture passage.", Difficulty.MEDIUM,
                List.of(new Question(UUID.randomUUID(), "Fixture question?", List.of("Yes", "No"), 0)));
    }
}

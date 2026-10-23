package com.adaptivereadinggame.service;

import com.adaptivereadinggame.ai.DifficultyService;
import com.adaptivereadinggame.model.AssessmentResult;
import com.adaptivereadinggame.model.Attempt;
import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.DifficultyDecisionSource;
import com.adaptivereadinggame.repository.memory.InMemoryGameRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModelAssistedDifficultyPolicyTest {
    private final UUID studentId = UUID.randomUUID();
    private final InMemoryGameRepository results = new InMemoryGameRepository();

    @Test void javaKeepsColdStartWithoutCallingModel() {
        AtomicInteger calls = new AtomicInteger();
        var policy = new ModelAssistedDifficultyPolicy(results, prompt -> {
            calls.incrementAndGet();
            return DifficultyService.Action.INCREASE;
        });

        assertEquals(Difficulty.MEDIUM, policy.nextDifficulty(context(Difficulty.MEDIUM, 4, 5)));
        assertEquals(DifficultyDecisionSource.JAVA_RULES,
                policy.recommend(context(Difficulty.MEDIUM, 4, 5)).source());
        assertEquals(0, calls.get());
    }

    @Test void sendsOnlyNumericHistoryAndMapsTheSafeguardedJavaDecision() {
        addHistory(Difficulty.MEDIUM, 5, 4);
        UUID chapterId = UUID.randomUUID();
        var policy = new ModelAssistedDifficultyPolicy(results, prompt -> {
            assertTrue(prompt.input().contains("100%, 80%, 80%"));
            assertFalse(prompt.input().contains(studentId.toString()));
            assertFalse(prompt.input().contains(chapterId.toString()));
            return DifficultyService.Action.INCREASE;
        });

        var recommendation = policy.recommend(
                new DifficultyPolicy.Context(studentId, chapterId, Difficulty.MEDIUM, 4, 5));
        assertEquals(Difficulty.HARD, recommendation.nextDifficulty());
        assertEquals(DifficultyDecisionSource.MODEL, recommendation.source());
    }

    @Test void serviceFailureUsesJavaBaseline() {
        addHistory(Difficulty.MEDIUM, 2, 5, 2, 5);
        var policy = new ModelAssistedDifficultyPolicy(results,
                prompt -> { throw new java.io.IOException("Model helper unavailable"); });

        var recommendation = policy.recommend(context(Difficulty.MEDIUM, 2, 5));
        assertEquals(Difficulty.EASY, recommendation.nextDifficulty());
        assertEquals(DifficultyDecisionSource.JAVA_FALLBACK, recommendation.source());
    }

    @Test void javaGuardrailSourceIsVisibleWhenItRejectsTheModelSuggestion() {
        addHistory(Difficulty.MEDIUM, 3, 3);
        var policy = new ModelAssistedDifficultyPolicy(results, prompt -> DifficultyService.Action.INCREASE);
        var recommendation = policy.recommend(context(Difficulty.MEDIUM, 3, 5));
        assertEquals(Difficulty.MEDIUM, recommendation.nextDifficulty());
        assertEquals(DifficultyDecisionSource.JAVA_GUARDRAIL, recommendation.source());
    }

    @Test void aDifficultyChangeResetsTheComparableHistoryRun() {
        addHistory(Difficulty.MEDIUM, 5, 5);
        addHistory(Difficulty.EASY, 5, 5);
        AtomicInteger calls = new AtomicInteger();
        var policy = new ModelAssistedDifficultyPolicy(results, prompt -> {
            calls.incrementAndGet();
            return DifficultyService.Action.INCREASE;
        });

        assertEquals(Difficulty.MEDIUM, policy.nextDifficulty(context(Difficulty.MEDIUM, 5, 5)));
        assertEquals(0, calls.get());
    }

    private DifficultyPolicy.Context context(Difficulty current, int correct, int total) {
        return new DifficultyPolicy.Context(studentId, UUID.randomUUID(), current, correct, total);
    }

    private void addHistory(Difficulty difficulty, int... scores) {
        Instant completed = Instant.parse("2026-01-01T00:00:00Z");
        for (int score : scores) {
            UUID chapterId = UUID.randomUUID();
            var attempt = new Attempt(UUID.randomUUID(), studentId, chapterId, score, 5, completed);
            results.saveCompleted(new AssessmentResult(attempt, difficulty, difficulty));
            completed = completed.plusSeconds(1);
        }
    }
}

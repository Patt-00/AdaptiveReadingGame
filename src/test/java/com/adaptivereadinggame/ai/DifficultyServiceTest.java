package com.adaptivereadinggame.ai;

import org.junit.jupiter.api.Test;
import java.util.List;

import static com.adaptivereadinggame.ai.DifficultyService.*;
import static org.junit.jupiter.api.Assertions.*;

class DifficultyServiceTest {
    private List<Section> history(Level level, int... scores) {
        return java.util.Arrays.stream(scores).mapToObj(score -> new Section(level, score, 5)).toList();
    }

    @Test void javaValidatesSectionScores() {
        assertThrows(IllegalArgumentException.class, () -> new Section(Level.MEDIUM, 6, 5));
        assertThrows(IllegalArgumentException.class, () -> new Section(Level.MEDIUM, -1, 5));
        assertThrows(IllegalArgumentException.class, () -> new Section(Level.MEDIUM, 0, 0));
    }

    @Test void coldStartDoesNotCallPython() {
        var service = new DifficultyService(prompt -> { fail("Model should not be called"); return Action.INCREASE; });
        assertEquals("cold_start", service.decide(Level.MEDIUM, List.of()).source());
        assertEquals(Level.MEDIUM, service.decide(Level.MEDIUM, history(Level.MEDIUM, 5, 5)).nextDifficulty());
    }

    @Test void summarizesOnlyTheLatestConsecutiveSectionsAtCurrentDifficulty() {
        var service = new DifficultyService(prompt -> Action.KEEP);
        var summary = service.summarize(Level.MEDIUM, List.of(new Section(Level.MEDIUM, 5, 5),
                new Section(Level.EASY, 5, 5), new Section(Level.MEDIUM, 3, 5)));
        assertEquals(List.of(60.0), summary.recentAccuracyPercent());
        assertEquals(List.of(40.0, 60.0, 80.0),
                service.summarize(Level.MEDIUM, history(Level.MEDIUM, 5, 2, 3, 4)).recentAccuracyPercent());
    }

    @Test void acceptsARecommendationSupportedByTheRecentAccuracy() {
        var service = new DifficultyService(prompt -> {
            assertTrue(prompt.input().contains("80%, 80%, 100%"));
            assertTrue(prompt.input().contains("improving"));
            return Action.INCREASE;
        });
        var decision = service.decide(Level.MEDIUM, history(Level.MEDIUM, 4, 4, 5));
        assertEquals(Level.HARD, decision.nextDifficulty());
        assertEquals("qwen", decision.source());
    }

    @Test void rejectsSuggestionsUnsupportedByJavaThresholds() {
        var increase = new DifficultyService(prompt -> Action.INCREASE)
                .decide(Level.MEDIUM, history(Level.MEDIUM, 3, 3, 3));
        assertEquals(Action.INCREASE, increase.suggestedAction());
        assertEquals(Action.KEEP, increase.appliedAction());
        var decrease = new DifficultyService(prompt -> Action.DECREASE)
                .decide(Level.MEDIUM, history(Level.MEDIUM, 4, 4, 5));
        assertEquals(Action.KEEP, decrease.appliedAction());
    }

    @Test void acceptsSupportedDecrease() {
        assertEquals(Level.EASY, new DifficultyService(prompt -> Action.DECREASE)
                .decide(Level.MEDIUM, history(Level.MEDIUM, 2, 1, 1)).nextDifficulty());
    }

    @Test void clampsRecommendationsAtDifficultyBounds() {
        assertEquals(Action.KEEP, new DifficultyService(prompt -> Action.INCREASE)
                .decide(Level.HARD, history(Level.HARD, 5, 5, 5)).appliedAction());
        assertEquals(Action.KEEP, new DifficultyService(prompt -> Action.DECREASE)
                .decide(Level.EASY, history(Level.EASY, 1, 1, 1)).appliedAction());
    }

    @Test void fallsBackToJavaBaselineWhenModelFails() {
        var service = new DifficultyService(prompt -> { throw new java.io.IOException("Unavailable"); });
        assertEquals(Level.HARD, service.decide(Level.MEDIUM, history(Level.MEDIUM, 3, 3, 4)).nextDifficulty());
        assertEquals(Level.MEDIUM, service.decide(Level.MEDIUM, history(Level.MEDIUM, 3, 3, 3)).nextDifficulty());
        assertEquals(Level.EASY, service.decide(Level.MEDIUM, history(Level.MEDIUM, 3, 3, 2)).nextDifficulty());
        assertEquals("rules_fallback", service.decide(Level.MEDIUM, history(Level.MEDIUM, 3, 3, 2)).source());
    }

    @Test void fallsBackWhenTheModelReturnsNull() {
        assertEquals("rules_fallback", new DifficultyService(prompt -> null)
                .decide(Level.MEDIUM, history(Level.MEDIUM, 3, 3, 3)).source());
    }
}

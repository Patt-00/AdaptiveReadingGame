package com.adaptivereadinggame.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.adaptivereadinggame.ai.DifficultyService.*;
import static org.junit.jupiter.api.Assertions.*;

class DifficultyServiceTest {
    private List<Section> history(Level level, int... scores) {
        return java.util.Arrays.stream(scores).mapToObj(n -> new Section(level, n, 5)).toList();
    }
    @Test void javaScoresAnswers() {
        assertEquals(new Section(Level.MEDIUM, 2, 3), Section.fromAnswers(Level.MEDIUM, List.of(true, false, true)));
    }
    @Test void javaChecksAnswerKeyAndRejectsInvalidSelections() {
        var questions = List.of(new ComprehensionQuestion(3, 1), new ComprehensionQuestion(3, 2));
        assertEquals(new Section(Level.MEDIUM, 1, 2), Section.grade(Level.MEDIUM, questions, List.of(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> Section.grade(Level.MEDIUM, questions, List.of(1, 3)));
        assertThrows(IllegalArgumentException.class, () -> Section.grade(Level.MEDIUM, questions, List.of(1)));
    }
    @Test void invalidScoresRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Section(Level.MEDIUM, 6, 5));
        assertThrows(IllegalArgumentException.class, () -> new Section(Level.MEDIUM, -1, 5));
        assertThrows(IllegalArgumentException.class, () -> new Section(Level.MEDIUM, 0, 0));
    }
    @Test void coldStartDoesNotCallPython() {
        var service = new DifficultyService(prompt -> { fail("Model should not be called"); return Action.INCREASE; });
        assertEquals("cold_start", service.decide(Level.MEDIUM, List.of()).source());
        assertEquals(Level.MEDIUM, service.decide(Level.MEDIUM, history(Level.MEDIUM, 5, 5)).nextDifficulty());
    }
    @Test void javaSummarizesOnlyCurrentContiguousRun() {
        var service = new DifficultyService(prompt -> Action.KEEP);
        var summary = service.summarize(Level.MEDIUM, List.of(new Section(Level.MEDIUM, 5,5),
                new Section(Level.EASY, 5,5), new Section(Level.MEDIUM, 3,5)));
        assertEquals(List.of(60.0), summary.recentAccuracyPercent());
        assertEquals(List.of(40.0, 60.0, 80.0), service.summarize(Level.MEDIUM, history(Level.MEDIUM, 5,2,3,4)).recentAccuracyPercent());
    }
    @Test void javaConstructsPromptAndAcceptsSupportedSuggestion() {
        var service = new DifficultyService(prompt -> {
            assertTrue(prompt.input().contains("80%, 80%, 100%"));
            assertTrue(prompt.input().contains("improving")); return Action.INCREASE;
        });
        var result = service.decide(Level.MEDIUM, history(Level.MEDIUM, 4,4,5));
        assertEquals(Level.HARD, result.nextDifficulty()); assertEquals("qwen", result.source());
    }
    @Test void javaVetoesUnsupportedModelSuggestions() {
        var result = new DifficultyService(prompt -> Action.INCREASE).decide(Level.MEDIUM, history(Level.MEDIUM, 3,3,3));
        assertEquals(Action.INCREASE, result.suggestedAction()); assertEquals(Action.KEEP, result.appliedAction());
        result = new DifficultyService(prompt -> Action.DECREASE).decide(Level.MEDIUM, history(Level.MEDIUM, 4,4,5));
        assertEquals(Action.KEEP, result.appliedAction());
    }
    @Test void javaAllowsSupportedDecrease() {
        assertEquals(Level.EASY, new DifficultyService(prompt -> Action.DECREASE)
                .decide(Level.MEDIUM, history(Level.MEDIUM, 2,1,1)).nextDifficulty());
    }
    @Test void javaKeepsDifficultyWithinBounds() {
        assertEquals(Action.KEEP, new DifficultyService(prompt -> Action.INCREASE)
                .decide(Level.HARD, history(Level.HARD, 5,5,5)).appliedAction());
        assertEquals(Action.KEEP, new DifficultyService(prompt -> Action.DECREASE)
                .decide(Level.EASY, history(Level.EASY, 1,1,1)).appliedAction());
    }
    @Test void javaFallbackUsesLatestScore() {
        var service = new DifficultyService(prompt -> { throw new java.io.IOException("Unavailable"); });
        assertEquals(Level.HARD, service.decide(Level.MEDIUM, history(Level.MEDIUM, 3,3,4)).nextDifficulty());
        assertEquals(Level.MEDIUM, service.decide(Level.MEDIUM, history(Level.MEDIUM, 3,3,3)).nextDifficulty());
        assertEquals(Level.EASY, service.decide(Level.MEDIUM, history(Level.MEDIUM, 3,3,2)).nextDifficulty());
        assertEquals("rules_fallback", service.decide(Level.MEDIUM, history(Level.MEDIUM, 3,3,2)).source());
    }
    @Test void nullSuggestionUsesFallback() {
        assertEquals("rules_fallback", new DifficultyService(prompt -> null)
                .decide(Level.MEDIUM, history(Level.MEDIUM, 3,3,3)).source());
    }
}

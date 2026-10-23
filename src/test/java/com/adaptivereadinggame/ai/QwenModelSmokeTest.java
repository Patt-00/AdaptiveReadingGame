package com.adaptivereadinggame.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static com.adaptivereadinggame.ai.DifficultyService.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real-model smoke test. The local Python helper must report ready first. */
@EnabledIfSystemProperty(named = "arg.modelTests", matches = "true")
final class QwenModelSmokeTest {
    @Test void realModelReturnsAnAllowedJavaGuardedDecision() {
        var service = new DifficultyService(QwenClient.fromEnvironment());
        var history = List.of(new Section(Level.MEDIUM, 4, 5),
                new Section(Level.MEDIUM, 4, 5), new Section(Level.MEDIUM, 4, 5));
        Decision decision = service.decide(Level.MEDIUM, history);

        assertEquals("qwen", decision.source());
        assertNotNull(decision.suggestedAction());
        assertTrue(Math.abs(decision.nextDifficulty().ordinal() - Level.MEDIUM.ordinal()) <= 1);
    }
}

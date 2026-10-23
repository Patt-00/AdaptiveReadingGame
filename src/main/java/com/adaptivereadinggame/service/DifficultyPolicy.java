package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.DifficultyDecisionSource;
import java.util.UUID;

/** Java owns the final decision and validates optional model recommendations behind this boundary. */
@FunctionalInterface
public interface DifficultyPolicy {
    record Context(UUID studentId, UUID chapterId, Difficulty current, int correct, int total) {}
    record Recommendation(Difficulty nextDifficulty, DifficultyDecisionSource source) {
        public Recommendation {
            java.util.Objects.requireNonNull(nextDifficulty);
            java.util.Objects.requireNonNull(source);
        }
    }
    Difficulty nextDifficulty(Context context);

    default Recommendation recommend(Context context) {
        return new Recommendation(nextDifficulty(context), DifficultyDecisionSource.JAVA_RULES);
    }
}

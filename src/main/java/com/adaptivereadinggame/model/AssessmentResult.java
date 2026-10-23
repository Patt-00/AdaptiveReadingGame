package com.adaptivereadinggame.model;

import java.util.Objects;

/** Persist the score and the validated source of its next-difficulty decision. */
public record AssessmentResult(Attempt attempt, Difficulty assessedDifficulty, Difficulty nextDifficulty,
                              DifficultyDecisionSource decisionSource) {
    public AssessmentResult(Attempt attempt, Difficulty assessedDifficulty, Difficulty nextDifficulty) {
        this(attempt, assessedDifficulty, nextDifficulty, DifficultyDecisionSource.JAVA_RULES);
    }

    public AssessmentResult {
        Objects.requireNonNull(attempt); Objects.requireNonNull(assessedDifficulty); Objects.requireNonNull(nextDifficulty);
        Objects.requireNonNull(decisionSource);
    }
}

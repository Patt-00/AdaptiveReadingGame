package com.adaptivereadinggame.model;

import java.util.Objects;

/** Persist the difficulty decision with the original assessed chapter difficulty. */
public record AssessmentResult(Attempt attempt, Difficulty assessedDifficulty, Difficulty nextDifficulty) {
    public AssessmentResult {
        Objects.requireNonNull(attempt); Objects.requireNonNull(assessedDifficulty); Objects.requireNonNull(nextDifficulty);
    }
}

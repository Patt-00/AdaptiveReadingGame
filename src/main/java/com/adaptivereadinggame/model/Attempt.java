package com.adaptivereadinggame.model;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

public record Attempt(UUID id, UUID studentId, UUID materialId, int correctAnswers,
                      int totalQuestions, Instant completedAt) {
    public Attempt {
        Objects.requireNonNull(id); Objects.requireNonNull(studentId);
        Objects.requireNonNull(materialId); Objects.requireNonNull(completedAt);
        if (totalQuestions < 1 || totalQuestions > 100 || correctAnswers < 0 || correctAnswers > totalQuestions)
            throw new IllegalArgumentException("Invalid completed assessment score");
    }
    public double score() {
        return totalQuestions == 0 ? 0 : (double) correctAnswers / totalQuestions;
    }
}

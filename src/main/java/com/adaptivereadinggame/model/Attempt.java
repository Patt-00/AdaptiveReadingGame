package com.adaptivereadinggame.model;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

public record Attempt(UUID id, UUID studentId, UUID materialId, String checkpointId,
                      int correctAnswers, int totalQuestions, Instant completedAt) {
    public Attempt(UUID id, UUID studentId, UUID materialId, int correctAnswers,
                   int totalQuestions, Instant completedAt) {
        this(id, studentId, materialId, "", correctAnswers, totalQuestions, completedAt);
    }

    public Attempt {
        Objects.requireNonNull(id); Objects.requireNonNull(studentId);
        Objects.requireNonNull(materialId); Objects.requireNonNull(completedAt);
        checkpointId = Objects.requireNonNullElse(checkpointId, "");
        if (!checkpointId.isEmpty() && !checkpointId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))
            throw new IllegalArgumentException("A checkpoint ID must be a simple identifier.");
        if (totalQuestions < 1 || totalQuestions > 100 || correctAnswers < 0 || correctAnswers > totalQuestions)
            throw new IllegalArgumentException("Invalid completed assessment score");
    }
    public double score() {
        return totalQuestions == 0 ? 0 : (double) correctAnswers / totalQuestions;
    }
}

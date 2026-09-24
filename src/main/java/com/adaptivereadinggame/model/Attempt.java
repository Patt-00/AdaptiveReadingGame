package com.adaptivereadinggame.model;

import java.time.Instant;
import java.util.UUID;

public record Attempt(UUID id, UUID studentId, UUID materialId, int correctAnswers,
                      int totalQuestions, Instant completedAt) {
    public double score() {
        return totalQuestions == 0 ? 0 : (double) correctAnswers / totalQuestions;
    }
}

package com.adaptivereadinggame.model;

import java.util.UUID;

public record Student(UUID id, String displayName, ReadingLevel readingLevel,
                      int completedAttempts, double accuracy) {
    public Student {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("A student name is required.");
        }
        if (accuracy < 0 || accuracy > 1) {
            throw new IllegalArgumentException("Accuracy must be between 0 and 1.");
        }
    }
}

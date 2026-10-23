package com.adaptivereadinggame.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Question(UUID id, String prompt, List<String> choices, int correctChoiceIndex, String explanation) {
    public Question(UUID id, String prompt, List<String> choices, int correctChoiceIndex) {
        this(id, prompt, choices, correctChoiceIndex, "");
    }

    public Question {
        Objects.requireNonNull(id, "id");
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("A question prompt is required.");
        choices = List.copyOf(choices);
        if (choices.size() < 2 || choices.stream().anyMatch(choice -> choice == null || choice.isBlank()))
            throw new IllegalArgumentException("A question needs at least two non-empty answer choices.");
        if (correctChoiceIndex < 0 || correctChoiceIndex >= choices.size()) {
            throw new IllegalArgumentException("Correct choice must exist in choices.");
        }
        explanation = Objects.requireNonNullElse(explanation, "");
    }
}

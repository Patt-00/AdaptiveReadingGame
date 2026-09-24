package com.adaptivereadinggame.model;

import java.util.List;
import java.util.UUID;

public record Question(UUID id, String prompt, List<String> choices, int correctChoiceIndex) {
    public Question {
        choices = List.copyOf(choices);
        if (correctChoiceIndex < 0 || correctChoiceIndex >= choices.size()) {
            throw new IllegalArgumentException("Correct choice must exist in choices.");
        }
    }
}

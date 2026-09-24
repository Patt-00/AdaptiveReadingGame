package com.adaptivereadinggame.model;

import java.util.List;
import java.util.UUID;

public record ReadingMaterial(UUID id, String title, String passage, ReadingLevel level,
                              List<Question> questions) {
    public ReadingMaterial {
        questions = List.copyOf(questions);
    }
}

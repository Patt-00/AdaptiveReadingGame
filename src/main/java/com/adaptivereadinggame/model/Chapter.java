package com.adaptivereadinggame.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Prepared reading variant. Story-choice branching is a later gameplay feature. */
public record Chapter(UUID id, String title, String passage, Difficulty difficulty, List<Question> questions) {
    public Chapter {
        Objects.requireNonNull(id); Objects.requireNonNull(difficulty);
        if (title == null || title.isBlank() || passage == null || passage.isBlank())
            throw new IllegalArgumentException("Title and passage required");
        questions = List.copyOf(questions);
        if (questions.isEmpty() || questions.size() > 100 || questions.stream().anyMatch(q -> q.id() == null)
                || questions.stream().map(Question::id).distinct().count() != questions.size())
            throw new IllegalArgumentException("Require 1..100 uniquely identified questions");
    }
}

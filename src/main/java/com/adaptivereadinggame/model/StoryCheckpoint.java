package com.adaptivereadinggame.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One comprehension checkpoint, its three adaptive question variants, and narrative outcomes. */
public record StoryCheckpoint(String id, String nodeId, String passage,
                              Map<Difficulty, List<Question>> questionsByDifficulty,
                              String correctNextNodeId, String incorrectNextNodeId) {
    private static final Set<Difficulty> ALL_DIFFICULTIES = Set.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);

    public StoryCheckpoint {
        requireIdentifier(id, "checkpoint ID");
        requireIdentifier(nodeId, "checkpoint node ID");
        if (passage == null || passage.isBlank()) throw new IllegalArgumentException("A checkpoint passage is required.");
        requireIdentifier(correctNextNodeId, "correct outcome node ID");
        requireIdentifier(incorrectNextNodeId, "incorrect outcome node ID");
        if (correctNextNodeId.equals(incorrectNextNodeId))
            throw new IllegalArgumentException("Correct and incorrect outcomes must have distinct story nodes.");
        Objects.requireNonNull(questionsByDifficulty, "questionsByDifficulty");
        EnumMap<Difficulty, List<Question>> copy = new EnumMap<>(Difficulty.class);
        questionsByDifficulty.forEach((difficulty, questions) -> {
            Objects.requireNonNull(difficulty, "question difficulty");
            List<Question> immutable = List.copyOf(questions);
            if (immutable.isEmpty() || immutable.size() > 100)
                throw new IllegalArgumentException("Each difficulty variant needs 1..100 questions.");
            if (immutable.stream().anyMatch(question -> !id.equals(question.checkpointId())
                    || question.difficulty() != difficulty))
                throw new IllegalArgumentException("A question must match its checkpoint and difficulty.");
            if (immutable.stream().map(Question::id).distinct().count() != immutable.size())
                throw new IllegalArgumentException("Question IDs must be unique within a difficulty variant.");
            copy.put(difficulty, immutable);
        });
        if (!copy.keySet().equals(ALL_DIFFICULTIES))
            throw new IllegalArgumentException("A checkpoint needs EASY, MEDIUM, and HARD question variants.");
        questionsByDifficulty = Map.copyOf(copy);
    }

    public List<Question> questions(Difficulty difficulty) {
        return questionsByDifficulty.get(Objects.requireNonNull(difficulty));
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))
            throw new IllegalArgumentException(label + " must use letters, numbers, dots, underscores, or hyphens.");
    }
}

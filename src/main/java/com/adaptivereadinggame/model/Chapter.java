package com.adaptivereadinggame.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Prepared reading variant with its passage and comprehension questions. */
public record Chapter(UUID id, String title, String passage, Difficulty difficulty, List<Question> questions,
                      List<StoryCheckpoint> checkpoints) {
    public Chapter(UUID id, String title, String passage, Difficulty difficulty, List<Question> questions) {
        this(id, title, passage, difficulty, questions, List.of());
    }

    public static Chapter adaptive(UUID id, String title, String passage, Difficulty initialDifficulty,
                                   List<Question> questions, List<StoryCheckpoint> checkpoints) {
        return new Chapter(id, title, passage, initialDifficulty, questions, checkpoints);
    }

    public Chapter {
        Objects.requireNonNull(id); Objects.requireNonNull(difficulty);
        if (title == null || title.isBlank() || passage == null || passage.isBlank())
            throw new IllegalArgumentException("Title and passage required");
        questions = List.copyOf(questions);
        checkpoints = checkpoints.stream().sorted(java.util.Comparator.comparing(StoryCheckpoint::id)).toList();
        if (questions.isEmpty() || questions.size() > 600 || questions.stream().anyMatch(q -> q.id() == null)
                || questions.stream().map(Question::id).distinct().count() != questions.size())
            throw new IllegalArgumentException("Require 1..600 uniquely identified chapter questions");
        if (checkpoints.stream().map(StoryCheckpoint::id).distinct().count() != checkpoints.size()
                || checkpoints.stream().map(StoryCheckpoint::nodeId).distinct().count() != checkpoints.size())
            throw new IllegalArgumentException("Checkpoint IDs and reading-check nodes must be unique within a chapter.");
        if (!checkpoints.isEmpty()) {
            var checkpointQuestions = checkpoints.stream()
                    .flatMap(checkpoint -> checkpoint.questionsByDifficulty().values().stream())
                    .flatMap(List::stream).toList();
            if (checkpointQuestions.size() != questions.size()
                    || !checkpointQuestions.stream().map(Question::id).collect(java.util.stream.Collectors.toSet())
                            .equals(questions.stream().map(Question::id).collect(java.util.stream.Collectors.toSet())))
                throw new IllegalArgumentException("Chapter questions must exactly match its checkpoint variants.");
        } else if (questions.size() > 100) {
            throw new IllegalArgumentException("A legacy chapter supports at most 100 questions.");
        }
    }

    public boolean adaptive() { return !checkpoints.isEmpty(); }

    public List<Question> questions(String checkpointId, Difficulty selectedDifficulty) {
        if (checkpoints.isEmpty()) return questions;
        return checkpoints.stream().filter(checkpoint -> checkpoint.id().equals(checkpointId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown reading checkpoint."))
                .questions(selectedDifficulty);
    }

    public java.util.Optional<StoryCheckpoint> checkpointAtNode(String nodeId) {
        return checkpoints.stream().filter(checkpoint -> checkpoint.nodeId().equals(nodeId)).findFirst();
    }

    public java.util.Optional<StoryCheckpoint> checkpoint(String checkpointId) {
        return checkpoints.stream().filter(checkpoint -> checkpoint.id().equals(checkpointId)).findFirst();
    }
}

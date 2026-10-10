package com.adaptivereadinggame.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable progress snapshot. Stored drafts, not snapshots, own first answers. */
public record GameState(UUID studentId, UUID chapterId, String storyNodeId,
                        UUID assessmentSessionId, int questionIndex,
                        Map<UUID, Integer> firstAnswers) {
    public GameState {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(chapterId, "chapterId");
        if (storyNodeId == null || storyNodeId.isBlank())
            throw new IllegalArgumentException("A game state needs a story node ID");
        if (questionIndex < 0)
            throw new IllegalArgumentException("Question index must not be negative");
        firstAnswers = Map.copyOf(firstAnswers);
        if (firstAnswers.values().stream().anyMatch(selectedIndex -> selectedIndex < 0))
            throw new IllegalArgumentException("Submitted option indexes must not be negative");
        if (assessmentSessionId == null && (questionIndex != 0 || !firstAnswers.isEmpty()))
            throw new IllegalArgumentException("Before assessment starts, index is zero and submitted answers are empty");
    }
}

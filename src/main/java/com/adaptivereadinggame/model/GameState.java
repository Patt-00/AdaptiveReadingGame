package com.adaptivereadinggame.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable progress snapshot. Stored drafts, not snapshots, own first answers. */
public record GameState(UUID studentId, UUID chapterId, String storyNodeId,
                        UUID assessmentSessionId, int questionIndex,
                        Map<UUID, Integer> firstAnswers, String routeId,
                        String activeCheckpointId, Difficulty activeDifficulty,
                        String lastCompletedCheckpointId) {
    public GameState(UUID studentId, UUID chapterId, String storyNodeId,
                     UUID assessmentSessionId, int questionIndex, Map<UUID, Integer> firstAnswers) {
        this(studentId, chapterId, storyNodeId, assessmentSessionId, questionIndex,
                firstAnswers, "", "", null, "");
    }

    public GameState(UUID studentId, UUID chapterId, String storyNodeId,
                     UUID assessmentSessionId, int questionIndex, Map<UUID, Integer> firstAnswers,
                     String routeId, String activeCheckpointId, Difficulty activeDifficulty) {
        this(studentId, chapterId, storyNodeId, assessmentSessionId, questionIndex, firstAnswers,
                routeId, activeCheckpointId, activeDifficulty, "");
    }

    public GameState {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(chapterId, "chapterId");
        if (storyNodeId == null || storyNodeId.isBlank())
            throw new IllegalArgumentException("A game state needs a story node ID");
        if (questionIndex < 0)
            throw new IllegalArgumentException("Question index must not be negative");
        firstAnswers = Map.copyOf(firstAnswers);
        routeId = Objects.requireNonNullElse(routeId, "");
        activeCheckpointId = Objects.requireNonNullElse(activeCheckpointId, "");
        lastCompletedCheckpointId = Objects.requireNonNullElse(lastCompletedCheckpointId, "");
        if (!routeId.isEmpty() && !routeId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            throw new IllegalArgumentException("A saved route ID must be a simple identifier.");
        if (!activeCheckpointId.isEmpty() && !activeCheckpointId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))
            throw new IllegalArgumentException("An active checkpoint ID must be a simple identifier.");
        if (!lastCompletedCheckpointId.isEmpty()
                && !lastCompletedCheckpointId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))
            throw new IllegalArgumentException("A completed checkpoint ID must be a simple identifier.");
        if (firstAnswers.values().stream().anyMatch(selectedIndex -> selectedIndex < 0))
            throw new IllegalArgumentException("Submitted option indexes must not be negative");
        if (assessmentSessionId == null && (questionIndex != 0 || !firstAnswers.isEmpty()))
            throw new IllegalArgumentException("Before assessment starts, index is zero and submitted answers are empty");
        if (assessmentSessionId != null && !activeCheckpointId.isEmpty() && activeDifficulty == null)
            throw new IllegalArgumentException("An active checkpoint assessment needs its selected difficulty.");
    }
}

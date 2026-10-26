package com.adaptivereadinggame.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Story node IDs, not UI indexes, identify stable checkpoints. */
public record SaveGame(UUID studentId, int slot, UUID chapterId, String storyNodeId,
                       Difficulty difficulty, Instant savedAt, String routeId, String checkpointId) {
    public SaveGame(UUID studentId, int slot, UUID chapterId, String storyNodeId,
                    Difficulty difficulty, Instant savedAt) {
        this(studentId, slot, chapterId, storyNodeId, difficulty, savedAt, "", "");
    }

    public SaveGame {
        Objects.requireNonNull(studentId); Objects.requireNonNull(chapterId);
        Objects.requireNonNull(difficulty); Objects.requireNonNull(savedAt);
        routeId = Objects.requireNonNullElse(routeId, "");
        checkpointId = Objects.requireNonNullElse(checkpointId, "");
        if (!routeId.isEmpty() && !routeId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            throw new IllegalArgumentException("A saved route ID must be a simple identifier.");
        if (!checkpointId.isEmpty() && !checkpointId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))
            throw new IllegalArgumentException("A saved checkpoint ID must be a simple identifier.");
        if (slot < 1 || slot > 3 || storyNodeId == null || storyNodeId.isBlank())
            throw new IllegalArgumentException("Require slot 1..3 and a story node ID");
    }
}

package com.adaptivereadinggame.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Story node IDs, not UI indexes, identify stable checkpoints. */
public record SaveGame(UUID studentId, int slot, UUID chapterId, String storyNodeId,
                       Difficulty difficulty, Instant savedAt) {
    public SaveGame {
        Objects.requireNonNull(studentId); Objects.requireNonNull(chapterId);
        Objects.requireNonNull(difficulty); Objects.requireNonNull(savedAt);
        if (slot < 1 || slot > 3 || storyNodeId == null || storyNodeId.isBlank())
            throw new IllegalArgumentException("Require slot 1..3 and a story node ID");
    }
}

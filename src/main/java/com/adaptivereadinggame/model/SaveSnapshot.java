package com.adaptivereadinggame.model;

import java.util.Objects;

/** The existing slot details plus the exact full progress to resume. */
public record SaveSnapshot(SaveGame checkpoint, GameState gameState) {
    public SaveSnapshot {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(gameState, "gameState");
        if (!checkpoint.studentId().equals(gameState.studentId())
                || !checkpoint.chapterId().equals(gameState.chapterId())
                || !checkpoint.storyNodeId().equals(gameState.storyNodeId())
                || !checkpoint.routeId().equals(gameState.routeId())
                || !checkpoint.checkpointId().equals(gameState.activeCheckpointId())
                || gameState.activeDifficulty() != null && checkpoint.difficulty() != gameState.activeDifficulty())
            throw new IllegalArgumentException("Checkpoint and game state must describe the same learner, chapter, and node");
    }
}

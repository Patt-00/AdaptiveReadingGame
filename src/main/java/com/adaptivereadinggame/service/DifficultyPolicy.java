package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.Difficulty;
import java.util.UUID;

/** Java owns the final decision. A future model adapter must remain behind this boundary. */
@FunctionalInterface
public interface DifficultyPolicy {
    record Context(UUID studentId, UUID chapterId, Difficulty current, int correct, int total) {}
    Difficulty nextDifficulty(Context context);
}

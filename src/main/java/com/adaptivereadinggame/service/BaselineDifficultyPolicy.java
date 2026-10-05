package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.Difficulty;
import java.util.Objects;

/** Generalizes the proposed five-question rule to percentages. Not a validated assessment. */
public final class BaselineDifficultyPolicy implements DifficultyPolicy {
    @Override public Difficulty nextDifficulty(Context context) {
        Objects.requireNonNull(context);
        return nextDifficulty(context.current(), context.correct(), context.total());
    }
    public Difficulty nextDifficulty(Difficulty current, int correct, int total) {
        Objects.requireNonNull(current);
        if (total < 1 || correct < 0 || correct > total) throw new IllegalArgumentException("Invalid score");
        double accuracy = (double) correct / total;
        return current.move(accuracy >= .8 ? 1 : accuracy < .6 ? -1 : 0);
    }
}

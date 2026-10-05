package com.adaptivereadinggame.model;

/** New gameplay difficulty, separate from the legacy dashboard's ReadingLevel. */
public enum Difficulty {
    EASY, MEDIUM, HARD;

    public Difficulty move(int step) {
        if (step < -1 || step > 1) throw new IllegalArgumentException("Change at most one level");
        return values()[Math.clamp(ordinal() + step, 0, values().length - 1)];
    }
}

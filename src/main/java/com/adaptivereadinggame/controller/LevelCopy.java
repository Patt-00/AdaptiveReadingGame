package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.model.Difficulty;
import java.util.Locale;

/**
 * Shared player wording for reading levels. A level describes the comprehension questions only:
 * the story text is the same for every player, so screens must never imply a story has a level.
 */
final class LevelCopy {
    private LevelCopy() {}

    static String questionLevel(Difficulty difficulty) {
        return "Question level: " + titleCase(difficulty);
    }

    static String nextQuestionLevel(Difficulty difficulty) {
        return "Next question level: " + titleCase(difficulty);
    }

    static String startsAt(Difficulty difficulty) {
        return "Reading checks start at " + titleCase(difficulty);
    }

    private static String titleCase(Difficulty difficulty) {
        String lower = difficulty.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

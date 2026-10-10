package com.adaptivereadinggame.model;

/** A narrative choice, never a scored comprehension answer. */
public record StoryChoice(String choiceId, String choiceText, String nextNodeId) {
    public StoryChoice {
        if (choiceId == null || choiceId.isBlank())
            throw new IllegalArgumentException("A story choice needs an ID");
        if (choiceText == null || choiceText.isBlank())
            throw new IllegalArgumentException("A story choice needs button text");
        if (nextNodeId == null || nextNodeId.isBlank())
            throw new IllegalArgumentException("A story choice needs its destination node ID");
    }
}

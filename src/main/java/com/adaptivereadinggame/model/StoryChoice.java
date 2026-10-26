package com.adaptivereadinggame.model;

/** A narrative choice, never a scored comprehension answer. */
public record StoryChoice(String choiceId, String choiceText, String nextNodeId, String routeId) {
    public StoryChoice(String choiceId, String choiceText, String nextNodeId) {
        this(choiceId, choiceText, nextNodeId, "");
    }

    public StoryChoice {
        if (choiceId == null || choiceId.isBlank())
            throw new IllegalArgumentException("A story choice needs an ID");
        if (choiceText == null || choiceText.isBlank())
            throw new IllegalArgumentException("A story choice needs button text");
        if (nextNodeId == null || nextNodeId.isBlank())
            throw new IllegalArgumentException("A story choice needs its destination node ID");
        routeId = java.util.Objects.requireNonNullElse(routeId, "");
        if (!routeId.isEmpty() && !routeId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            throw new IllegalArgumentException("A route ID must use letters, numbers, dots, underscores, or hyphens.");
    }
}

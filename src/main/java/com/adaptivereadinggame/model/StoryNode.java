package com.adaptivereadinggame.model;

import java.util.List;
import java.util.Objects;

/** One prepared dialogue/thought with exactly one kind of next action. */
public record StoryNode(String storyNodeId, String speakerName, String dialogueText,
                        boolean thought, List<StoryChoice> storyChoices,
                        String nextNodeId, boolean readingCheck) {
    public StoryNode {
        if (storyNodeId == null || storyNodeId.isBlank())
            throw new IllegalArgumentException("A story node needs an ID");
        Objects.requireNonNull(speakerName, "speakerName"); // Empty is allowed for narration.
        if (dialogueText == null || dialogueText.isBlank())
            throw new IllegalArgumentException("A story node needs dialogue text");
        storyChoices = List.copyOf(storyChoices);
        Objects.requireNonNull(nextNodeId, "nextNodeId");
        if (!nextNodeId.isEmpty() && nextNodeId.isBlank())
            throw new IllegalArgumentException("Use an empty destination for the end of a path");
        if (storyChoices.stream().map(StoryChoice::choiceId).distinct().count() != storyChoices.size())
            throw new IllegalArgumentException("Choice IDs must be unique within a story node");
        int actions = (storyChoices.isEmpty() ? 0 : 1) + (nextNodeId.isEmpty() ? 0 : 1)
                + (readingCheck ? 1 : 0);
        if (actions > 1)
            throw new IllegalArgumentException("Choose only one next action: choices, next node, or reading check");
    }
}

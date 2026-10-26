package com.adaptivereadinggame.model;

import java.util.List;
import java.util.Objects;

/** One prepared dialogue/thought with exactly one kind of next action. */
public record StoryNode(String storyNodeId, String speakerName, String dialogueText,
                        boolean thought, List<StoryChoice> storyChoices,
                        String nextNodeId, boolean readingCheck, String speakerAssetKey,
                        String checkpointId, String sceneAssetKey, String cgAssetKey,
                        boolean storyComplete) {
    public StoryNode(String storyNodeId, String speakerName, String dialogueText,
                     boolean thought, List<StoryChoice> storyChoices,
                     String nextNodeId, boolean readingCheck) {
        this(storyNodeId, speakerName, dialogueText, thought, storyChoices,
                nextNodeId, readingCheck, "", "", "", "", false);
    }

    public StoryNode(String storyNodeId, String speakerName, String dialogueText,
                     boolean thought, List<StoryChoice> storyChoices,
                     String nextNodeId, boolean readingCheck, String speakerAssetKey) {
        this(storyNodeId, speakerName, dialogueText, thought, storyChoices,
                nextNodeId, readingCheck, speakerAssetKey, "", "", "", false);
    }

    public StoryNode {
        if (storyNodeId == null || storyNodeId.isBlank())
            throw new IllegalArgumentException("A story node needs an ID");
        Objects.requireNonNull(speakerName, "speakerName"); // Empty is allowed for narration.
        if (dialogueText == null || dialogueText.isBlank())
            throw new IllegalArgumentException("A story node needs dialogue text");
        speakerAssetKey = normalizedAssetKey(speakerAssetKey, "speaker");
        checkpointId = Objects.requireNonNullElse(checkpointId, "");
        if (!checkpointId.isEmpty() && !checkpointId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))
            throw new IllegalArgumentException("A checkpoint key must be a simple identifier.");
        if (!checkpointId.isEmpty() && !readingCheck)
            throw new IllegalArgumentException("A checkpoint node must be marked as a reading check.");
        sceneAssetKey = normalizedAssetKey(sceneAssetKey, "scene");
        cgAssetKey = normalizedAssetKey(cgAssetKey, "CG");
        storyChoices = List.copyOf(storyChoices);
        Objects.requireNonNull(nextNodeId, "nextNodeId");
        if (!nextNodeId.isEmpty() && nextNodeId.isBlank())
            throw new IllegalArgumentException("Use an empty destination for the end of a path");
        if (storyChoices.stream().map(StoryChoice::choiceId).distinct().count() != storyChoices.size())
            throw new IllegalArgumentException("Choice IDs must be unique within a story node");
        int actions = (storyChoices.isEmpty() ? 0 : 1) + (nextNodeId.isEmpty() ? 0 : 1)
                + (readingCheck ? 1 : 0) + (storyComplete ? 1 : 0);
        if (actions > 1)
            throw new IllegalArgumentException("Choose only one next action: choices, next node, reading check, or story completion");
        if (storyComplete && !storyChoices.isEmpty())
            throw new IllegalArgumentException("A completed story node cannot have choices.");
    }

    private static String normalizedAssetKey(String value, String label) {
        String key = Objects.requireNonNullElse(value, "");
        if (!key.isEmpty() && !key.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            throw new IllegalArgumentException("A " + label + " asset key must be a simple filename key.");
        return key;
    }
}

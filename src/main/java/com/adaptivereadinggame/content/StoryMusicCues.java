package com.adaptivereadinggame.content;

import com.adaptivereadinggame.audio.GameAudio.Track;
import com.adaptivereadinggame.model.StoryNode;
import java.util.UUID;

/** Provisional soundtrack cues for the supplied draft, without changing its story graph. */
public final class StoryMusicCues {
    private static final UUID DRAFT = StoryPackLoader.stableId("chapter", "hailey-investigation-reading-draft");
    private static final java.util.Set<UUID> COMPLETED = java.util.Set.of(
            StoryPackLoader.stableId("chapter", "hailey-investigation-complete-v1"),
            StoryPackLoader.stableId("chapter", "hailey-investigation-final-v2"));
    private StoryMusicCues() {}
    public static Track forNode(UUID chapterId, StoryNode node) {
        if (!(DRAFT.equals(chapterId) || COMPLETED.contains(chapterId)) || node.readingCheck() || node.storyComplete()) return Track.SILENT;
        String id = node.storyNodeId();
        if (id.startsWith("poisoning-") || id.equals("poisoning") || id.startsWith("accusation-")
                || id.startsWith("conclusion-") || id.startsWith("search-")
                || id.startsWith("ending-") || id.startsWith("aftermath-")) return Track.TENSE;
        return Track.INVESTIGATION;
    }
}

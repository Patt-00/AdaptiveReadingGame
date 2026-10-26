package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.StoryNode;
import com.adaptivereadinggame.model.StoryCheckpoint;
import java.util.List;
import java.util.UUID;

/** Writes a prepared chapter's complete scene graph before a learner can use it. */
public interface StoryContentRepository extends StoryRepository {
    void saveStoryNodes(UUID chapterId, List<StoryNode> nodes, String firstNodeId);

    default void saveStoryNodes(UUID chapterId, List<StoryNode> nodes, String firstNodeId,
                                List<StoryCheckpoint> checkpoints) {
        if (checkpoints != null && !checkpoints.isEmpty())
            throw new UnsupportedOperationException("This story repository does not store adaptive checkpoints.");
        saveStoryNodes(chapterId, nodes, firstNodeId);
    }
}

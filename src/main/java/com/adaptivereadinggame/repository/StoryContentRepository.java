package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.StoryNode;
import java.util.List;
import java.util.UUID;

/** Writes a prepared chapter's complete scene graph before a learner can use it. */
public interface StoryContentRepository extends StoryRepository {
    void saveStoryNodes(UUID chapterId, List<StoryNode> nodes, String firstNodeId);
}

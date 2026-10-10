package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.StoryNode;
import java.util.Optional;
import java.util.UUID;

/** Prepared narrative data, scoped by the exact chapter variant ID. */
public interface StoryRepository {
    Optional<String> firstNodeId(UUID chapterId);
    Optional<StoryNode> findNode(UUID chapterId, String storyNodeId);
}

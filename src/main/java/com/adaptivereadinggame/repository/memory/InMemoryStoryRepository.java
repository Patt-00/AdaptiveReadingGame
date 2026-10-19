package com.adaptivereadinggame.repository.memory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.adaptivereadinggame.model.StoryChoice;
import com.adaptivereadinggame.model.StoryNode;
import com.adaptivereadinggame.repository.StoryRepository;

/**
 * In-memory story storage. It only answers two questions: where a chapter starts, and what a
 * given story node is. It never changes a node and never invents one. Dev 3's MySQL version
 * will replace it later, so keep every rule here in the StoryRepository contract.
 */
public final class InMemoryStoryRepository implements StoryRepository {
    private record Script(String firstNodeId, Map<String, StoryNode> nodes) {}

    private final Map<UUID, Script> scripts = new ConcurrentHashMap<>();

    /** A repository that already holds the Chapter 0 demo story. */
    public static InMemoryStoryRepository withSampleChapter() {
        InMemoryStoryRepository repository = new InMemoryStoryRepository();
        repository.register(SampleChapter.chapterId, SampleChapter.FIRST_NODE_ID, SampleChapter.nodes());
        return repository;
    }

    /**
     * Adds one chapter's story. Checks the whole story up front, so a typo in a node ID
     * fails at startup instead of in the middle of a game.
     */
    public void register(UUID chapterId, String firstNodeId, Map<String, StoryNode> nodes) {
        if (chapterId == null || firstNodeId == null || nodes == null || nodes.isEmpty())
            throw new IllegalArgumentException("Chapter, first node and nodes are required.");
        if (!nodes.containsKey(firstNodeId))
            throw new IllegalArgumentException("First node does not exist: " + firstNodeId);
        for (Map.Entry<String, StoryNode> entry : nodes.entrySet()) {
            StoryNode node = entry.getValue();
            if (node == null || !entry.getKey().equals(node.storyNodeId()))
                throw new IllegalArgumentException("Map key must match the node's own ID: " + entry.getKey());
            if (!node.nextNodeId().isEmpty() && !nodes.containsKey(node.nextNodeId()))
                throw new IllegalArgumentException(node.storyNodeId() + " points to a missing node: " + node.nextNodeId());
            for (StoryChoice choice : node.storyChoices())
                if (!nodes.containsKey(choice.nextNodeId()))
                    throw new IllegalArgumentException(node.storyNodeId() + " has a choice to a missing node: " + choice.nextNodeId());
        }
        if (scripts.putIfAbsent(chapterId, new Script(firstNodeId, Map.copyOf(nodes))) != null)
            throw new IllegalStateException("Story already registered for chapter " + chapterId);
    }

    @Override
    public Optional<String> firstNodeId(UUID chapterId) {
        if (chapterId == null) return Optional.empty();
        Script script = scripts.get(chapterId);
        return script == null ? Optional.empty() : Optional.of(script.firstNodeId());
    }

    @Override
    public Optional<StoryNode> findNode(UUID chapterId, String storyNodeId) {
        if (chapterId == null || storyNodeId == null) return Optional.empty();
        Script script = scripts.get(chapterId);
        return script == null ? Optional.empty() : Optional.ofNullable(script.nodes().get(storyNodeId));
    }
}
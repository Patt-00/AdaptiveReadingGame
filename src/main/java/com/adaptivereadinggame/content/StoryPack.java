package com.adaptivereadinggame.content;

import com.adaptivereadinggame.model.Chapter;
import com.adaptivereadinggame.model.StoryGraphValidator;
import com.adaptivereadinggame.model.StoryNode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Validated, code-independent story content ready for repository import. */
public record StoryPack(int formatVersion, List<ChapterPack> chapters) {
    public static final int CURRENT_FORMAT_VERSION = 2;

    public StoryPack(List<ChapterPack> chapters) {
        this(1, chapters);
    }

    public StoryPack {
        if (formatVersion < 1 || formatVersion > CURRENT_FORMAT_VERSION)
            throw new IllegalArgumentException("Unsupported story-pack format version.");
        chapters = List.copyOf(chapters);
        if (chapters.isEmpty()) throw new IllegalArgumentException("A story pack must contain at least one chapter.");
        Set<String> sourceIds = new HashSet<>();
        Set<UUID> chapterIds = new HashSet<>();
        for (ChapterPack chapter : chapters) {
            if (!sourceIds.add(chapter.sourceId()) || !chapterIds.add(chapter.chapter().id()))
                throw new IllegalArgumentException("Chapter IDs must be unique within a story pack.");
        }
    }

    public record ChapterPack(String sourceId, Chapter chapter, String startNodeId, List<StoryNode> nodes) {
        public ChapterPack {
            if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("A chapter needs a stable content ID.");
            Objects.requireNonNull(chapter, "chapter");
            if (startNodeId == null || startNodeId.isBlank()) throw new IllegalArgumentException("A chapter needs a start node.");
            nodes = List.copyOf(nodes);
            if (chapter.adaptive()) StoryGraphValidator.validateAdaptive(nodes, startNodeId, chapter.checkpoints());
            else StoryGraphValidator.validate(nodes, startNodeId);
        }
    }
}

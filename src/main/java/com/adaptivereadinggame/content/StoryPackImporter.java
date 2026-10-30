package com.adaptivereadinggame.content;

import com.adaptivereadinggame.repository.ChapterRepository;
import com.adaptivereadinggame.repository.StoryContentRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Validates the full pack before importing its immutable chapter content. */
public final class StoryPackImporter {
    private final ChapterRepository chapters;
    private final StoryContentRepository stories;
    private final StoryPackLoader loader;

    public StoryPackImporter(ChapterRepository chapters, StoryContentRepository stories) {
        this(chapters, stories, new StoryPackLoader());
    }

    StoryPackImporter(ChapterRepository chapters, StoryContentRepository stories, StoryPackLoader loader) {
        this.chapters = Objects.requireNonNull(chapters);
        this.stories = Objects.requireNonNull(stories);
        this.loader = Objects.requireNonNull(loader);
    }

    public int importFile(Path path) throws IOException {
        return importPack(loader.read(path));
    }

    public int importPack(StoryPack pack) {
        Objects.requireNonNull(pack);
        for (StoryPack.ChapterPack item : pack.chapters()) {
            chapters.saveChapter(item.chapter(), item.availableForNewGame());
            stories.saveStoryNodes(item.chapter().id(), item.nodes(), item.startNodeId(), item.chapter().checkpoints());
        }
        return pack.chapters().size();
    }
}

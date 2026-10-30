package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.Chapter;
import com.adaptivereadinggame.model.Difficulty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterRepository {
    Optional<Chapter> findChapter(UUID id);
    List<Chapter> findByDifficulty(Difficulty difficulty);
    Chapter saveChapter(Chapter chapter, boolean availableForNewGame);

    default Chapter saveChapter(Chapter chapter) {
        return saveChapter(chapter, true);
    }
}

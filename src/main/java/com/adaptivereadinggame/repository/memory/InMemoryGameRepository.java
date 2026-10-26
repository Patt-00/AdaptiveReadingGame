package com.adaptivereadinggame.repository.memory;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Replace through repository interfaces, never by adding SQL to a controller/service. */
public final class InMemoryGameRepository implements ChapterRepository, AssessmentRepository, SaveGameRepository,
        StoryContentRepository {
    private record AssessmentKey(UUID studentId, UUID chapterId, String checkpointId) {}
    private record SlotKey(UUID studentId, int slot) {}
    private final Map<UUID, Chapter> chapters = new ConcurrentHashMap<>();
    private final Map<AssessmentKey, AssessmentResult> results = new ConcurrentHashMap<>();
    private final Map<SlotKey, SaveGame> saves = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, StoryNode>> storyNodes = new ConcurrentHashMap<>();
    private final Map<UUID, String> firstNodes = new ConcurrentHashMap<>();
    @Override public Optional<Chapter> findChapter(UUID id) { return Optional.ofNullable(chapters.get(Objects.requireNonNull(id))); }
    @Override public List<Chapter> findByDifficulty(Difficulty difficulty) {
        return chapters.values().stream().filter(c -> c.difficulty() == difficulty
                        || c.adaptive() && c.checkpoints().stream()
                                .allMatch(checkpoint -> checkpoint.questionsByDifficulty().containsKey(difficulty)))
                .sorted(Comparator.comparing(Chapter::title).thenComparing(Chapter::id)).toList();
    }
    @Override public Chapter saveChapter(Chapter chapter) {
        Objects.requireNonNull(chapter);
        Chapter existing = chapters.putIfAbsent(chapter.id(), chapter);
        if (existing != null && !existing.equals(chapter))
            throw new IllegalStateException("Prepared chapter content is immutable after it is stored.");
        return existing == null ? chapter : existing;
    }
    @Override public synchronized void saveStoryNodes(UUID chapterId, List<StoryNode> nodes, String firstNodeId) {
        Chapter chapter = chapters.get(Objects.requireNonNull(chapterId));
        if (chapter == null) throw new IllegalArgumentException("Unknown chapter.");
        saveStoryNodes(chapterId, nodes, firstNodeId, chapter.checkpoints());
    }
    @Override public synchronized void saveStoryNodes(UUID chapterId, List<StoryNode> nodes, String firstNodeId,
                                                       List<StoryCheckpoint> checkpoints) {
        Objects.requireNonNull(chapterId);
        Objects.requireNonNull(nodes);
        if (!chapters.containsKey(chapterId)) throw new IllegalArgumentException("Unknown chapter.");
        Chapter chapter = chapters.get(chapterId);
        if (!chapter.checkpoints().equals(List.copyOf(checkpoints)))
            throw new IllegalArgumentException("Story checkpoint definitions must match the prepared chapter.");
        if (chapter.adaptive()) StoryGraphValidator.validateAdaptive(nodes, firstNodeId, chapter.checkpoints());
        else StoryGraphValidator.validate(nodes, firstNodeId);
        Map<String, StoryNode> byId = new LinkedHashMap<>();
        for (StoryNode node : nodes) {
            byId.put(node.storyNodeId(), node);
        }
        Map<String, StoryNode> existing = storyNodes.get(chapterId);
        if (existing != null) {
            if (existing.equals(byId) && Objects.equals(firstNodes.get(chapterId), firstNodeId)) return;
            throw new IllegalStateException("Prepared story content is immutable after it is stored.");
        }
        storyNodes.put(chapterId, Map.copyOf(byId));
        firstNodes.put(chapterId, firstNodeId);
    }
    @Override public Optional<String> firstNodeId(UUID chapterId) {
        return Optional.ofNullable(firstNodes.get(Objects.requireNonNull(chapterId)));
    }
    @Override public Optional<StoryNode> findNode(UUID chapterId, String storyNodeId) {
        Objects.requireNonNull(chapterId);
        Objects.requireNonNull(storyNodeId);
        return Optional.ofNullable(storyNodes.getOrDefault(chapterId, Map.of()).get(storyNodeId));
    }
    @Override public Optional<AssessmentResult> findCompleted(UUID studentId, UUID chapterId) {
        return findCompleted(studentId, chapterId, "");
    }
    @Override public Optional<AssessmentResult> findCompleted(UUID studentId, UUID chapterId, String checkpointId) {
        return Optional.ofNullable(results.get(new AssessmentKey(studentId, chapterId,
                Objects.requireNonNullElse(checkpointId, ""))));
    }
    @Override public List<AssessmentResult> resultsForChapter(UUID studentId, UUID chapterId) {
        return results.entrySet().stream().filter(entry -> entry.getKey().studentId().equals(studentId)
                        && entry.getKey().chapterId().equals(chapterId))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing((AssessmentResult r) -> r.attempt().completedAt())
                        .thenComparing(r -> r.attempt().id())).toList();
    }
    @Override public List<AssessmentResult> historyFor(UUID studentId) {
        return results.values().stream().filter(r -> r.attempt().studentId().equals(studentId))
                .sorted(Comparator.comparing((AssessmentResult r) -> r.attempt().completedAt()).thenComparing(r -> r.attempt().id())).toList();
    }
    @Override public AssessmentResult saveCompleted(AssessmentResult result) {
        var attempt = result.attempt();
        var existing = results.putIfAbsent(new AssessmentKey(attempt.studentId(), attempt.materialId(),
                attempt.checkpointId()), result);
        return existing == null ? result : existing;
    }
    @Override public Optional<SaveGame> findSlot(UUID studentId, int slot) {
        return Optional.ofNullable(saves.get(new SlotKey(studentId, slot)));
    }
    @Override public List<SaveGame> slotsFor(UUID studentId) {
        return saves.values().stream().filter(s -> s.studentId().equals(studentId)).sorted(Comparator.comparingInt(SaveGame::slot)).toList();
    }
    @Override public SaveGame saveSlot(SaveGame save) { saves.put(new SlotKey(save.studentId(), save.slot()), save); return save; }
}

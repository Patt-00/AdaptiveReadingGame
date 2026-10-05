package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.*;
import java.time.Clock;
import java.util.*;

/** Account-scoped queries and explicit save operation; authentication facade is still TODO. */
public final class ProgressService {
    private final StudentRepository students;
    private final ChapterRepository chapters;
    private final AssessmentRepository attempts;
    private final SaveGameRepository saves;
    private final Clock clock;
    public ProgressService(StudentRepository students, ChapterRepository chapters, AssessmentRepository attempts,
                           SaveGameRepository saves, Clock clock) {
        this.students = Objects.requireNonNull(students); this.chapters = Objects.requireNonNull(chapters);
        this.attempts = Objects.requireNonNull(attempts); this.saves = Objects.requireNonNull(saves); this.clock = Objects.requireNonNull(clock);
    }
    public List<AssessmentResult> history(UUID studentId) { requireStudent(studentId); return attempts.historyFor(studentId); }
    public List<SaveGame> slots(UUID studentId) { requireStudent(studentId); return saves.slotsFor(studentId); }
    public Optional<SaveGame> load(UUID studentId, int slot) {
        requireStudent(studentId); requireSlot(slot); return saves.findSlot(studentId, slot);
    }
    /** Explicit overwrite. The future UI must ask for confirmation before calling on an occupied slot. */
    public SaveGame save(UUID studentId, int slot, UUID chapterId, String storyNodeId) {
        requireStudent(studentId); requireSlot(slot);
        Chapter chapter = chapters.findChapter(chapterId).orElseThrow(() -> new IllegalArgumentException("Unknown chapter"));
        return saves.saveSlot(new SaveGame(studentId, slot, chapterId, storyNodeId, chapter.difficulty(), clock.instant()));
    }
    private void requireStudent(UUID id) { students.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown student")); }
    private void requireSlot(int slot) { if (slot < 1 || slot > 3) throw new IllegalArgumentException("Slot must be 1..3"); }
}

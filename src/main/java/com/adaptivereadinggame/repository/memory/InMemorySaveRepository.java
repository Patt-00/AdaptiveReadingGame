package com.adaptivereadinggame.repository.memory;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.*;
import java.util.*;

/** Development storage only. Basic and full saves share a single synchronized write boundary. */
public final class InMemorySaveRepository implements SaveGameRepository, SaveSnapshotRepository {
    private record SlotKey(UUID studentId, int slot) {}
    private final Map<SlotKey, SaveGame> checkpoints = new HashMap<>();
    private final Map<SlotKey, SaveSnapshot> snapshots = new HashMap<>();
    @Override public synchronized Optional<SaveGame> findSlot(UUID studentId, int slot) {
        return Optional.ofNullable(checkpoints.get(key(studentId, slot)));
    }
    @Override public synchronized List<SaveGame> slotsFor(UUID studentId) {
        Objects.requireNonNull(studentId);
        return checkpoints.values().stream().filter(s -> s.studentId().equals(studentId))
                .sorted(Comparator.comparingInt(SaveGame::slot)).toList();
    }
    @Override public synchronized SaveGame saveSlot(SaveGame save) {
        Objects.requireNonNull(save);
        SlotKey key = key(save.studentId(), save.slot());
        // A new basic-only save cannot silently leave an older full snapshot available.
        checkpoints.put(key, save); snapshots.remove(key); return save;
    }
    @Override public synchronized SaveSnapshot saveSnapshot(SaveSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        SaveGame checkpoint = snapshot.checkpoint();
        SlotKey key = key(checkpoint.studentId(), checkpoint.slot());
        checkpoints.put(key, checkpoint); snapshots.put(key, snapshot); return snapshot;
    }
    @Override public synchronized Optional<SaveSnapshot> findSnapshot(UUID studentId, int slot) {
        return Optional.ofNullable(snapshots.get(key(studentId, slot)));
    }
    @Override public synchronized List<SaveSnapshot> snapshotsFor(UUID studentId) {
        Objects.requireNonNull(studentId);
        return snapshots.values().stream().filter(s -> s.checkpoint().studentId().equals(studentId))
                .sorted(Comparator.comparingInt(s -> s.checkpoint().slot())).toList();
    }
    private static SlotKey key(UUID studentId, int slot) {
        Objects.requireNonNull(studentId);
        if (slot < 1 || slot > 3) throw new IllegalArgumentException("Slot must be 1..3");
        return new SlotKey(studentId, slot);
    }
}

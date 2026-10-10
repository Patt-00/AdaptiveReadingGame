package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.SaveSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Dev 3 supplies full saves using the same slot records as SaveGameRepository. */
public interface SaveSnapshotRepository {
    /**
     * Store slot details and full state together, after validating referenced IDs.
     * Failure retains the previous valid save and throws; it is not a save success.
     * This operation must not change stored first answers or completed results.
     */
    SaveSnapshot saveSnapshot(SaveSnapshot snapshot);
    /** Find this learner's full snapshot in slot 1, 2, or 3; empty means no full save. */
    Optional<SaveSnapshot> findSnapshot(UUID studentId, int slot);
    /** Occupied full saves in slot-number order. Continue selection belongs to Dev 1. */
    List<SaveSnapshot> snapshotsFor(UUID studentId);
}

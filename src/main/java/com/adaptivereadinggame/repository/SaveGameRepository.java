package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.SaveGame;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaveGameRepository {
    Optional<SaveGame> findSlot(UUID studentId, int slot);
    List<SaveGame> slotsFor(UUID studentId);
    SaveGame saveSlot(SaveGame save);
}

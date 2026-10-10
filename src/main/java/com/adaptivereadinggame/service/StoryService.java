package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.GameState;
import java.util.Optional;
import java.util.UUID;

/** Shared narrative actions. Dev 2 supplies the implementation, not Dev 1. */
public interface StoryService {
    GameState startChapter(UUID studentId, UUID chapterId);
    /** Advance only an automatic next node; do not skip a choice or reading check. */
    GameState advanceStory(UUID studentId);
    /** Follow a valid narrative choice without changing comprehension scores. */
    GameState chooseStory(UUID studentId, String choiceId);
    Optional<GameState> currentState(UUID studentId);

    /**
     * Validate the entire candidate before replacing any active state. Verify chapter,
     * node, learner, question index, and question/option membership. For an assessment,
     * read its authoritative stored draft and retain its original first answers even
     * if the saved snapshot is older. Reject mismatched/missing sessions and do not
     * revive a completed assessment for scoring. Restore the validated story and its
     * assessment session together before publishing either as the active state.
     * Failure leaves both previous states unchanged. Return the restored authoritative
     * state, which may differ from the older save. An implementation that cannot safely
     * restore assessments yet must throw UnsupportedOperationException before mutation.
     */
    GameState restoreState(GameState gameState);

    /** Forget only this learner's active in-memory state on logout; keep stored data. */
    void clearStateFor(UUID studentId);
}

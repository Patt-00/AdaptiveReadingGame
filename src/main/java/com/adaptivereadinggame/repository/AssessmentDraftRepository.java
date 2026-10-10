package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.GameState;
import java.util.Optional;
import java.util.UUID;

/** First-answer storage. Dev 3 implements persistence; Dev 2 applies scoring rules. */
public interface AssessmentDraftRepository {
    /**
     * Create only a new, valid assessment session with zero index and no first answers.
     * Verify learner/chapter/node existence. Reject duplicate creation rather than
     * deleting or resetting a previous draft, including one restored from an old save.
     */
    void createDraft(GameState gameState);

    /**
     * Atomically insert this session/question's first submitted index and return it.
     * A repeated or concurrent submission returns the original stored index. Validate
     * the session's learner ownership, question's chapter membership, and option bounds
     * before accepting a submission. Never accept a write for a completed assessment.
     * A write failure must throw rather than returning invented feedback.
     */
    int recordFirstAnswer(UUID sessionId, UUID studentId, UUID questionId, int selectedIndex);

    /** Read authoritative locked answers. The caller checks learner ownership before use. */
    Optional<GameState> findDraft(UUID sessionId);
}

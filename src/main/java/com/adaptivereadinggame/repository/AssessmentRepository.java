package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.AssessmentResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository {
    /** Legacy whole-chapter assessment lookup. */
    Optional<AssessmentResult> findCompleted(UUID studentId, UUID chapterId);
    /** Checkpoint-specific lookup; older adapters treat only the empty ID as a legacy result. */
    default Optional<AssessmentResult> findCompleted(UUID studentId, UUID chapterId, String checkpointId) {
        return checkpointId == null || checkpointId.isEmpty()
                ? findCompleted(studentId, chapterId) : Optional.empty();
    }
    default List<AssessmentResult> resultsForChapter(UUID studentId, UUID chapterId) {
        return findCompleted(studentId, chapterId).stream().toList();
    }
    List<AssessmentResult> historyFor(UUID studentId);
    /** Atomically insert once per learner/chapter/checkpoint; if present return the original result. */
    AssessmentResult saveCompleted(AssessmentResult result);
}

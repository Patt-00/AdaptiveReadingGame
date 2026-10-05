package com.adaptivereadinggame.repository;

import com.adaptivereadinggame.model.AssessmentResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository {
    Optional<AssessmentResult> findCompleted(UUID studentId, UUID chapterId);
    List<AssessmentResult> historyFor(UUID studentId);
    /** Atomically insert once per student/chapter; if already present return the original result. */
    AssessmentResult saveCompleted(AssessmentResult result);
}

package com.adaptivereadinggame.backend;

import com.adaptivereadinggame.repository.*;
import com.adaptivereadinggame.repository.memory.*;
import com.adaptivereadinggame.service.*;
import java.time.Clock;

/** Composition root: swap repository adapters here, not inside gameplay services. */
public record BackendContext(StudentRepository students, ChapterRepository chapters,
                             AssessmentRepository assessments, SaveGameRepository saves,
                             AssessmentService assessmentService, ProgressService progressService) {
    public static BackendContext inMemory() {
        var students = new InMemoryStudentRepository();
        var game = new InMemoryGameRepository();
        var clock = Clock.systemUTC();
        return new BackendContext(students, game, game, game,
                new AssessmentService(students, game, game, new BaselineDifficultyPolicy(), clock),
                new ProgressService(students, game, game, game, clock));
    }
}

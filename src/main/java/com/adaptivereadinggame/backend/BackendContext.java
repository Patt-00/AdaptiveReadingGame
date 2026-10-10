package com.adaptivereadinggame.backend;

import com.adaptivereadinggame.repository.*;
import com.adaptivereadinggame.repository.memory.*;
import com.adaptivereadinggame.service.*;
import java.time.Clock;
import java.util.function.Function;

/** Composition root: swap repository adapters here, not inside gameplay services. */
public record BackendContext(StudentRepository students, ChapterRepository chapters,
                             AssessmentRepository assessments, SaveGameRepository saves,
                             AssessmentService assessmentService, ProgressService progressService,
                             AuthService authService, AccountGameService accountGameService) {
    public static BackendContext inMemory() {
        var accounts = new InMemoryAccountRepository();
        var game = new InMemoryGameRepository();
        var saves = new InMemorySaveRepository();
        var pending = new UnavailableGameplay();
        return create(accounts, accounts, accounts, accounts, game, game, saves, saves,
                pending, pending, ignored -> pending, new Pbkdf2PasswordHasher(), new BaselineDifficultyPolicy(), Clock.systemUTC());
    }

    /** Dev 3 injects MySQL adapters here; Dev 2 injects StoryService. No controller changes are needed. */
    public static BackendContext create(StudentRepository students, AccountRepository accounts,
            CredentialRepository credentials, RegistrationRepository registration,
            ChapterRepository chapters, AssessmentRepository results, SaveGameRepository saves,
            SaveSnapshotRepository snapshots, StoryRepository nodes, AssessmentDraftRepository drafts,
            Function<AssessmentService, StoryService> storyFactory, PasswordHasher hasher,
            DifficultyPolicy difficulty, Clock clock) {
        var assessmentService = new AssessmentService(students, chapters, results, difficulty, clock);
        var story = java.util.Objects.requireNonNull(storyFactory.apply(assessmentService));
        var progressService = new ProgressService(students, chapters, results, saves, clock);
        var auth = new DefaultAuthService(accounts, credentials, registration, hasher);
        var accountGame = new AccountGameService(auth, progressService, snapshots, story, nodes, drafts,
                chapters, results, assessmentService, clock);
        return new BackendContext(students, chapters, results, saves, assessmentService, progressService,
                auth, accountGame);
    }
}

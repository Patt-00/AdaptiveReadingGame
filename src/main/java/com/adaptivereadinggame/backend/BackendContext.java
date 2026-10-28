package com.adaptivereadinggame.backend;

import com.adaptivereadinggame.content.StoryPackImporter;
import com.adaptivereadinggame.repository.*;
import com.adaptivereadinggame.repository.mysql.ConnectionProvider;
import com.adaptivereadinggame.repository.mysql.DriverManagerConnectionProvider;
import com.adaptivereadinggame.repository.mysql.MySqlAccountRepository;
import com.adaptivereadinggame.repository.mysql.MySqlConfig;
import com.adaptivereadinggame.repository.mysql.MySqlGameRepository;
import com.adaptivereadinggame.repository.mysql.MySqlPlayerAppearanceRepository;
import com.adaptivereadinggame.repository.memory.*;
import com.adaptivereadinggame.service.*;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;

/** Composition root: swap repository adapters here, not inside gameplay services. */
public record BackendContext(StudentRepository students, ChapterRepository chapters,
                             AssessmentRepository assessments, SaveGameRepository saves,
                             AssessmentService assessmentService, ProgressService progressService,
                             AuthService authService, AccountGameService accountGameService,
                             PlayerAppearanceService playerAppearanceService,
                             Optional<StoryPackImporter> storyPackImporter) {
    public static BackendContext inMemory() {
        return inMemory(false);
    }

    /** In-memory app mode with the optional local model and Java fallback. */
    public static BackendContext inMemoryWithModel() {
        return inMemory(true);
    }

    private static BackendContext inMemory(boolean modelAssisted) {
        var accounts = new InMemoryAccountRepository();
        var game = new InMemoryGameRepository();
        var saves = new InMemorySaveRepository();
        var drafts = new InMemoryAssessmentDraftRepository(accounts, game, game, game);
        DifficultyPolicy policy = modelAssisted ? ModelAssistedDifficultyPolicy.local(game)
                : new BaselineDifficultyPolicy();
        return create(accounts, accounts, accounts, accounts, game, game, saves, saves,
                game, drafts, assessment -> new RepositoryStoryService(accounts, game, game, drafts, assessment),
                new Pbkdf2PasswordHasher(),
                policy, Clock.systemUTC());
    }

    /** Persistent desktop configuration; run the checked-in schema migration before use. */
    public static BackendContext mysql(MySqlConfig config) {
        return mysql(new DriverManagerConnectionProvider(config));
    }

    /** Test seam for a disposable MySQL database; production code uses the config overload. */
    public static BackendContext mysql(ConnectionProvider connections) {
        var accounts = new MySqlAccountRepository(connections);
        var game = new MySqlGameRepository(connections);
        return createWithAppearance(accounts, accounts, accounts, accounts, game, game, game, game, game, game,
                assessment -> new RepositoryStoryService(accounts, game, game, game, assessment),
                new Pbkdf2PasswordHasher(),
                ModelAssistedDifficultyPolicy.local(game), Clock.systemUTC(), new MySqlPlayerAppearanceRepository(connections));
    }

    /** Compose storage adapters and a story-service factory without adding repository access to controllers. */
    public static BackendContext create(StudentRepository students, AccountRepository accounts,
            CredentialRepository credentials, RegistrationRepository registration,
            ChapterRepository chapters, AssessmentRepository results, SaveGameRepository saves,
            SaveSnapshotRepository snapshots, StoryRepository nodes, AssessmentDraftRepository drafts,
            Function<AssessmentService, StoryService> storyFactory, PasswordHasher hasher,
            DifficultyPolicy difficulty, Clock clock) {
        return createWithAppearance(students, accounts, credentials, registration, chapters, results, saves,
                snapshots, nodes, drafts, storyFactory, hasher, difficulty, clock, new InMemoryPlayerAppearanceRepository());
    }

    private static BackendContext createWithAppearance(StudentRepository students, AccountRepository accounts,
            CredentialRepository credentials, RegistrationRepository registration,
            ChapterRepository chapters, AssessmentRepository results, SaveGameRepository saves,
            SaveSnapshotRepository snapshots, StoryRepository nodes, AssessmentDraftRepository drafts,
            Function<AssessmentService, StoryService> storyFactory, PasswordHasher hasher,
            DifficultyPolicy difficulty, Clock clock, PlayerAppearanceRepository appearances) {
        var assessmentService = new AssessmentService(students, chapters, results, difficulty, clock, drafts);
        var story = java.util.Objects.requireNonNull(storyFactory.apply(assessmentService));
        var progressService = new ProgressService(students, chapters, results, saves, clock);
        var auth = new DefaultAuthService(accounts, credentials, registration, hasher);
        var accountGame = new AccountGameService(auth, progressService, snapshots, story, nodes, drafts,
                chapters, results, assessmentService, clock);
        var appearanceService = new PlayerAppearanceService(auth, appearances);
        Optional<StoryPackImporter> contentImporter = nodes instanceof StoryContentRepository writer
                ? Optional.of(new StoryPackImporter(chapters, writer)) : Optional.empty();
        return new BackendContext(students, chapters, results, saves, assessmentService, progressService,
                auth, accountGame, appearanceService, contentImporter);
    }
}

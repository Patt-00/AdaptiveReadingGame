package com.adaptivereadinggame.repository.mysql;

import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.content.StoryPack;
import com.adaptivereadinggame.content.StoryPackImporter;
import com.adaptivereadinggame.content.StoryPackLoader;
import com.adaptivereadinggame.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit opt-in integration test. It refuses non-loopback hosts and any schema
 * other than adaptive_reading_test; run only against a disposable MySQL instance.
 */
@EnabledIfSystemProperty(named = "arg.mysqlTests", matches = "true")
final class MySqlGameRepositoryIntegrationTest {
    private static MySqlConfig config;
    private static ConnectionProvider connections;

    @BeforeAll static void prepareTestDatabase() {
        config = testConfigFromEnvironment();
        connections = new DriverManagerConnectionProvider(config);
        new MySqlSchemaMigrator(connections).migrate();
        new MySqlSchemaMigrator(connections).migrate(); // repeated migration must be safe
    }

    @Test void playerAppearancePersistsAcrossContextsAndRemainsAccountScoped() {
        String first = "avatar-a-" + shortId();
        String second = "avatar-b-" + shortId();
        char[] password = "test-only-passphrase".toCharArray();
        var initial = BackendContext.mysql(connections);
        initial.accountGameService().signup(first, "First Reader", password.clone());
        initial.accountGameService().signup(second, "Second Reader", password.clone());
        initial.accountGameService().login(first, password.clone());
        assertTrue(initial.playerAppearanceService().current().isEmpty());
        initial.playerAppearanceService().save(PlayerAppearance.B);
        initial.accountGameService().logout(true);
        var reopened = BackendContext.mysql(connections);
        reopened.accountGameService().login(first, password.clone());
        assertEquals(PlayerAppearance.B, reopened.playerAppearanceService().current().orElseThrow());
        reopened.accountGameService().logout(true);
        reopened.accountGameService().login(second, password.clone());
        assertTrue(reopened.playerAppearanceService().current().isEmpty());
        reopened.playerAppearanceService().save(PlayerAppearance.A);
        reopened.accountGameService().logout(true);
        reopened.accountGameService().login(first, password.clone());
        assertEquals(PlayerAppearance.B, reopened.playerAppearanceService().current().orElseThrow());
        reopened.playerAppearanceService().save(PlayerAppearance.A);
        assertEquals(PlayerAppearance.A, reopened.playerAppearanceService().current().orElseThrow());
        reopened.accountGameService().logout(true);
    }

    @Test void mysql_contract_round_trip_and_atomicity() throws Exception {
        var accounts = new MySqlAccountRepository(connections);
        var game = new MySqlGameRepository(connections);

        UUID failedRegistrationId = UUID.randomUUID();
        Account failedAccount = new Account(failedRegistrationId, "rollback-" + shortId(), "Rollback Reader");
        Student failedStudent = student(failedRegistrationId, "Rollback Reader");
        createStudentFailureTrigger();
        try {
            assertThrows(RepositoryException.class, () -> accounts.register(failedAccount,
                    "test-verifier-only", failedStudent));
        } finally {
            dropTrigger("arg_test_reject_student");
        }
        assertTrue(accounts.findAccount(failedRegistrationId).isEmpty(), "account insert rolled back");
        assertTrue(accounts.findEncodedVerifier(failedRegistrationId).isEmpty(), "credential insert rolled back");
        assertTrue(accounts.findById(failedRegistrationId).isEmpty(), "student insert rolled back");

        UUID learnerId = UUID.randomUUID();
        Account account = new Account(learnerId, "reader-" + shortId(), "Reader One");
        accounts.register(account, "pbkdf2:test-only-verifier", student(learnerId, account.displayName()));
        assertEquals(account, accounts.findByUsername(account.username()).orElseThrow());
        assertEquals("pbkdf2:test-only-verifier", accounts.findEncodedVerifier(learnerId).orElseThrow());
        assertEquals(student(learnerId, account.displayName()), accounts.findById(learnerId).orElseThrow());
        UUID duplicateId = UUID.randomUUID();
        Account duplicateUsername = new Account(duplicateId, account.username(), "Duplicate Reader");
        assertThrows(IllegalArgumentException.class, () -> accounts.register(duplicateUsername,
                "pbkdf2:duplicate-test-verifier", student(duplicateId, duplicateUsername.displayName())));
        assertTrue(accounts.findAccount(duplicateId).isEmpty());
        assertTrue(accounts.findEncodedVerifier(duplicateId).isEmpty());
        assertTrue(accounts.findById(duplicateId).isEmpty());

        UUID secondId = UUID.randomUUID();
        Account second = new Account(secondId, "second-" + shortId(), "Reader Two");
        accounts.register(second, "pbkdf2:second-test-only-verifier", student(secondId, second.displayName()));

        var accountFlow = BackendContext.mysql(connections).accountGameService();
        Account applicationAccount = accountFlow.signup("app-" + shortId(), "Application Reader",
                "test-only-passphrase".toCharArray());
        assertTrue(accountFlow.currentAccount().isEmpty(), "signup does not automatically sign the learner in");
        assertEquals(applicationAccount, accountFlow.login(applicationAccount.username(),
                "test-only-passphrase".toCharArray()), "signup and login run through the MySQL-backed app context");
        assertTrue(accountFlow.history().isEmpty());
        assertTrue(accountFlow.snapshots().isEmpty());
        accountFlow.logout(true);
        var reopenedAccountFlow = BackendContext.mysql(connections).accountGameService();
        assertEquals(applicationAccount, reopenedAccountFlow.login(applicationAccount.username(),
                "test-only-passphrase".toCharArray()), "the account remains available after repository recreation");
        reopenedAccountFlow.logout(true);

        UUID chapterId = UUID.randomUUID();
        UUID questionOneId = UUID.randomUUID();
        UUID questionTwoId = UUID.randomUUID();
        Chapter chapter = new Chapter(chapterId, "Database Fixture", "A short fixture passage.", Difficulty.EASY,
                List.of(new Question(questionOneId, "Question one?", List.of("Yes", "No"), 0,
                                "Fixture explanation one."),
                        new Question(questionTwoId, "Question two?", List.of("North", "South"), 1)));
        assertEquals(chapter, game.saveChapter(chapter));
        assertEquals(chapter, game.saveChapter(chapter), "same stable content is idempotent");
        assertEquals(List.of(chapter), game.findByDifficulty(Difficulty.EASY).stream()
                .filter(found -> found.id().equals(chapterId)).toList());

        StoryNode start = new StoryNode("start", "", "The reader enters the room.", false, List.of(), "choice", false);
        StoryNode choice = new StoryNode("choice", "Guide", "Which path?", false,
                List.of(new StoryChoice("left", "Go left", "check"),
                        new StoryChoice("right", "Go right", "check")), "", false, "guide");
        StoryNode readingCheck = new StoryNode("check", "", "Answer the questions.", false, List.of(), "", true);
        List<StoryNode> story = List.of(start, choice, readingCheck);
        game.saveStoryNodes(chapterId, story, "start");
        game.saveStoryNodes(chapterId, story, "start");
        assertEquals("start", game.firstNodeId(chapterId).orElseThrow());
        assertEquals(choice, game.findNode(chapterId, "choice").orElseThrow());
        assertEquals("Fixture explanation one.", game.findChapter(chapterId).orElseThrow().questions()
                .getFirst().explanation());

        accountFlow = BackendContext.mysql(connections).accountGameService();
        accountFlow.login(applicationAccount.username(), "test-only-passphrase".toCharArray());
        assertEquals("start", accountFlow.startChapter(chapterId, false).storyNodeId());
        assertEquals("choice", accountFlow.advanceStory().storyNodeId());
        assertEquals("check", accountFlow.chooseStory("left").storyNodeId());
        UUID applicationSession = accountFlow.startAssessment(chapterId);
        assertTrue(accountFlow.submit(applicationSession, questionOneId, 0));
        accountFlow.save(3, false);
        accountFlow.logout(false);

        var resumedAccountFlow = BackendContext.mysql(connections).accountGameService();
        resumedAccountFlow.login(applicationAccount.username(), "test-only-passphrase".toCharArray());
        GameState resumed = resumedAccountFlow.load(3, false).orElseThrow();
        assertEquals(applicationSession, resumed.assessmentSessionId());
        assertEquals(Map.of(questionOneId, 0), resumed.firstAnswers());
        assertTrue(resumedAccountFlow.submit(applicationSession, questionTwoId, 1));
        assertEquals(2, resumedAccountFlow.complete(applicationSession).attempt().correctAnswers());
        assertEquals(1, resumedAccountFlow.history().size());
        resumedAccountFlow.logout(true);

        UUID sessionId = UUID.randomUUID();
        GameState emptyDraft = new GameState(learnerId, chapterId, "check", sessionId, 0, Map.of());
        game.createDraft(emptyDraft);
        assertThrows(IllegalStateException.class,
                () -> game.recordFirstAnswer(sessionId, learnerId, questionTwoId, 1),
                "questions cannot be answered out of order");
        assertEquals(0, game.recordFirstAnswer(sessionId, learnerId, questionOneId, 0));
        assertEquals(0, game.recordFirstAnswer(sessionId, learnerId, questionOneId, 1),
                "a second submit must not replace the first answer");
        assertThrows(SecurityException.class,
                () -> game.recordFirstAnswer(sessionId, secondId, questionTwoId, 1));
        assertThrows(IllegalArgumentException.class,
                () -> game.recordFirstAnswer(sessionId, learnerId, questionTwoId, 2));

        GameState oneAnswer = game.findDraft(sessionId).orElseThrow();
        assertEquals(1, oneAnswer.questionIndex());
        assertEquals(Map.of(questionOneId, 0), oneAnswer.firstAnswers());
        Instant requestedTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        SaveSnapshot requested = new SaveSnapshot(new SaveGame(learnerId, 1, chapterId, "check", Difficulty.EASY,
                requestedTime), oneAnswer);
        SaveSnapshot saved = game.saveSnapshot(requested);
        assertEquals(requested, saved, "save time uses the contract's microsecond precision");

        createSaveFailureTrigger();
        try {
            SaveSnapshot replacement = new SaveSnapshot(new SaveGame(learnerId, 1, chapterId, "start", Difficulty.EASY,
                    Instant.now()), new GameState(learnerId, chapterId, "start", null, 0, Map.of()));
            assertThrows(RepositoryException.class, () -> game.saveSnapshot(replacement));
        } finally {
            dropTrigger("arg_test_reject_save_update");
        }
        assertEquals(saved, new MySqlGameRepository(connections).findSnapshot(learnerId, 1).orElseThrow(),
                "a failed full-save update must preserve the previous snapshot");

        assertTrue(new MySqlGameRepository(connections).findSnapshot(secondId, 1).isEmpty(),
                "save slots are isolated between learners");
        assertTrue(game.findSlot(learnerId, 3).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> game.findSlot(learnerId, 4));

        assertEquals(1, game.recordFirstAnswer(sessionId, learnerId, questionTwoId, 1));
        GameState completedDraft = game.findDraft(sessionId).orElseThrow();
        assertEquals(2, completedDraft.questionIndex());
        SaveSnapshot updated = game.saveSnapshot(new SaveSnapshot(new SaveGame(learnerId, 1, chapterId,
                "check", Difficulty.EASY, Instant.now()), completedDraft));

        AssessmentResult forged = new AssessmentResult(new Attempt(UUID.randomUUID(), learnerId, chapterId,
                0, 2, Instant.now()), Difficulty.EASY, Difficulty.EASY);
        assertThrows(IllegalArgumentException.class, () -> game.saveCompleted(forged),
                "the repository validates a result against stored first answers");
        AssessmentResult result = new AssessmentResult(new Attempt(UUID.randomUUID(), learnerId, chapterId,
                2, 2, Instant.now()), Difficulty.EASY, Difficulty.MEDIUM, DifficultyDecisionSource.JAVA_FALLBACK);
        AssessmentResult storedResult = game.saveCompleted(result);
        assertEquals(2, storedResult.attempt().correctAnswers());
        assertEquals(DifficultyDecisionSource.JAVA_FALLBACK, storedResult.decisionSource());
        AssessmentResult replay = new AssessmentResult(new Attempt(UUID.randomUUID(), learnerId, chapterId,
                0, 2, Instant.now()), Difficulty.EASY, Difficulty.EASY);
        assertEquals(storedResult, game.saveCompleted(replay), "a repeated completion returns the original result");
        assertEquals(List.of(storedResult), game.historyFor(learnerId));
        assertThrows(IllegalStateException.class,
                () -> game.recordFirstAnswer(sessionId, learnerId, questionTwoId, 0));
        assertEquals(updated, new MySqlGameRepository(connections).findSnapshot(learnerId, 1).orElseThrow(),
                "full saves survive repository recreation");

        SaveGame basic = new SaveGame(learnerId, 2, chapterId, "start", Difficulty.EASY, Instant.now());
        game.saveSlot(basic);
        assertEquals(2, game.slotsFor(learnerId).size());
        assertTrue(game.findSnapshot(learnerId, 2).isEmpty());
        List<StoryNode> changedStory = List.of(start,
                new StoryNode("choice", "Guide", "Changed path text.", false,
                        List.of(new StoryChoice("left", "Go left", "check"),
                                new StoryChoice("right", "Go right", "check")), "", false, "guide"),
                readingCheck);
        assertThrows(IllegalStateException.class, () -> game.saveStoryNodes(chapterId, changedStory, "start"),
                "published story nodes are immutable after assessment data exists");
    }

    @Test void mysql_content_pack_import_is_idempotent_and_keepsFeedbackAndAssetKeys() {
        String sourceId = "mysql-content-" + shortId();
        String document = """
                {
                  "formatVersion": 1,
                  "chapters": [{
                    "id": "%s",
                    "title": "MySQL content fixture",
                    "difficulty": "EASY",
                    "passage": "A short content-pack passage.",
                    "startNodeId": "opening",
                    "nodes": [
                      {"id":"opening","speakerName":"Guide","speakerAssetKey":"guide","dialogueText":"A test scene begins.","thought":false,"readingCheck":false,"nextNodeId":"choice","choices":[]},
                      {"id":"choice","speakerName":"Guide","dialogueText":"Pick a path.","thought":false,"readingCheck":false,"nextNodeId":"","choices":[{"id":"left","text":"Go left","nextNodeId":"check"},{"id":"right","text":"Go right","nextNodeId":"check"}]},
                      {"id":"check","speakerName":"","dialogueText":"Read the passage.","thought":false,"readingCheck":true,"nextNodeId":"","choices":[]}
                    ],
                    "questions": [
                      {"id":"question-one","prompt":"Question one?","choices":["Yes","No"],"correctChoiceIndex":0,"explanation":"Fixture explanation."},
                      {"id":"question-two","prompt":"Question two?","choices":["North","South"],"correctChoiceIndex":1}
                    ]
                  }]
                }
                """.formatted(sourceId);
        StoryPack pack = new StoryPackLoader().parse(document);
        StoryPack.ChapterPack chapter = pack.chapters().getFirst();
        MySqlGameRepository game = new MySqlGameRepository(connections);
        StoryPackImporter importer = new StoryPackImporter(game, game);
        assertEquals(1, importer.importPack(pack));
        assertEquals(chapter.chapter(), game.findChapter(chapter.chapter().id()).orElseThrow());
        assertEquals(chapter.startNodeId(), game.firstNodeId(chapter.chapter().id()).orElseThrow());
        StoryNode storedNode = game.findNode(chapter.chapter().id(), "opening").orElseThrow();
        assertEquals("guide", storedNode.speakerAssetKey());
        assertEquals("Fixture explanation.", game.findChapter(chapter.chapter().id()).orElseThrow()
                .questions().getFirst().explanation());
        assertEquals(1, importer.importPack(pack), "loading the same content pack twice must be safe");
    }

    @Test void mysql_adaptive_checkpoint_restoresRouteDifficultyAnswersAndOutcome() {
        UUID chapterId = UUID.randomUUID();
        Question easy = new Question(UUID.randomUUID(), "Easy checkpoint question?", List.of("Correct", "Wrong"),
                0, "Easy evidence.", "study-first", Difficulty.EASY);
        Question medium = new Question(UUID.randomUUID(), "Medium checkpoint question?", List.of("Wrong", "Correct"),
                1, "Medium evidence.", "study-first", Difficulty.MEDIUM);
        Question hard = new Question(UUID.randomUUID(), "Hard checkpoint question?", List.of("Wrong", "Correct"),
                1, "Hard evidence.", "study-first", Difficulty.HARD);
        Question nextEasy = new Question(UUID.randomUUID(), "Next easy question?", List.of("Correct", "Wrong"),
                0, "Next easy evidence.", "hall-second", Difficulty.EASY);
        Question nextMedium = new Question(UUID.randomUUID(), "Next medium question?", List.of("Wrong", "Correct"),
                1, "Next medium evidence.", "hall-second", Difficulty.MEDIUM);
        Question nextHard = new Question(UUID.randomUUID(), "Next hard question?", List.of("Wrong", "Correct"),
                1, "Next hard evidence.", "hall-second", Difficulty.HARD);
        var questions = java.util.Map.of(Difficulty.EASY, List.of(easy), Difficulty.MEDIUM, List.of(medium),
                Difficulty.HARD, List.of(hard));
        var nextQuestions = java.util.Map.of(Difficulty.EASY, List.of(nextEasy), Difficulty.MEDIUM, List.of(nextMedium),
                Difficulty.HARD, List.of(nextHard));
        StoryCheckpoint checkpoint = new StoryCheckpoint("study-first", "check", "Checkpoint evidence passage.",
                questions, "correct-outcome", "incorrect-outcome");
        StoryCheckpoint nextCheckpoint = new StoryCheckpoint("hall-second", "second-check", "Next checkpoint passage.",
                nextQuestions, "draft-end-correct", "draft-end-incorrect");
        Chapter chapter = Chapter.adaptive(chapterId, "Adaptive MySQL Fixture", "Episode passage.", Difficulty.EASY,
                List.of(easy, medium, hard, nextEasy, nextMedium, nextHard), List.of(checkpoint, nextCheckpoint));
        List<StoryNode> nodes = List.of(
                new StoryNode("start", "", "Episode starts.", false, List.of(), "route", false),
                new StoryNode("route", "", "Choose a test route.", false,
                        List.of(new StoryChoice("study-first", "Study first", "check", "study-first")), "", false),
                new StoryNode("check", "", "Answer the checkpoint.", false, List.of(), "", true,
                        "", "study-first", "", "", false),
                new StoryNode("correct-outcome", "", "Correct route.", false, List.of(), "second-check", false),
                new StoryNode("incorrect-outcome", "", "Incorrect route.", false, List.of(), "second-check", false),
                new StoryNode("second-check", "", "Answer the second checkpoint.", false, List.of(), "", true,
                        "", "hall-second", "", "", false),
                new StoryNode("draft-end-correct", "", "Draft boundary after a correct answer.", false, List.of(), "", false,
                        "", "", "", "", true),
                new StoryNode("draft-end-incorrect", "", "Draft boundary after an incorrect answer.", false, List.of(), "", false,
                        "", "", "", "", true));
        StoryPack.ChapterPack item = new StoryPack.ChapterPack("mysql-adaptive-" + shortId(), chapter,
                "start", nodes);
        StoryPack pack = new StoryPack(StoryPack.CURRENT_FORMAT_VERSION, List.of(item));
        MySqlGameRepository game = new MySqlGameRepository(connections);
        StoryPackImporter importer = new StoryPackImporter(game, game);
        importer.importPack(pack);
        assertEquals(chapter, game.findChapter(chapterId).orElseThrow());
        assertEquals(chapter.checkpoints(), game.findChapter(chapterId).orElseThrow().checkpoints());
        assertEquals(nodes.get(1), game.findNode(chapterId, "route").orElseThrow());

        var player = BackendContext.mysql(connections).accountGameService();
        Account account = player.signup("adaptive-" + shortId(), "Adaptive Reader", "test-only-passphrase".toCharArray());
        player.login(account.username(), "test-only-passphrase".toCharArray());
        player.startChapter(chapterId, Difficulty.EASY, false);
        player.advanceStory();
        player.chooseStory("study-first");
        UUID session = player.startAssessment(chapterId);
        assertEquals(easy.id(), player.questions(session).getFirst().id());
        assertTrue(player.submit(session, easy.id(), 0));
        SaveSnapshot saved = player.save(3, false);
        assertEquals("study-first", saved.gameState().routeId());
        assertEquals("study-first", saved.gameState().activeCheckpointId());
        assertEquals(Difficulty.EASY, saved.gameState().activeDifficulty());
        player.logout(false);

        var resumed = BackendContext.mysql(connections).accountGameService();
        resumed.login(account.username(), "test-only-passphrase".toCharArray());
        GameState restored = resumed.load(3, false).orElseThrow();
        assertEquals("study-first", restored.routeId());
        assertEquals("study-first", restored.activeCheckpointId());
        assertEquals(Map.of(easy.id(), 0), restored.firstAnswers());
        assertEquals(1, resumed.complete(session).attempt().correctAnswers());
        assertEquals("correct-outcome", resumed.continueAfterCheckpoint().storyNodeId());
        assertEquals("second-check", resumed.advanceStory().storyNodeId());
        UUID secondSession = resumed.startAssessment(chapterId);
        assertEquals(nextEasy.id(), resumed.questions(secondSession).getFirst().id(),
                "Java keeps the level during model cold start.");
        assertTrue(resumed.submit(secondSession, nextEasy.id(), 0));
        SaveSnapshot secondSave = resumed.save(3, true);
        assertEquals("hall-second", secondSave.gameState().activeCheckpointId());
        assertEquals("study-first", secondSave.gameState().lastCompletedCheckpointId());
        resumed.logout(false);

        var reopened = BackendContext.mysql(connections).accountGameService();
        reopened.login(account.username(), "test-only-passphrase".toCharArray());
        GameState secondRestored = reopened.load(3, false).orElseThrow();
        assertEquals("study-first", secondRestored.routeId());
        assertEquals("hall-second", secondRestored.activeCheckpointId());
        assertEquals("study-first", secondRestored.lastCompletedCheckpointId());
        assertEquals(Map.of(nextEasy.id(), 0), secondRestored.firstAnswers());
        assertEquals(1, reopened.complete(secondSession).attempt().correctAnswers());
        assertEquals("draft-end-correct", reopened.continueAfterCheckpoint().storyNodeId());
        assertEquals(2, reopened.episodeResults().size());
        assertEquals("hall-second", reopened.currentResult().orElseThrow().attempt().checkpointId());
        reopened.logout(true);
    }

    private static Student student(UUID id, String name) {
        return new Student(id, name, ReadingLevel.BEGINNER, 0, 0.0);
    }

    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8); }

    @Test void mysql_suppliedStoryRestoresFirstAnswersAndReachesBoundaryInBothOrders() throws Exception {
        StoryPack pack = new StoryPackLoader().read(java.nio.file.Path.of("assets/content/hailey-investigation-draft.json"));
        var item = pack.chapters().getFirst();
        var context = BackendContext.mysql(connections);
        context.storyPackImporter().orElseThrow().importPack(pack);
        context.storyPackImporter().orElseThrow().importPack(pack);
        for (String route : List.of("study-first", "hall-first")) {
            String username = "story-" + shortId();
            char[] passphrase = "database-story-test".toCharArray();
            var player = BackendContext.mysql(connections).accountGameService();
            player.signup(username, "Story Reader", passphrase);
            player.login(username, passphrase);
            player.startChapter(item.chapter().id(), Difficulty.EASY, false);
            String next = route.equals("study-first") ? "hall-second" : "study-second";
            for (String checkpoint : List.of(route, next)) {
                reachSuppliedCheckpoint(player, checkpoint, route);
                UUID session = player.startAssessment(item.chapter().id());
                Difficulty level = player.currentGame().orElseThrow().activeDifficulty();
                Question key = item.chapter().questions(checkpoint, level).getFirst();
                var prompt = player.questions(session).getFirst();
                int selected = route.equals("study-first") ? key.correctChoiceIndex()
                        : (key.correctChoiceIndex() + 1) % key.choices().size();
                boolean correct = route.equals("study-first");
                assertEquals(correct, player.submit(session, prompt.id(), selected));
                player.save(1, !checkpoint.equals(route));
                player.logout(false);
                player = BackendContext.mysql(connections).accountGameService();
                player.login(username, passphrase);
                GameState restored = player.continueGame(false).orElseThrow();
                assertEquals(route, restored.routeId());
                assertEquals(checkpoint, restored.activeCheckpointId());
                assertEquals(level, restored.activeDifficulty());
                assertEquals(selected, restored.firstAnswers().get(prompt.id()));
                assertEquals(correct, player.submit(session, prompt.id(), (selected + 1) % key.choices().size()));
                assertTrue(player.feedback(session, prompt.id()).explanation().contains("Provisional answer key"));
                assertEquals(correct ? 1 : 0, player.complete(session).attempt().correctAnswers());
                assertEquals(checkpoint + (correct ? "-correct" : "-incorrect"), player.continueAfterCheckpoint().storyNodeId());
            }
            for (int guard = 0; !player.currentGameView().orElseThrow().storyNode().storyComplete(); guard++) {
                assertTrue(guard < 1000);
                player.advanceStory();
            }
            assertEquals(2, player.episodeResults().size());
            assertTrue(player.currentGameView().orElseThrow().storyNode().dialogueText().contains("CURRENT STORY DRAFT ENDS HERE"));
            player.save(3, false);
            player.logout(false);
            var reopened = BackendContext.mysql(connections).accountGameService();
            reopened.login(username, passphrase);
            reopened.continueGame(false).orElseThrow();
            assertTrue(reopened.currentGameView().orElseThrow().storyNode().storyComplete());
            assertTrue(reopened.currentResult().isPresent());
        }
    }

    @Test void completedPackKeepsDraftSavesAndRestoresCompletedCheckpointAcrossContexts() throws Exception {
        var pack = new StoryPackLoader().read(java.nio.file.Path.of("assets/content/hailey-investigation-complete.json"));
        var draft = pack.chapters().getFirst();
        var complete = pack.chapters().get(1);
        String username = "versions-" + shortId();
        char[] password = "versioned-story-test".toCharArray();
        var first = BackendContext.mysql(connections);
        first.storyPackImporter().orElseThrow().importPack(pack);
        var player = first.accountGameService();
        player.signup(username, "Versioned Reader", password.clone());
        player.login(username, password.clone());
        player.startChapter(draft.chapter().id(), Difficulty.EASY, false);
        player.save(1, false);
        player.logout(false);

        var reopened = BackendContext.mysql(connections);
        reopened.storyPackImporter().orElseThrow().importPack(pack);
        var resumed = reopened.accountGameService();
        resumed.login(username, password.clone());
        GameState oldSave = resumed.load(1, false).orElseThrow();
        assertEquals(draft.chapter().id(), oldSave.chapterId());
        assertEquals("draft-notice", oldSave.storyNodeId());

        resumed.startChapter(complete.chapter().id(), Difficulty.EASY, true);
        reachCompletedCheckpoint(resumed, "study-first-check");
        UUID session = resumed.startAssessment(complete.chapter().id());
        var prompt = resumed.questions(session).getFirst();
        resumed.submit(session, prompt.id(), 0);
        resumed.save(2, false);
        resumed.logout(false);

        var afterRestart = BackendContext.mysql(connections);
        afterRestart.storyPackImporter().orElseThrow().importPack(pack);
        var restored = afterRestart.accountGameService();
        restored.login(username, password.clone());
        GameState completeSave = restored.continueGame(false).orElseThrow();
        assertEquals(complete.chapter().id(), completeSave.chapterId());
        assertEquals("study-first-check", completeSave.storyNodeId());
        assertEquals("study-first", completeSave.activeCheckpointId());
        assertEquals(1, completeSave.firstAnswers().size());
        assertEquals(0, completeSave.firstAnswers().get(prompt.id()));
        assertEquals(draft.chapter().id(), restored.load(1, true).orElseThrow().chapterId());
        restored.logout(true);
    }

    private static void reachCompletedCheckpoint(com.adaptivereadinggame.service.AccountGameService player,
                                                   String target) {
        for (int step = 0; step < 1200; step++) {
            var view = player.currentGameView().orElseThrow();
            StoryNode node = view.storyNode();
            if (target.equals(node.storyNodeId())) return;
            if (node.readingCheck()) {
                UUID session = player.startAssessment(view.chapter().id());
                for (var question : player.questions(session)) player.submit(session, question.id(), 0);
                player.complete(session);
                player.continueAfterCheckpoint();
            } else if (node.storyChoices().isEmpty()) player.advanceStory();
            else {
                String id = node.storyNodeId().equals("investigation-route-choice")
                        ? "study-first" : node.storyChoices().getFirst().choiceId();
                player.chooseStory(node.storyChoices().stream().anyMatch(choice -> choice.choiceId().equals(id))
                        ? id : node.storyChoices().getFirst().choiceId());
            }
        }
        fail("Completed story did not reach " + target);
    }

    private static void reachSuppliedCheckpoint(com.adaptivereadinggame.service.AccountGameService player,
            String expected, String route) {
        for (int guard = 0; guard < 1000; guard++) {
            StoryNode node = player.currentGameView().orElseThrow().storyNode();
            if (node.readingCheck()) {
                assertEquals(expected, node.checkpointId());
                return;
            }
            if (node.storyChoices().isEmpty()) player.advanceStory();
            else {
                StoryChoice choice = node.storyChoices().stream().filter(option -> option.routeId().equals(route))
                        .findFirst().orElse(node.storyChoices().getFirst());
                player.chooseStory(choice.choiceId());
            }
        }
        fail("Supplied story did not reach checkpoint " + expected);
    }

    private static void createStudentFailureTrigger() throws Exception {
        execute("DROP TRIGGER IF EXISTS arg_test_reject_student");
        execute("CREATE TRIGGER arg_test_reject_student BEFORE INSERT ON students FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'intentional integration-test rollback'");
    }

    private static void createSaveFailureTrigger() throws Exception {
        execute("DROP TRIGGER IF EXISTS arg_test_reject_save_update");
        execute("CREATE TRIGGER arg_test_reject_save_update BEFORE UPDATE ON save_slots FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'intentional integration-test rollback'");
    }

    private static void dropTrigger(String trigger) {
        try { execute("DROP TRIGGER IF EXISTS " + trigger); }
        catch (Exception failure) { throw new AssertionError("Could not remove temporary test trigger.", failure); }
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static MySqlConfig testConfigFromEnvironment() {
        String url = System.getenv("ARG_MYSQL_TEST_URL");
        String username = System.getenv("ARG_MYSQL_TEST_USER");
        String password = System.getenv("ARG_MYSQL_TEST_PASSWORD");
        if (url == null || username == null || password == null)
            throw new IllegalStateException("Set the three ARG_MYSQL_TEST_* variables for the opt-in test.");
        URI address = URI.create("http" + url.substring("jdbc:mysql".length()));
        if (!List.of("localhost", "127.0.0.1", "::1").contains(address.getHost()))
            throw new IllegalArgumentException("MySQL integration tests may connect only to a loopback host.");
        if (!"/adaptive_reading_test".equals(address.getPath()))
            throw new IllegalArgumentException("MySQL integration tests require the adaptive_reading_test schema.");
        return new MySqlConfig(url, username, password);
    }
}

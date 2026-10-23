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

    private static Student student(UUID id, String name) {
        return new Student(id, name, ReadingLevel.BEGINNER, 0, 0.0);
    }

    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8); }

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

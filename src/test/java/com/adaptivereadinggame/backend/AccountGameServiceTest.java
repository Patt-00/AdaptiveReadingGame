package com.adaptivereadinggame.backend;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.*;
import com.adaptivereadinggame.repository.memory.*;
import com.adaptivereadinggame.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Test doubles stand in for teammates' pieces, not production game implementations. */
class AccountGameServiceTest {
    private BackendContext backend;
    private AccountGameService game;
    private InMemorySaveRepository saves;
    private InMemoryGameRepository data;
    private TestGameplay story;
    private Drafts drafts;
    private Chapter chapter;
    private Account first;
    private final char[] password = "test password".toCharArray();
    private final Instant now = Instant.parse("2026-10-11T01:02:03.123456789Z");

    @BeforeEach void setup() {
        var accounts = new InMemoryAccountRepository(); data = new InMemoryGameRepository();
        saves = new InMemorySaveRepository(); drafts = new Drafts(); story = new TestGameplay();
        PasswordHasher fastTestHasher = new PasswordHasher() {
            public String encode(char[] value) { return "test-only:" + Arrays.hashCode(value); }
            public boolean matches(char[] value, String verifier) { return encode(value).equals(verifier); }
        };
        backend = BackendContext.create(accounts, accounts, accounts, accounts, data, data, saves, saves,
                story, drafts, assessment -> story, fastTestHasher, new BaselineDifficultyPolicy(), Clock.fixed(now, ZoneOffset.UTC));
        game = backend.accountGameService();
        chapter = new Chapter(UUID.randomUUID(), "Chapter", "A small garden.", Difficulty.MEDIUM,
                List.of(new Question(UUID.randomUUID(), "What grew?", List.of("A seed", "A desk"), 0)));
        data.saveChapter(chapter);
        first = game.signup("first", "First", password); game.login("FIRST", password);
    }
    private void start() { game.startChapter(chapter.id(), false); }
    private GameState state() { return game.currentGame().orElseThrow(); }

    @Test void unauthenticatedQueriesAreDenied() {
        game.logout(false);
        assertThrows(SecurityException.class, game::history);
        assertThrows(SecurityException.class, game::snapshots);
        assertThrows(SecurityException.class, game::canContinue);
        assertThrows(SecurityException.class, () -> game.load(1, true));
        assertThrows(SecurityException.class, game::currentGame);
        assertThrows(SecurityException.class, () -> game.startAssessment(chapter.id()));
    }
    @Test void readingCheckCannotBypassStoryOrItsFirstAnswerStorage() {
        assertThrows(IllegalStateException.class, () -> game.startAssessment(chapter.id()));
        start(); UUID oldSession = game.startAssessment(chapter.id());
        game.startChapter(chapter.id(), true);
        assertThrows(IllegalStateException.class, () -> game.submit(oldSession, chapter.questions().getFirst().id(), 0));
        assertThrows(IllegalStateException.class, () -> game.complete(oldSession));
    }
    @Test void cleanupFailureStillSignsOutAndClearsPrivateAccountState() {
        start(); UUID session = game.startAssessment(chapter.id()); story.failCleanup = true;
        assertThrows(IllegalStateException.class, () -> game.logout(true));
        assertTrue(game.currentAccount().isEmpty());
        assertThrows(SecurityException.class, game::currentGame);
        assertThrows(IllegalArgumentException.class, () -> backend.assessmentService().questions(session));
    }
    @Test void fullSaveHasConsistentCheckpointAndClearsDirtyOnlyOnSuccess() {
        start(); assertTrue(game.hasUnsavedProgress());
        var snapshot = game.save(2, false);
        assertEquals(state(), snapshot.gameState()); assertEquals(first.id(), snapshot.checkpoint().studentId());
        assertEquals(now.truncatedTo(java.time.temporal.ChronoUnit.MICROS), snapshot.checkpoint().savedAt());
        assertEquals(snapshot.checkpoint(), backend.progressService().load(first.id(), 2).orElseThrow());
        assertFalse(game.hasUnsavedProgress()); assertTrue(game.canContinue());
    }
    @Test void occupiedSlotNeedsConfirmationAndCancelKeepsOldSave() {
        start(); var before = game.save(1, false);
        game.advanceStory(); var current = state();
        assertThrows(ConfirmationRequiredException.class, () -> game.save(1, false));
        assertEquals(before, saves.findSnapshot(first.id(), 1).orElseThrow());
        assertEquals(current, state()); assertTrue(game.hasUnsavedProgress());
        assertEquals("ending", game.save(1, true).gameState().storyNodeId());
    }
    @Test void saveFailureKeepsPreviousValidSaveAndUnsavedProgress() {
        start(); var previous = game.save(1, false); game.advanceStory(); var current = state();
        var failing = new SaveSnapshotRepository() {
            public SaveSnapshot saveSnapshot(SaveSnapshot s) { throw new IllegalStateException("Storage unavailable"); }
            public Optional<SaveSnapshot> findSnapshot(UUID id, int slot) { return saves.findSnapshot(id, slot); }
            public List<SaveSnapshot> snapshotsFor(UUID id) { return saves.snapshotsFor(id); }
        };
        var failingGame = facadeWith(failing);
        failingGame.startChapter(chapter.id(), false); failingGame.advanceStory();
        assertThrows(IllegalStateException.class, () -> failingGame.save(1, true));
        assertTrue(failingGame.hasUnsavedProgress()); assertEquals(current, failingGame.currentGame().orElseThrow());
        assertEquals(previous, saves.findSnapshot(first.id(), 1).orElseThrow());
    }
    private AccountGameService facadeWith(SaveSnapshotRepository snapshots) {
        return new AccountGameService(backend.authService(), backend.progressService(), snapshots, story, story, drafts,
                data, data, backend.assessmentService(), Clock.fixed(now, ZoneOffset.UTC));
    }
    @Test void emptyLoadDoesNotDiscardCurrentProgress() {
        start(); var before = state(); assertTrue(game.load(2, false).isEmpty());
        assertEquals(before, state()); assertTrue(game.hasUnsavedProgress());
    }
    @Test void loadAndContinueCancellationDoNotMutateCurrentState() {
        start(); game.save(1, false); game.advanceStory(); var before = state();
        assertThrows(ConfirmationRequiredException.class, () -> game.load(1, false));
        assertThrows(ConfirmationRequiredException.class, () -> game.continueGame(false));
        assertEquals(before, state()); assertTrue(game.hasUnsavedProgress());
        assertEquals("opening", game.load(1, true).orElseThrow().storyNodeId()); assertFalse(game.hasUnsavedProgress());
    }
    @Test void continueUsesNewestFullSaveAndLowestSlotOnTimeTie() {
        start(); var base = state();
        var ending = new GameState(first.id(), chapter.id(), "ending", null, 0, Map.of());
        snapshot(3, now.minusSeconds(1), base); snapshot(2, now.plusSeconds(1), ending); snapshot(1, now, base);
        assertEquals("ending", game.continueGame(true).orElseThrow().storyNodeId());
        snapshot(1, now.plusSeconds(1), base);
        assertEquals("opening", game.continueGame(true).orElseThrow().storyNodeId());
    }
    private SaveSnapshot snapshot(int slot, Instant at, GameState state) {
        return saves.saveSnapshot(new SaveSnapshot(new SaveGame(first.id(), slot, chapter.id(), state.storyNodeId(),
                chapter.difficulty(), at), state));
    }
    @Test void basicCheckpointAloneDoesNotEnableContinue() {
        backend.progressService().save(first.id(), 1, chapter.id(), "opening");
        assertFalse(game.canContinue()); assertTrue(game.continueGame(false).isEmpty());
    }
    @Test void basicOverwriteCannotLeaveAnOlderFullSnapshotAvailable() {
        start(); game.save(1, false); assertTrue(game.canContinue());
        backend.progressService().save(first.id(), 1, chapter.id(), "ending");
        assertFalse(game.canContinue()); assertTrue(saves.findSnapshot(first.id(), 1).isEmpty());
        assertEquals("ending", saves.findSlot(first.id(), 1).orElseThrow().storyNodeId());
    }
    @Test void crossAccountSavesAndHistoryStayHiddenAndLogoutClearsSessions() {
        start(); game.save(1, false); UUID session = game.startAssessment(chapter.id());
        game.submit(session, chapter.questions().getFirst().id(), 0); game.complete(session);
        game.logout(true);
        assertThrows(IllegalArgumentException.class, () -> backend.assessmentService().questions(session));
        assertTrue(story.currentState(first.id()).isEmpty()); assertTrue(game.currentAccount().isEmpty());
        game.signup("second", "Second", password); game.login("second", password);
        assertTrue(game.snapshots().isEmpty()); assertTrue(game.history().isEmpty());
        assertTrue(game.load(1, true).isEmpty()); assertFalse(game.canContinue());
        assertThrows(SecurityException.class, () -> game.questions(session));
    }
    @Test void logoutCancellationLeavesAccountStoryAndAssessmentAlive() {
        start(); var before = state(); var session = game.startAssessment(chapter.id());
        var current = state();
        assertThrows(ConfirmationRequiredException.class, () -> game.logout(false));
        assertEquals(first, game.currentAccount().orElseThrow()); assertEquals(current, state());
        assertEquals(1, game.questions(session).size()); assertEquals(before.storyNodeId(), current.storyNodeId());
    }
    @Test void chapterReplacementRequiresExplicitUnsavedConfirmation() {
        start(); var before = state();
        assertThrows(ConfirmationRequiredException.class, () -> game.startChapter(chapter.id(), false));
        assertEquals(before, state()); assertEquals(before, game.startChapter(chapter.id(), true));
    }
    @Test void unknownNodeAndForeignSavedAnswersFailWithoutReplacingState() {
        start(); game.save(1, false); var before = state();
        snapshot(2, now, new GameState(first.id(), chapter.id(), "unknown", null, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> game.load(2, true)); assertEquals(before, state());
        UUID session = UUID.randomUUID();
        snapshot(2, now, new GameState(first.id(), chapter.id(), "opening", session, 0, Map.of(UUID.randomUUID(), 0)));
        assertThrows(IllegalArgumentException.class, () -> game.load(2, true)); assertEquals(before, state());
    }
    @Test void restoreFailureKeepsOldGameAndDirtyFlag() {
        start(); game.save(1, false); game.advanceStory(); var before = state(); story.failRestore = true;
        assertThrows(UnsupportedOperationException.class, () -> game.load(1, true));
        assertEquals(before, state()); assertTrue(game.hasUnsavedProgress());
    }
    @Test void oldSnapshotCannotEraseAuthoritativeFirstAnswer() {
        start(); UUID session = UUID.randomUUID(); UUID question = chapter.questions().getFirst().id();
        var old = new GameState(first.id(), chapter.id(), "opening", session, 0, Map.of());
        snapshot(1, now, old);
        drafts.states.put(session, new GameState(first.id(), chapter.id(), "opening", session, 1, Map.of(question, 1)));
        var restored = game.load(1, true).orElseThrow();
        assertEquals(session, restored.assessmentSessionId()); assertEquals(1, restored.firstAnswers().get(question));
        assertTrue(game.hasUnsavedProgress()); // Restored newer draft differs from the older saved slot.
    }
    @Test void completedAssessmentCannotBeRevivedFromOldSave() {
        start(); UUID session = UUID.randomUUID(); snapshot(1, now,
                new GameState(first.id(), chapter.id(), "opening", session, 0, Map.of()));
        var before = state();
        data.saveCompleted(new AssessmentResult(new Attempt(UUID.randomUUID(), first.id(), chapter.id(), 0, 1, now),
                Difficulty.MEDIUM, Difficulty.EASY));
        assertThrows(IllegalStateException.class, () -> game.load(1, true)); assertEquals(before, state());
    }
    @Test void invalidSlotsAndWrongOwnerAdapterDataAreRejected() {
        start(); assertThrows(IllegalArgumentException.class, () -> game.save(4, true));
        assertThrows(IllegalArgumentException.class, () -> game.load(0, true));
        var foreign = new GameState(UUID.randomUUID(), chapter.id(), "opening", null, 0, Map.of());
        var malicious = new SaveSnapshotRepository() {
            SaveSnapshot result = new SaveSnapshot(new SaveGame(foreign.studentId(), 1, chapter.id(), "opening", Difficulty.MEDIUM, now), foreign);
            public SaveSnapshot saveSnapshot(SaveSnapshot s) { return result; }
            public Optional<SaveSnapshot> findSnapshot(UUID id, int slot) { return Optional.of(result); }
            public List<SaveSnapshot> snapshotsFor(UUID id) { return List.of(result); }
        };
        var guarded = facadeWith(malicious);
        assertThrows(SecurityException.class, guarded::snapshots);
        assertThrows(SecurityException.class, () -> guarded.load(1, true));
    }
    @Test void draftFailureProducesNoFeedbackOrCurrentSessionChange() {
        start(); var before = state(); drafts.failCreate = true;
        assertThrows(IllegalStateException.class, () -> game.startAssessment(chapter.id()));
        assertEquals(before, state());
    }
    @Test void firstAnswerIsStoredBeforeFeedbackAndRepeatKeepsOriginal() {
        start(); UUID session = game.startAssessment(chapter.id()); UUID question = chapter.questions().getFirst().id();
        assertFalse(game.submit(session, question, 1)); assertEquals(1, drafts.findDraft(session).orElseThrow().firstAnswers().get(question));
        assertFalse(game.submit(session, question, 0)); assertEquals(1, state().firstAnswers().get(question));
        assertEquals(0, game.complete(session).attempt().correctAnswers());
    }
    @Test void firstAnswerWriteFailureDoesNotShowFeedbackOrChangeScoreState() {
        start(); UUID session = game.startAssessment(chapter.id()); var before = state(); drafts.failWrite = true;
        assertThrows(IllegalStateException.class, () -> game.submit(session, chapter.questions().getFirst().id(), 0));
        assertEquals(before, state()); assertThrows(IllegalStateException.class, () -> game.complete(session));
    }
    @Test void restoredSessionHasAccountAccessButBaselineRestoreIsExplicitlyUnimplemented() {
        start(); UUID session = game.startAssessment(chapter.id()); game.save(1, false);
        game.load(1, true); // Test story port keeps the same still-active AssessmentService session.
        assertEquals(1, game.questions(session).size());
        assertThrows(UnsupportedOperationException.class, () -> backend.assessmentService().restoreAssessment(state()));
    }

    private final class Drafts implements AssessmentDraftRepository {
        final Map<UUID, GameState> states = new HashMap<>(); boolean failCreate, failWrite;
        public void createDraft(GameState state) {
            if (failCreate) throw new IllegalStateException("Draft storage failed");
            if (states.putIfAbsent(state.assessmentSessionId(), state) != null) throw new IllegalArgumentException("Duplicate draft");
        }
        public int recordFirstAnswer(UUID session, UUID student, UUID question, int option) {
            if (failWrite) throw new IllegalStateException("First answer storage failed");
            var before = states.get(session); assertEquals(student, before.studentId());
            Map<UUID, Integer> answers = new HashMap<>(before.firstAnswers()); answers.putIfAbsent(question, option);
            states.put(session, new GameState(student, before.chapterId(), before.storyNodeId(), session, answers.size(), answers));
            return answers.get(question);
        }
        public Optional<GameState> findDraft(UUID session) { return Optional.ofNullable(states.get(session)); }
    }
    private static final class TestGameplay implements StoryService, StoryRepository {
        final Map<UUID, GameState> current = new HashMap<>(); boolean failRestore, failCleanup;
        public GameState startChapter(UUID student, UUID chapter) {
            GameState state = new GameState(student, chapter, "opening", null, 0, Map.of()); current.put(student, state); return state;
        }
        public GameState advanceStory(UUID student) {
            var previous = current.get(student); var next = new GameState(student, previous.chapterId(), "ending", null, 0, Map.of());
            current.put(student, next); return next;
        }
        public GameState chooseStory(UUID student, String choice) { return advanceStory(student); }
        public Optional<GameState> currentState(UUID student) { return Optional.ofNullable(current.get(student)); }
        public GameState restoreState(GameState state) {
            if (failRestore) throw new UnsupportedOperationException("Restore not connected");
            current.put(state.studentId(), state); return state;
        }
        public void clearStateFor(UUID student) {
            if (failCleanup) throw new IllegalStateException("Cleanup failed");
            current.remove(student);
        }
        public Optional<String> firstNodeId(UUID chapter) { return Optional.of("opening"); }
        public Optional<StoryNode> findNode(UUID chapter, String node) {
            if (!Set.of("opening", "ending").contains(node)) return Optional.empty();
            return Optional.of(new StoryNode(node, "Mia", "A garden.", false, List.of(), "", true));
        }
    }
}

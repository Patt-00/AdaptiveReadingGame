package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Dev 1's controller-facing boundary. No operation accepts an arbitrary learner ID.
 * StoryService owns narrative/assessment restoration; repositories own durable writes.
 * A UI confirmation never grants access to a different account's data.
 */
public final class AccountGameService {
    /** Chapter picker data deliberately excludes passages, questions, and answer keys. */
    public record ChapterSummary(UUID id, String title, Difficulty difficulty) {}
    private final AuthService auth;
    private final ProgressService progress;
    private final SaveSnapshotRepository snapshots;
    private final StoryService story;
    private final StoryRepository nodes;
    private final AssessmentDraftRepository drafts;
    private final ChapterRepository chapters;
    private final AssessmentRepository results;
    private final AssessmentService assessments;
    private final Clock clock;
    private GameState currentGame;
    private boolean hasUnsavedProgress;
    // Only sessions created through this boundary can be submitted/completed through it.
    private final Map<UUID, UUID> ownedSessions = new HashMap<>();

    public AccountGameService(AuthService auth, ProgressService progress,
            SaveSnapshotRepository snapshots, StoryService story, StoryRepository nodes,
            AssessmentDraftRepository drafts, ChapterRepository chapters,
            AssessmentRepository results, AssessmentService assessments, Clock clock) {
        this.auth = Objects.requireNonNull(auth);
        this.progress = Objects.requireNonNull(progress);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.story = Objects.requireNonNull(story);
        this.nodes = Objects.requireNonNull(nodes);
        this.drafts = Objects.requireNonNull(drafts);
        this.chapters = Objects.requireNonNull(chapters);
        this.results = Objects.requireNonNull(results);
        this.assessments = Objects.requireNonNull(assessments);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized Account signup(String username, String displayName, char[] password) {
        return auth.signup(username, displayName, password);
    }
    public synchronized Account login(String username, char[] password) { return auth.login(username, password); }
    public synchronized Optional<Account> currentAccount() { return auth.currentAccount(); }
    public synchronized Optional<GameState> currentGame() {
        UUID studentId = studentId();
        if (currentGame != null) requireOwner(currentGame, studentId);
        return Optional.ofNullable(currentGame);
    }
    public synchronized boolean hasUnsavedProgress() { studentId(); return hasUnsavedProgress; }
    public synchronized List<AssessmentResult> history() { return List.copyOf(progress.history(studentId())); }
    public synchronized List<ChapterSummary> availableChapters(Difficulty difficulty) {
        studentId(); return chapters.findByDifficulty(Objects.requireNonNull(difficulty)).stream()
                .map(chapter -> new ChapterSummary(chapter.id(), chapter.title(), chapter.difficulty())).toList();
    }
    public synchronized List<SaveSnapshot> snapshots() {
        UUID studentId = studentId();
        return snapshots.snapshotsFor(studentId).stream().peek(s -> requireOwner(s.gameState(), studentId))
                .sorted(Comparator.comparingInt(s -> s.checkpoint().slot())).toList();
    }
    public synchronized boolean canContinue() { return !snapshots().isEmpty(); }

    public synchronized GameState startChapter(UUID chapterId, boolean discardConfirmed) {
        UUID studentId = studentId(); requireDiscardConfirmation(discardConfirmed);
        chapters.findChapter(Objects.requireNonNull(chapterId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown chapter"));
        GameState next = story.startChapter(studentId, chapterId);
        validateState(next, studentId);
        currentGame = next; hasUnsavedProgress = true; return next;
    }
    public synchronized GameState advanceStory() {
        GameState previous = requireGame();
        requireStoryPhase(previous);
        GameState next = story.advanceStory(previous.studentId());
        validateState(next, previous.studentId());
        currentGame = next; hasUnsavedProgress = true; return next;
    }
    public synchronized GameState chooseStory(String choiceId) {
        GameState previous = requireGame();
        requireStoryPhase(previous);
        GameState next = story.chooseStory(previous.studentId(), Objects.requireNonNull(choiceId));
        validateState(next, previous.studentId());
        currentGame = next; hasUnsavedProgress = true; return next;
    }

    /** A basic checkpoint alone is intentionally not treated as a resumable full save. */
    public synchronized SaveSnapshot save(int slot, boolean overwriteConfirmed) {
        requireSlot(slot); GameState state = requireGame(); validateState(state, state.studentId());
        if (progress.load(state.studentId(), slot).isPresent() && !overwriteConfirmed)
            throw new ConfirmationRequiredException("This slot is occupied. Overwrite it?");
        Chapter chapter = chapters.findChapter(state.chapterId()).orElseThrow();
        SaveSnapshot requested = new SaveSnapshot(new SaveGame(state.studentId(), slot, state.chapterId(),
                state.storyNodeId(), chapter.difficulty(), clock.instant().truncatedTo(ChronoUnit.MICROS)), state);
        // Do not announce success or clear dirty state until storage returns the written snapshot.
        SaveSnapshot stored = snapshots.saveSnapshot(requested);
        if (!requested.equals(stored)) throw new IllegalStateException("Storage did not return the requested save");
        hasUnsavedProgress = false;
        return stored;
    }
    public synchronized Optional<GameState> load(int slot, boolean discardConfirmed) {
        UUID studentId = studentId(); requireSlot(slot);
        Optional<SaveSnapshot> snapshot = snapshots.findSnapshot(studentId, slot);
        if (snapshot.isEmpty()) return Optional.empty();
        requireDiscardConfirmation(discardConfirmed);
        return Optional.of(restore(snapshot.get(), studentId));
    }
    public synchronized Optional<GameState> continueGame(boolean discardConfirmed) {
        UUID studentId = studentId();
        Optional<SaveSnapshot> snapshot = snapshots().stream().sorted(Comparator
                .comparing((SaveSnapshot s) -> s.checkpoint().savedAt()).reversed()
                .thenComparingInt(s -> s.checkpoint().slot())).findFirst();
        if (snapshot.isEmpty()) return Optional.empty();
        requireDiscardConfirmation(discardConfirmed);
        return Optional.of(restore(snapshot.get(), studentId));
    }
    private GameState restore(SaveSnapshot snapshot, UUID studentId) {
        GameState saved = snapshot.gameState(); validateState(saved, studentId);
        Chapter chapter = chapters.findChapter(saved.chapterId()).orElseThrow();
        if (snapshot.checkpoint().difficulty() != chapter.difficulty())
            throw new IllegalArgumentException("Saved chapter difficulty no longer matches");
        GameState authoritative = saved;
        if (saved.assessmentSessionId() != null) {
            if (results.findCompleted(studentId, saved.chapterId()).isPresent())
                throw new IllegalStateException("This reading check is already completed. Open results instead.");
            GameState draft = drafts.findDraft(saved.assessmentSessionId())
                    .orElseThrow(() -> new IllegalStateException("The original reading-check draft is unavailable"));
            validateState(draft, studentId);
            if (!draft.chapterId().equals(saved.chapterId())
                    || !Objects.equals(draft.assessmentSessionId(), saved.assessmentSessionId()))
                throw new IllegalArgumentException("Reading-check draft does not match the save");
            // An old save cannot reset first answers. The database draft wins completely.
            authoritative = new GameState(studentId, saved.chapterId(), saved.storyNodeId(),
                    saved.assessmentSessionId(), draft.questionIndex(), draft.firstAnswers());
        }
        // Dev 2's contract validates/restores all game/question state before replacing it.
        GameState restored = story.restoreState(authoritative);
        validateState(restored, studentId);
        if (!restored.chapterId().equals(authoritative.chapterId())
                || !restored.storyNodeId().equals(authoritative.storyNodeId())
                || !Objects.equals(restored.assessmentSessionId(), authoritative.assessmentSessionId())
                || !restored.firstAnswers().entrySet().containsAll(authoritative.firstAnswers().entrySet()))
            throw new IllegalStateException("Story restore returned inconsistent state");
        currentGame = restored;
        hasUnsavedProgress = !restored.equals(saved);
        if (restored.assessmentSessionId() != null)
            ownedSessions.put(restored.assessmentSessionId(), studentId);
        return restored;
    }
    public synchronized void logout(boolean discardConfirmed) {
        UUID studentId = studentId(); requireDiscardConfirmation(discardConfirmed);
        try { story.clearStateFor(studentId); }
        finally {
            assessments.clearSessionsFor(studentId);
            ownedSessions.entrySet().removeIf(e -> e.getValue().equals(studentId));
            currentGame = null; hasUnsavedProgress = false; auth.logout();
        }
    }

    /** Account-safe access to the existing baseline, without changing Dev 2's grading code. */
    public synchronized UUID startAssessment(UUID chapterId) {
        UUID studentId = studentId();
        requireGame();
        {
            requireOwner(currentGame, studentId);
            if (!currentGame.chapterId().equals(chapterId)) throw new IllegalArgumentException("Reading check must match the active chapter");
            if (currentGame.assessmentSessionId() != null) {
                // A port may only publish restored state after restoring this same assessment service.
                assessments.questions(currentGame.assessmentSessionId());
                ownedSessions.put(currentGame.assessmentSessionId(), studentId);
                return currentGame.assessmentSessionId();
            }
            if (!nodes.findNode(chapterId, currentGame.storyNodeId()).orElseThrow().readingCheck())
                throw new IllegalStateException("Finish the story before starting its reading check");
        }
        UUID sessionId = assessments.start(studentId, Objects.requireNonNull(chapterId));
        GameState started = new GameState(studentId, chapterId, currentGame.storyNodeId(), sessionId, 0, Map.of());
        try { drafts.createDraft(started); }
        catch (RuntimeException failure) { assessments.discard(sessionId); throw failure; }
        currentGame = started;
        ownedSessions.put(sessionId, studentId); hasUnsavedProgress = true; return sessionId;
    }
    public synchronized List<AssessmentService.QuestionPrompt> questions(UUID sessionId) {
        requireSessionOwner(sessionId); return assessments.questions(sessionId);
    }
    public synchronized boolean submit(UUID sessionId, UUID questionId, int selectedIndex) {
        requireSessionOwner(sessionId);
        {
            if (results.findCompleted(studentId(), currentGame.chapterId()).isPresent())
                throw new IllegalStateException("Assessment already completed");
            var prompts = assessments.questions(sessionId);
            var question = prompts.stream().filter(q -> q.id().equals(questionId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Question not in this chapter"));
            if (selectedIndex < 0 || selectedIndex >= question.choices().size())
                throw new IllegalArgumentException("Invalid choice");
            // Account coordinator requires a durable first-answer write before any feedback.
            int storedIndex = drafts.recordFirstAnswer(sessionId, studentId(), questionId, selectedIndex);
            if (storedIndex < 0 || storedIndex >= question.choices().size())
                throw new IllegalStateException("Storage returned an invalid answer");
            Map<UUID, Integer> firstAnswers = new HashMap<>(currentGame.firstAnswers());
            firstAnswers.put(questionId, storedIndex);
            currentGame = new GameState(studentId(), currentGame.chapterId(), currentGame.storyNodeId(),
                    sessionId, firstAnswers.size(), firstAnswers);
            hasUnsavedProgress = true;
            return assessments.submit(sessionId, questionId, storedIndex);
        }
    }
    public synchronized AssessmentResult complete(UUID sessionId) {
        requireSessionOwner(sessionId);
        AssessmentResult result = assessments.complete(sessionId);
        hasUnsavedProgress = true; return result;
    }
    private void requireSessionOwner(UUID sessionId) {
        UUID studentId = studentId();
        if (!studentId.equals(ownedSessions.get(Objects.requireNonNull(sessionId))))
            throw new SecurityException("This reading check is not available to the signed-in account");
        requireGame();
        if (!sessionId.equals(currentGame.assessmentSessionId()))
            throw new IllegalStateException("This reading check is not the active game's session");
    }
    private static void requireStoryPhase(GameState state) {
        if (state.assessmentSessionId() != null)
            throw new IllegalStateException("Finish this reading check and choose the next chapter from results");
    }
    private UUID studentId() {
        return auth.currentAccount().orElseThrow(() -> new SecurityException("Please log in first")).id();
    }
    private GameState requireGame() {
        UUID studentId = studentId();
        if (currentGame == null) throw new IllegalStateException("No game is active yet");
        requireOwner(currentGame, studentId); return currentGame;
    }
    private static void requireOwner(GameState state, UUID studentId) {
        if (!Objects.requireNonNull(state).studentId().equals(studentId))
            throw new SecurityException("Progress does not belong to the signed-in account");
    }
    private void validateState(GameState state, UUID studentId) {
        requireOwner(state, studentId);
        Chapter chapter = chapters.findChapter(state.chapterId())
                .orElseThrow(() -> new IllegalArgumentException("Saved chapter is unavailable"));
        StoryNode node = nodes.findNode(state.chapterId(), state.storyNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Saved story node is unavailable"));
        if (state.assessmentSessionId() != null && !node.readingCheck())
            throw new IllegalArgumentException("Reading-check state must be at a reading-check story node");
        if (state.questionIndex() > chapter.questions().size()) throw new IllegalArgumentException("Invalid question position");
        for (var answer : state.firstAnswers().entrySet()) {
            Question question = chapter.questions().stream().filter(q -> q.id().equals(answer.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Saved answer belongs to another chapter"));
            if (answer.getValue() < 0 || answer.getValue() >= question.choices().size())
                throw new IllegalArgumentException("Invalid saved answer");
        }
    }
    private void requireDiscardConfirmation(boolean confirmed) {
        if (hasUnsavedProgress && !confirmed)
            throw new ConfirmationRequiredException("Unsaved progress would be lost. Continue?");
    }
    private static void requireSlot(int slot) {
        if (slot < 1 || slot > 3) throw new IllegalArgumentException("Slot must be 1..3");
    }
}

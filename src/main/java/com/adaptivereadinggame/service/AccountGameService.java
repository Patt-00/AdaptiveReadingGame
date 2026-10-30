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
    /** Current screen data excludes question answer keys and is scoped to the signed-in learner. */
    public record GameView(GameState state, ChapterSummary chapter, StoryNode storyNode, String passage,
                           Optional<AssessmentResult> completedResult) {}
    public record SaveDescription(SaveSnapshot snapshot, String chapterTitle, String phase, String route) {}
    public record HistoryDescription(AssessmentResult result, String chapterTitle, String checkpointTitle) {}
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
    public synchronized Optional<GameView> currentGameView() {
        UUID studentId = studentId();
        if (currentGame == null) return Optional.empty();
        requireOwner(currentGame, studentId);
        Chapter chapter = chapters.findChapter(currentGame.chapterId())
                .orElseThrow(() -> new IllegalStateException("The active chapter is unavailable."));
        StoryNode node = nodes.findNode(currentGame.chapterId(), currentGame.storyNodeId())
                .orElseThrow(() -> new IllegalStateException("The active story node is unavailable."));
        String passage = chapter.adaptive() && !currentGame.activeCheckpointId().isEmpty()
                ? chapter.checkpoint(currentGame.activeCheckpointId()).orElseThrow().passage()
                : chapter.passage();
        return Optional.of(new GameView(currentGame,
                new ChapterSummary(chapter.id(), chapter.title(), currentGame.activeDifficulty() == null
                        ? chapter.difficulty() : currentGame.activeDifficulty()), node,
                passage, completedResultForCurrentState(currentGame, chapter, node, studentId)));
    }
    public synchronized boolean hasUnsavedProgress() { studentId(); return hasUnsavedProgress; }
    public synchronized Optional<AssessmentResult> currentResult() {
        GameState state = requireGame();
        Chapter chapter = chapters.findChapter(state.chapterId()).orElseThrow();
        StoryNode node = nodes.findNode(state.chapterId(), state.storyNodeId()).orElseThrow();
        return completedResultForCurrentState(state, chapter, node, state.studentId());
    }
    public synchronized List<AssessmentResult> episodeResults() {
        GameState state = requireGame();
        List<AssessmentResult> chapterResults = results.resultsForChapter(state.studentId(), state.chapterId());
        if (state.routeId().isEmpty()) return chapterResults;
        Set<String> routeCheckpoints = checkpointsOnRoute(state.chapterId(), state.routeId());
        return chapterResults.stream().filter(result -> routeCheckpoints.contains(result.attempt().checkpointId())).toList();
    }
    private Set<String> checkpointsOnRoute(UUID chapterId, String routeId) {
        Set<String> checkpoints = new HashSet<>();
        Set<String> visited = new HashSet<>();
        var remaining = new java.util.ArrayDeque<String>();
        remaining.add(nodes.firstNodeId(chapterId).orElseThrow());
        Chapter chapter = chapters.findChapter(chapterId).orElseThrow();
        while (!remaining.isEmpty()) {
            String nodeId = remaining.removeFirst();
            if (!visited.add(nodeId)) continue;
            StoryNode node = nodes.findNode(chapterId, nodeId).orElseThrow();
            if (node.readingCheck()) {
                var checkpoint = chapter.checkpointAtNode(nodeId).orElseThrow();
                checkpoints.add(checkpoint.id());
                remaining.add(checkpoint.correctNextNodeId());
                remaining.add(checkpoint.incorrectNextNodeId());
            } else if (!node.storyChoices().isEmpty()) {
                node.storyChoices().stream().filter(choice -> choice.routeId().isEmpty()
                        || choice.routeId().equals(routeId)).forEach(choice -> remaining.add(choice.nextNodeId()));
            } else if (!node.nextNodeId().isEmpty()) remaining.add(node.nextNodeId());
        }
        return checkpoints;
    }
    public synchronized boolean checkpointEndsSection() {
        GameState state = requireGame();
        if (state.activeCheckpointId().isEmpty()) return false;
        AssessmentResult result = results.findCompleted(state.studentId(), state.chapterId(), state.activeCheckpointId())
                .orElseThrow(() -> new IllegalStateException("The checkpoint result is not stored."));
        var chapter = chapters.findChapter(state.chapterId()).orElseThrow();
        var checkpoint = chapter.checkpoint(state.activeCheckpointId())
                .orElseThrow(() -> new IllegalStateException("The active checkpoint definition is unavailable."));
        boolean correct = result.attempt().correctAnswers() == result.attempt().totalQuestions();
        String destination = correct ? checkpoint.correctNextNodeId() : checkpoint.incorrectNextNodeId();
        return reachesCompletionWithoutAnotherCheckpoint(state.chapterId(), destination,
                new HashSet<>(), new HashMap<>());
    }
    public synchronized List<AssessmentResult> history() { return List.copyOf(progress.history(studentId())); }
    public synchronized List<HistoryDescription> historyDescriptions() {
        return history().stream().map(result -> new HistoryDescription(result,
                chapters.findChapter(result.attempt().materialId()).map(Chapter::title).orElse("Unavailable chapter"),
                checkpointLabel(result.attempt().checkpointId()))).toList();
    }

    public synchronized SaveDescription describeSnapshot(SaveSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        UUID learnerId = studentId();
        GameState state = snapshot.gameState();
        requireOwner(state, learnerId);
        Optional<Chapter> chapter = chapters.findChapter(state.chapterId());
        Optional<StoryNode> node = nodes.findNode(state.chapterId(), state.storyNodeId());
        String phase = "Story";
        if (chapter.isEmpty() || node.isEmpty()) phase = "Content unavailable";
        else if (node.get().storyComplete()) phase = "Completed";
        else if (completedResultForCurrentState(state, chapter.get(), node.get(), learnerId).isPresent()) phase = "Results";
        else if (state.assessmentSessionId() != null || node.get().readingCheck()) phase = "Reading check";
        String route = switch (state.routeId()) {
            case "study-first" -> "Study first";
            case "hall-first" -> "Hall first";
            default -> "";
        };
        return new SaveDescription(snapshot, chapter.map(Chapter::title).orElse("Unavailable chapter"), phase, route);
    }

    private static String checkpointLabel(String id) {
        if (id.startsWith("study-")) return id.endsWith("first") ? "Study, first" : "Study, second";
        if (id.startsWith("hall-")) return id.endsWith("first") ? "Hall, first" : "Hall, second";
        return "Reading check";
    }
    public synchronized List<ChapterSummary> availableChapters(Difficulty difficulty) {
        UUID learnerId = studentId();
        return chapters.findByDifficulty(Objects.requireNonNull(difficulty)).stream()
                .filter(chapter -> chapter.adaptive() || results.resultsForChapter(learnerId, chapter.id()).isEmpty())
                .map(chapter -> new ChapterSummary(chapter.id(), chapter.title(), difficulty)).toList();
    }
    public synchronized List<SaveSnapshot> snapshots() {
        UUID studentId = studentId();
        return snapshots.snapshotsFor(studentId).stream().peek(s -> requireOwner(s.gameState(), studentId))
                .sorted(Comparator.comparingInt(s -> s.checkpoint().slot())).toList();
    }
    public synchronized boolean canContinue() { return !snapshots().isEmpty(); }

    public synchronized GameState startChapter(UUID chapterId, boolean discardConfirmed) {
        Chapter chapter = chapters.findChapter(Objects.requireNonNull(chapterId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown chapter"));
        return startChapter(chapterId, chapter.difficulty(), discardConfirmed);
    }

    public synchronized GameState startChapter(UUID chapterId, Difficulty initialDifficulty, boolean discardConfirmed) {
        UUID studentId = studentId(); requireDiscardConfirmation(discardConfirmed);
        Objects.requireNonNull(initialDifficulty, "initialDifficulty");
        Chapter chapter = chapters.findChapter(Objects.requireNonNull(chapterId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown chapter"));
        if (!chapter.adaptive() && chapter.difficulty() != initialDifficulty)
            throw new IllegalArgumentException("Selected difficulty does not match this legacy chapter.");
        if (chapter.adaptive() && !chapter.checkpoints().stream()
                .allMatch(checkpoint -> checkpoint.questionsByDifficulty().containsKey(initialDifficulty)))
            throw new IllegalArgumentException("The chapter does not contain the selected initial difficulty.");
        GameState previous = currentGame;
        GameState next = story.startChapter(studentId, chapterId, initialDifficulty);
        validateState(next, studentId);
        currentGame = next; hasUnsavedProgress = true;
        discardReplacedSession(previous, next);
        return next;
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
        Difficulty saveDifficulty = state.activeDifficulty() == null ? chapter.difficulty() : state.activeDifficulty();
        SaveSnapshot requested = new SaveSnapshot(new SaveGame(state.studentId(), slot, state.chapterId(),
                state.storyNodeId(), saveDifficulty, clock.instant().truncatedTo(ChronoUnit.MICROS),
                state.routeId(), state.activeCheckpointId()), state);
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
        if (!chapter.adaptive() && snapshot.checkpoint().difficulty() != chapter.difficulty())
            throw new IllegalArgumentException("Saved chapter difficulty no longer matches");
        GameState authoritative = saved;
        boolean completedSavedAssessment = false;
        if (saved.assessmentSessionId() != null) {
            Optional<AssessmentResult> completed = results.findCompleted(studentId, saved.chapterId(), saved.activeCheckpointId());
            if (completed.isPresent()) {
                authoritative = new GameState(studentId, saved.chapterId(), saved.storyNodeId(), null, 0, Map.of(),
                        saved.routeId(), saved.activeCheckpointId(), saved.activeDifficulty(),
                        saved.lastCompletedCheckpointId());
                completedSavedAssessment = true;
            } else {
                GameState draft = drafts.findDraft(saved.assessmentSessionId())
                        .orElseThrow(() -> new IllegalStateException("The original reading-check draft is unavailable"));
                validateState(draft, studentId);
                if (!draft.chapterId().equals(saved.chapterId())
                        || !Objects.equals(draft.assessmentSessionId(), saved.assessmentSessionId())
                        || !draft.storyNodeId().equals(saved.storyNodeId())
                        || !draft.routeId().equals(saved.routeId())
                        || !draft.activeCheckpointId().equals(saved.activeCheckpointId())
                        || draft.activeDifficulty() != saved.activeDifficulty()
                        || !draft.lastCompletedCheckpointId().equals(saved.lastCompletedCheckpointId()))
                    throw new IllegalArgumentException("Reading-check draft does not match the save");
                // An old save cannot reset first answers. The database draft wins completely.
                authoritative = new GameState(studentId, saved.chapterId(), saved.storyNodeId(),
                        saved.assessmentSessionId(), draft.questionIndex(), draft.firstAnswers(),
                        saved.routeId(), saved.activeCheckpointId(), saved.activeDifficulty(),
                        draft.lastCompletedCheckpointId());
            }
        }
        // The story service validates/restores game and question state before replacing it.
        GameState previous = currentGame;
        GameState restored = story.restoreState(authoritative);
        validateState(restored, studentId);
        if (!restored.chapterId().equals(authoritative.chapterId())
                || !restored.storyNodeId().equals(authoritative.storyNodeId())
                || !Objects.equals(restored.assessmentSessionId(), authoritative.assessmentSessionId())
                || !restored.routeId().equals(authoritative.routeId())
                || !restored.activeCheckpointId().equals(authoritative.activeCheckpointId())
                || restored.activeDifficulty() != authoritative.activeDifficulty()
                || !restored.lastCompletedCheckpointId().equals(authoritative.lastCompletedCheckpointId())
                || !restored.firstAnswers().entrySet().containsAll(authoritative.firstAnswers().entrySet()))
            throw new IllegalStateException("Story restore returned inconsistent state");
        currentGame = restored;
        hasUnsavedProgress = !completedSavedAssessment && !restored.equals(saved);
        if (restored.assessmentSessionId() != null)
            ownedSessions.put(restored.assessmentSessionId(), studentId);
        discardReplacedSession(previous, restored);
        return restored;
    }
    private void discardReplacedSession(GameState previous, GameState next) {
        if (previous == null || previous.assessmentSessionId() == null
                || Objects.equals(previous.assessmentSessionId(), next.assessmentSessionId())) return;
        UUID oldSession = previous.assessmentSessionId();
        ownedSessions.remove(oldSession);
        assessments.discard(oldSession);
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

    /** Account-safe access to the assessment service; scoring remains in Java. */
    public synchronized UUID startAssessment(UUID chapterId) {
        UUID studentId = studentId();
        requireGame();
        Chapter chapter;
        String checkpointId;
        Difficulty selectedDifficulty;
        {
            requireOwner(currentGame, studentId);
            if (!currentGame.chapterId().equals(chapterId)) throw new IllegalArgumentException("Reading check must match the active chapter");
            chapter = chapters.findChapter(chapterId).orElseThrow(() -> new IllegalArgumentException("Unknown chapter"));
            StoryNode currentNode = nodes.findNode(chapterId, currentGame.storyNodeId()).orElseThrow();
            if (!currentNode.readingCheck())
                throw new IllegalStateException("Finish the story before starting its reading check");
            checkpointId = chapter.adaptive()
                    ? chapter.checkpointAtNode(currentNode.storyNodeId()).map(StoryCheckpoint::id)
                            .orElseThrow(() -> new IllegalStateException("The reading check has no checkpoint definition."))
                    : "";
            if (!currentGame.activeCheckpointId().isEmpty() && !currentGame.activeCheckpointId().equals(checkpointId))
                throw new IllegalStateException("A different reading checkpoint is already active.");
            selectedDifficulty = currentGame.activeDifficulty() == null ? chapter.difficulty() : currentGame.activeDifficulty();
            if (currentGame.assessmentSessionId() != null) {
                // A port may only publish restored state after restoring this same assessment service.
                assessments.questions(currentGame.assessmentSessionId());
                ownedSessions.put(currentGame.assessmentSessionId(), studentId);
                return currentGame.assessmentSessionId();
            }
            if (results.findCompleted(studentId, chapterId, checkpointId).isPresent())
                throw new IllegalStateException("This reading checkpoint is already complete. Open its results instead.");
        }
        UUID sessionId = chapter.adaptive()
                ? assessments.start(studentId, chapterId, checkpointId, selectedDifficulty)
                : assessments.start(studentId, Objects.requireNonNull(chapterId));
        GameState started = new GameState(studentId, chapterId, currentGame.storyNodeId(), sessionId, 0, Map.of(),
                currentGame.routeId(), checkpointId, selectedDifficulty, currentGame.lastCompletedCheckpointId());
        try { drafts.createDraft(started); }
        catch (RuntimeException failure) { assessments.discard(sessionId); throw failure; }
        currentGame = started;
        ownedSessions.put(sessionId, studentId); hasUnsavedProgress = true; return sessionId;
    }
    public synchronized List<AssessmentService.QuestionPrompt> questions(UUID sessionId) {
        requireSessionOwner(sessionId); return assessments.questions(sessionId);
    }
    public synchronized AssessmentService.QuestionFeedback feedback(UUID sessionId, UUID questionId) {
        requireSessionOwner(sessionId);
        return assessments.feedback(sessionId, questionId);
    }
    public synchronized boolean submit(UUID sessionId, UUID questionId, int selectedIndex) {
        requireSessionOwner(sessionId);
        {
            if (results.findCompleted(studentId(), currentGame.chapterId(), currentGame.activeCheckpointId()).isPresent())
                throw new IllegalStateException("Assessment already completed");
            var prompts = assessments.questions(sessionId);
            var question = prompts.stream().filter(q -> q.id().equals(questionId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Question not in this chapter"));
            if (!currentGame.firstAnswers().containsKey(questionId)) {
                int expectedIndex = currentGame.questionIndex();
                if (expectedIndex >= prompts.size() || !prompts.get(expectedIndex).id().equals(questionId))
                    throw new IllegalStateException("Answer the current reading-check question first.");
            }
            if (selectedIndex < 0 || selectedIndex >= question.choices().size())
                throw new IllegalArgumentException("Invalid choice");
            // Account coordinator requires a durable first-answer write before any feedback.
            int storedIndex = drafts.recordFirstAnswer(sessionId, studentId(), questionId, selectedIndex);
            if (storedIndex < 0 || storedIndex >= question.choices().size())
                throw new IllegalStateException("Storage returned an invalid answer");
            Map<UUID, Integer> firstAnswers = new HashMap<>(currentGame.firstAnswers());
            firstAnswers.put(questionId, storedIndex);
            currentGame = new GameState(studentId(), currentGame.chapterId(), currentGame.storyNodeId(),
                    sessionId, firstAnswers.size(), firstAnswers, currentGame.routeId(),
                    currentGame.activeCheckpointId(), currentGame.activeDifficulty(), currentGame.lastCompletedCheckpointId());
            hasUnsavedProgress = true;
            return assessments.submit(sessionId, questionId, storedIndex);
        }
    }
    public synchronized AssessmentResult complete(UUID sessionId) {
        requireSessionOwner(sessionId);
        AssessmentResult result = assessments.complete(sessionId);
        hasUnsavedProgress = true; return result;
    }

    /** Continue the narrative only after the checkpoint result has been durably stored. */
    public synchronized GameState continueAfterCheckpoint() {
        GameState current = requireGame();
        if (current.activeCheckpointId().isEmpty())
            throw new IllegalStateException("There is no completed adaptive checkpoint to continue from.");
        AssessmentResult result = results.findCompleted(current.studentId(), current.chapterId(), current.activeCheckpointId())
                .orElseThrow(() -> new IllegalStateException("The checkpoint result has not been stored."));
        GameState next = story.completeCheckpoint(current.studentId(), current.activeCheckpointId(),
                result.attempt().correctAnswers() == result.attempt().totalQuestions(), result.nextDifficulty());
        validateState(next, current.studentId());
        currentGame = next;
        UUID completedSession = current.assessmentSessionId();
        if (completedSession != null) {
            ownedSessions.remove(completedSession);
            assessments.discard(completedSession);
        }
        hasUnsavedProgress = true;
        return next;
    }

    /** Skip a checkpoint already completed in an earlier process after its result is verified. */
    public synchronized boolean advanceCompletedCheckpointIfPresent() {
        GameState state = requireGame();
        if (state.assessmentSessionId() != null) return false;
        Chapter chapter = chapters.findChapter(state.chapterId()).orElseThrow();
        if (!chapter.adaptive()) return false;
        StoryNode node = nodes.findNode(state.chapterId(), state.storyNodeId()).orElseThrow();
        if (!node.readingCheck()) return false;
        StoryCheckpoint checkpoint = chapter.checkpointAtNode(node.storyNodeId())
                .orElseThrow(() -> new IllegalStateException("Reading-check node has no checkpoint definition."));
        Optional<AssessmentResult> stored = results.findCompleted(state.studentId(), state.chapterId(), checkpoint.id());
        if (stored.isEmpty()) return false;
        GameState checkpointState = new GameState(state.studentId(), state.chapterId(), state.storyNodeId(),
                null, 0, Map.of(), state.routeId(), checkpoint.id(), stored.get().assessedDifficulty(),
                state.lastCompletedCheckpointId());
        story.restoreState(checkpointState);
        GameState advanced = story.completeCheckpoint(state.studentId(), checkpoint.id(),
                stored.get().attempt().correctAnswers() == stored.get().attempt().totalQuestions(),
                stored.get().nextDifficulty());
        validateState(advanced, state.studentId());
        currentGame = advanced;
        hasUnsavedProgress = true;
        return true;
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
        if (!state.lastCompletedCheckpointId().isEmpty()
                && results.findCompleted(studentId, state.chapterId(), state.lastCompletedCheckpointId()).isEmpty())
            throw new IllegalArgumentException("Saved progress references a checkpoint result that is not stored.");
        if (state.assessmentSessionId() != null && !node.readingCheck())
            throw new IllegalArgumentException("Reading-check state must be at a reading-check story node");
        List<Question> activeQuestions = chapter.questions();
        if (chapter.adaptive() && !state.activeCheckpointId().isEmpty()) {
            if (state.activeDifficulty() == null
                    || chapter.checkpointAtNode(state.storyNodeId())
                            .filter(checkpoint -> checkpoint.id().equals(state.activeCheckpointId())).isEmpty())
                throw new IllegalArgumentException("Active checkpoint metadata does not match the story node.");
            activeQuestions = chapter.questions(state.activeCheckpointId(), state.activeDifficulty());
        } else if (chapter.adaptive() && state.assessmentSessionId() != null) {
            throw new IllegalArgumentException("An active adaptive assessment needs its checkpoint ID.");
        }
        if (state.questionIndex() > activeQuestions.size()
                || state.questionIndex() != state.firstAnswers().size())
            throw new IllegalArgumentException("Invalid question position");
        for (var answer : state.firstAnswers().entrySet()) {
            Question question = activeQuestions.stream().filter(q -> q.id().equals(answer.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Saved answer belongs to another chapter"));
            if (answer.getValue() < 0 || answer.getValue() >= question.choices().size())
                throw new IllegalArgumentException("Invalid saved answer");
        }
        for (int index = 0; index < state.questionIndex(); index++) {
            if (!state.firstAnswers().containsKey(activeQuestions.get(index).id()))
                throw new IllegalArgumentException("Saved answers must be a continuous sequence of first attempts.");
        }
    }

    private Optional<AssessmentResult> completedResultForCurrentState(GameState state, Chapter chapter,
            StoryNode node, UUID studentId) {
        if (!state.activeCheckpointId().isEmpty())
            return results.findCompleted(studentId, state.chapterId(), state.activeCheckpointId());
        if (!chapter.adaptive()) return results.findCompleted(studentId, state.chapterId());
        if (!node.storyComplete() || state.lastCompletedCheckpointId().isEmpty()) return Optional.empty();
        return results.findCompleted(studentId, state.chapterId(), state.lastCompletedCheckpointId());
    }

    private boolean reachesCompletionWithoutAnotherCheckpoint(UUID chapterId, String nodeId,
            Set<String> visiting, Map<String, Boolean> memo) {
        Boolean known = memo.get(nodeId);
        if (known != null) return known;
        if (!visiting.add(nodeId)) throw new IllegalStateException("The story graph contains a loop.");
        StoryNode node = nodes.findNode(chapterId, nodeId)
                .orElseThrow(() -> new IllegalStateException("A checkpoint outcome node is unavailable."));
        boolean result;
        if (node.storyComplete()) result = true;
        else if (node.readingCheck()) result = false;
        else {
            List<String> destinations = node.storyChoices().isEmpty()
                    ? node.nextNodeId().isEmpty() ? List.of() : List.of(node.nextNodeId())
                    : node.storyChoices().stream().map(StoryChoice::nextNodeId).toList();
            result = !destinations.isEmpty() && destinations.stream().allMatch(destination ->
                    reachesCompletionWithoutAnotherCheckpoint(chapterId, destination, visiting, memo));
        }
        visiting.remove(nodeId);
        memo.put(nodeId, result);
        return result;
    }
    private void requireDiscardConfirmation(boolean confirmed) {
        if (hasUnsavedProgress && !confirmed)
            throw new ConfirmationRequiredException("Unsaved progress would be lost. Continue?");
    }
    private static void requireSlot(int slot) {
        if (slot < 1 || slot > 3) throw new IllegalArgumentException("Slot must be 1..3");
    }
}

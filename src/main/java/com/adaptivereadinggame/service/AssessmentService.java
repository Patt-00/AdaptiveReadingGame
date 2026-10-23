package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/** First-attempt scoring in Java. Active sessions are development-only, process-local state. */
public final class AssessmentService {
    public record QuestionPrompt(UUID id, String prompt, List<String> choices) {
        public QuestionPrompt { choices = List.copyOf(choices); }
    }
    public record QuestionFeedback(boolean correct, String explanation, String correctAnswer) {}
    private static final class Session {
        final UUID studentId;
        final Chapter chapter;
        final Map<UUID, Integer> firstAnswers = new HashMap<>();
        AssessmentResult result;
        CompletableFuture<AssessmentResult> completion;
        Session(UUID studentId, Chapter chapter) { this.studentId = studentId; this.chapter = chapter; }
    }
    private final StudentRepository students;
    private final ChapterRepository chapters;
    private final AssessmentRepository attempts;
    private final AssessmentDraftRepository drafts;
    private final DifficultyPolicy difficulty;
    private final Clock clock;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public AssessmentService(StudentRepository students, ChapterRepository chapters,
                             AssessmentRepository attempts, DifficultyPolicy difficulty, Clock clock) {
        this(students, chapters, attempts, difficulty, clock, null);
    }

    public AssessmentService(StudentRepository students, ChapterRepository chapters,
                             AssessmentRepository attempts, DifficultyPolicy difficulty, Clock clock,
                             AssessmentDraftRepository drafts) {
        this.students = Objects.requireNonNull(students); this.chapters = Objects.requireNonNull(chapters);
        this.attempts = Objects.requireNonNull(attempts); this.difficulty = Objects.requireNonNull(difficulty);
        this.clock = Objects.requireNonNull(clock);
        this.drafts = drafts;
    }
    /** Caller must derive studentId from authenticated account, not arbitrary UI input. */
    public UUID start(UUID studentId, UUID chapterId) {
        students.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Unknown student"));
        Chapter chapter = chapters.findChapter(chapterId).orElseThrow(() -> new IllegalArgumentException("Unknown chapter"));
        UUID id = UUID.randomUUID(); sessions.put(id, new Session(studentId, chapter)); return id;
    }
    public List<QuestionPrompt> questions(UUID sessionId) {
        return session(sessionId).chapter.questions().stream()
                .map(q -> new QuestionPrompt(q.id(), q.prompt(), q.choices())).toList();
    }
    /** Subsequent submissions return feedback for the original answer, never replace it. */
    public boolean submit(UUID sessionId, UUID questionId, int selectedIndex) {
        Session session = session(sessionId);
        synchronized (session) {
            if (session.result != null || session.completion != null)
                throw new IllegalStateException("Assessment is complete or completing");
            Question question = session.chapter.questions().stream().filter(q -> q.id().equals(questionId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Question not in this chapter"));
            if (selectedIndex < 0 || selectedIndex >= question.choices().size())
                throw new IllegalArgumentException("Invalid choice");
            session.firstAnswers.putIfAbsent(questionId, selectedIndex);
            return session.firstAnswers.get(questionId) == question.correctChoiceIndex();
        }
    }

    /** Reveals the explanation and correct choice only after the stored first answer exists. */
    public QuestionFeedback feedback(UUID sessionId, UUID questionId) {
        Session session = session(sessionId);
        synchronized (session) {
            Integer selected = session.firstAnswers.get(Objects.requireNonNull(questionId));
            if (selected == null) throw new IllegalStateException("Submit this question before requesting feedback.");
            Question question = session.chapter.questions().stream().filter(q -> q.id().equals(questionId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Question not in this chapter"));
            boolean correct = selected == question.correctChoiceIndex();
            String correctAnswer = correct ? "" : question.choices().get(question.correctChoiceIndex());
            return new QuestionFeedback(correct, question.explanation(), correctAnswer);
        }
    }
    /**
     * Reserve one completion per session, then run the difficulty policy outside
     * the service-wide monitor so model latency for one learner cannot block others.
     */
    public AssessmentResult complete(UUID sessionId) {
        Session session = session(sessionId);
        CompletableFuture<AssessmentResult> completion;
        Map<UUID, Integer> firstAnswers;
        boolean ownsCompletion;
        synchronized (session) {
            if (session.result != null) return session.result;
            if (session.completion != null) {
                completion = session.completion;
                firstAnswers = Map.of();
                ownsCompletion = false;
            } else {
                if (session.firstAnswers.size() != session.chapter.questions().size())
                    throw new IllegalStateException("Answer all comprehension questions before completing");
                completion = new CompletableFuture<>();
                session.completion = completion;
                firstAnswers = Map.copyOf(session.firstAnswers);
                ownsCompletion = true;
            }
        }
        if (!ownsCompletion) return await(completion);

        try {
            // A replay must not replace an earlier scored completion.
            var existing = attempts.findCompleted(session.studentId, session.chapter.id());
            if (existing.isPresent()) return complete(session, completion, existing.get());

            int correct = (int) session.chapter.questions().stream()
                    .filter(question -> firstAnswers.get(question.id()) == question.correctChoiceIndex()).count();
            int total = session.chapter.questions().size();
            DifficultyPolicy.Recommendation recommendation = difficulty.recommend(new DifficultyPolicy.Context(
                    session.studentId, session.chapter.id(), session.chapter.difficulty(), correct, total));
            Difficulty next = recommendation.nextDifficulty();
            if (next == null || Math.abs(next.ordinal() - session.chapter.difficulty().ordinal()) > 1)
                throw new IllegalStateException("Difficulty policy returned an invalid change");
            var attempt = new Attempt(UUID.randomUUID(), session.studentId, session.chapter.id(), correct, total,
                    clock.instant());
            AssessmentResult stored = Objects.requireNonNull(
                    attempts.saveCompleted(new AssessmentResult(attempt, session.chapter.difficulty(), next,
                            recommendation.source())),
                    "Assessment repository returned no saved result");
            return complete(session, completion, stored);
        } catch (RuntimeException | Error failure) {
            synchronized (session) {
                if (session.completion == completion && session.result == null) session.completion = null;
            }
            completion.completeExceptionally(failure);
            throw failure;
        }
    }

    private static AssessmentResult complete(Session session, CompletableFuture<AssessmentResult> future,
                                             AssessmentResult result) {
        synchronized (session) { session.result = result; }
        future.complete(result);
        return result;
    }

    private static AssessmentResult await(CompletableFuture<AssessmentResult> completion) {
        try {
            return completion.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Waiting for assessment completion was interrupted.", interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Assessment completion failed.", cause);
        }
    }

    public void discard(UUID sessionId) {
        Session session = sessions.get(Objects.requireNonNull(sessionId));
        if (session == null) return;
        CompletableFuture<AssessmentResult> completion;
        synchronized (session) { completion = session.completion; }
        if (completion != null && !completion.isDone()) {
            try { await(completion); }
            catch (RuntimeException ignored) { /* A failed completion can still be discarded. */ }
        }
        sessions.remove(sessionId, session);
    }
    /** Reopen the original unfinished session from its authoritative stored draft. */
    public synchronized UUID restoreAssessment(GameState gameState) {
        Objects.requireNonNull(gameState, "gameState");
        UUID sessionId = Objects.requireNonNull(gameState.assessmentSessionId(), "assessmentSessionId");
        if (drafts == null)
            throw new IllegalStateException("Stored assessment drafts are required to restore a reading check.");
        GameState storedDraft = drafts.findDraft(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("The original assessment draft is unavailable."));
        if (!storedDraft.equals(gameState))
            throw new IllegalArgumentException("The saved reading check does not match its stored first-answer draft.");
        if (gameState.questionIndex() != gameState.firstAnswers().size())
            throw new IllegalArgumentException("Saved question position does not match its first answers.");
        students.findById(gameState.studentId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown student"));
        Chapter chapter = chapters.findChapter(gameState.chapterId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown chapter"));
        if (gameState.questionIndex() > chapter.questions().size())
            throw new IllegalArgumentException("Saved question position exceeds the chapter length.");
        for (var answer : gameState.firstAnswers().entrySet()) {
            Question question = chapter.questions().stream().filter(item -> item.id().equals(answer.getKey()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Saved answer belongs to another chapter"));
            if (answer.getValue() < 0 || answer.getValue() >= question.choices().size())
                throw new IllegalArgumentException("Saved answer choice is not valid for its question.");
        }
        for (int index = 0; index < gameState.questionIndex(); index++) {
            if (!gameState.firstAnswers().containsKey(chapter.questions().get(index).id()))
                throw new IllegalArgumentException("Saved answers must be a continuous sequence of first attempts.");
        }
        if (attempts.findCompleted(gameState.studentId(), gameState.chapterId()).isPresent())
            throw new IllegalStateException("This reading check is already complete.");

        Session existing = sessions.get(sessionId);
        if (existing != null) {
            synchronized (existing) {
                if (!existing.studentId.equals(gameState.studentId())
                        || !existing.chapter.id().equals(gameState.chapterId())
                        || existing.result != null || existing.completion != null
                        || !existing.firstAnswers.equals(gameState.firstAnswers()))
                    throw new IllegalStateException("The original assessment session conflicts with the saved draft.");
            }
            return sessionId;
        }
        Session restored = new Session(gameState.studentId(), chapter);
        restored.firstAnswers.putAll(gameState.firstAnswers());
        if (sessions.putIfAbsent(sessionId, restored) != null)
            throw new IllegalStateException("The assessment session changed while it was being restored.");
        return sessionId;
    }
    /** Dev 1 must call this on logout; completed results remain in the repository. */
    public void clearSessionsFor(UUID studentId) {
        Objects.requireNonNull(studentId);
        sessions.entrySet().stream().filter(entry -> entry.getValue().studentId.equals(studentId))
                .map(Map.Entry::getKey).toList().forEach(this::discard);
    }
    private Session session(UUID id) {
        var session = sessions.get(Objects.requireNonNull(id));
        if (session == null) throw new IllegalArgumentException("Unknown assessment session");
        return session;
    }
}

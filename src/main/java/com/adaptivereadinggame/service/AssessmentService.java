package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.*;
import java.time.Clock;
import java.util.*;

/** First-attempt scoring in Java. Active sessions are development-only, process-local state. */
public final class AssessmentService {
    public record QuestionPrompt(UUID id, String prompt, List<String> choices) {
        public QuestionPrompt { choices = List.copyOf(choices); }
    }
    private static final class Session {
        final UUID studentId;
        final Chapter chapter;
        final Map<UUID, Integer> firstAnswers = new HashMap<>();
        AssessmentResult result;
        Session(UUID studentId, Chapter chapter) { this.studentId = studentId; this.chapter = chapter; }
    }
    private final StudentRepository students;
    private final ChapterRepository chapters;
    private final AssessmentRepository attempts;
    private final DifficultyPolicy difficulty;
    private final Clock clock;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public AssessmentService(StudentRepository students, ChapterRepository chapters,
                             AssessmentRepository attempts, DifficultyPolicy difficulty, Clock clock) {
        this.students = Objects.requireNonNull(students); this.chapters = Objects.requireNonNull(chapters);
        this.attempts = Objects.requireNonNull(attempts); this.difficulty = Objects.requireNonNull(difficulty);
        this.clock = Objects.requireNonNull(clock);
    }
    /** Caller must derive studentId from authenticated account, not arbitrary UI input. */
    public synchronized UUID start(UUID studentId, UUID chapterId) {
        students.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Unknown student"));
        Chapter chapter = chapters.findChapter(chapterId).orElseThrow(() -> new IllegalArgumentException("Unknown chapter"));
        UUID id = UUID.randomUUID(); sessions.put(id, new Session(studentId, chapter)); return id;
    }
    public synchronized List<QuestionPrompt> questions(UUID sessionId) {
        return session(sessionId).chapter.questions().stream()
                .map(q -> new QuestionPrompt(q.id(), q.prompt(), q.choices())).toList();
    }
    /** Subsequent submissions return feedback for the original answer, never replace it. */
    public synchronized boolean submit(UUID sessionId, UUID questionId, int selectedIndex) {
        Session session = session(sessionId);
        if (session.result != null) throw new IllegalStateException("Assessment already completed");
        Question question = session.chapter.questions().stream().filter(q -> q.id().equals(questionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question not in this chapter"));
        if (selectedIndex < 0 || selectedIndex >= question.choices().size()) throw new IllegalArgumentException("Invalid choice");
        session.firstAnswers.putIfAbsent(questionId, selectedIndex);
        return session.firstAnswers.get(questionId) == question.correctChoiceIndex();
    }
    public synchronized AssessmentResult complete(UUID sessionId) {
        Session session = session(sessionId);
        if (session.result != null) return session.result;
        if (session.firstAnswers.size() != session.chapter.questions().size())
            throw new IllegalStateException("Answer all comprehension questions before completing");
        // A replay must not replace an earlier scored completion.
        var existing = attempts.findCompleted(session.studentId, session.chapter.id());
        if (existing.isPresent()) { session.result = existing.get(); return session.result; }
        int correct = (int) session.chapter.questions().stream()
                .filter(q -> session.firstAnswers.get(q.id()) == q.correctChoiceIndex()).count();
        int total = session.chapter.questions().size();
        Difficulty next = difficulty.nextDifficulty(new DifficultyPolicy.Context(session.studentId,
                session.chapter.id(), session.chapter.difficulty(), correct, total));
        if (next == null || Math.abs(next.ordinal() - session.chapter.difficulty().ordinal()) > 1)
            throw new IllegalStateException("Difficulty policy returned an invalid change");
        var attempt = new Attempt(UUID.randomUUID(), session.studentId, session.chapter.id(), correct, total, clock.instant());
        session.result = attempts.saveCompleted(new AssessmentResult(attempt, session.chapter.difficulty(), next));
        return session.result;
    }
    public synchronized void discard(UUID sessionId) { sessions.remove(sessionId); }
    /**
     * Shared Dev 1 declaration for Dev 2's durable-resume implementation.
     * Until implemented, fail BEFORE mutating sessions: never invent a new attempt,
     * reset first answers, or claim an unfinished reading check has been restored.
     * StoryService.restoreState must coordinate this action with its own state.
     */
    public synchronized UUID restoreAssessment(GameState gameState) {
        throw new UnsupportedOperationException("Dev 2's durable reading-check restoration is not connected yet.");
    }
    /** Dev 1 must call this on logout; completed results remain in the repository. */
    public synchronized void clearSessionsFor(UUID studentId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().studentId.equals(studentId));
    }
    private Session session(UUID id) {
        var session = sessions.get(Objects.requireNonNull(id));
        if (session == null) throw new IllegalArgumentException("Unknown assessment session");
        return session;
    }
}

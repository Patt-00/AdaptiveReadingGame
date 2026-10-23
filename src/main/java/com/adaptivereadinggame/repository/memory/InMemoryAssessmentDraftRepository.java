package com.adaptivereadinggame.repository.memory;

import com.adaptivereadinggame.model.GameState;
import com.adaptivereadinggame.model.Question;
import com.adaptivereadinggame.repository.AssessmentDraftRepository;
import com.adaptivereadinggame.repository.AssessmentRepository;
import com.adaptivereadinggame.repository.ChapterRepository;
import com.adaptivereadinggame.repository.StoryRepository;
import com.adaptivereadinggame.repository.StudentRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Volatile first-answer storage with the same ownership rules as the MySQL adapter. */
public final class InMemoryAssessmentDraftRepository implements AssessmentDraftRepository {
    private final StudentRepository students;
    private final ChapterRepository chapters;
    private final StoryRepository stories;
    private final AssessmentRepository assessments;
    private final Map<UUID, GameState> drafts = new ConcurrentHashMap<>();

    public InMemoryAssessmentDraftRepository(StudentRepository students, ChapterRepository chapters,
            StoryRepository stories, AssessmentRepository assessments) {
        this.students = Objects.requireNonNull(students);
        this.chapters = Objects.requireNonNull(chapters);
        this.stories = Objects.requireNonNull(stories);
        this.assessments = Objects.requireNonNull(assessments);
    }

    @Override public synchronized void createDraft(GameState gameState) {
        Objects.requireNonNull(gameState);
        UUID sessionId = Objects.requireNonNull(gameState.assessmentSessionId(), "assessmentSessionId");
        if (gameState.questionIndex() != 0 || !gameState.firstAnswers().isEmpty())
            throw new IllegalArgumentException("A new assessment draft must not contain submitted answers.");
        students.findById(gameState.studentId()).orElseThrow(() -> new IllegalArgumentException("Unknown student."));
        var chapter = chapters.findChapter(gameState.chapterId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown chapter."));
        var node = stories.findNode(gameState.chapterId(), gameState.storyNodeId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown story node."));
        if (!node.readingCheck()) throw new IllegalArgumentException("An assessment draft must reference the reading-check node.");
        if (assessments.findCompleted(gameState.studentId(), gameState.chapterId()).isPresent())
            throw new IllegalStateException("This chapter already has a completed reading check.");
        if (chapter.questions().isEmpty()) throw new IllegalStateException("The chapter has no reading-check questions.");
        if (drafts.putIfAbsent(sessionId, gameState) != null)
            throw new IllegalStateException("An assessment draft already exists for this session.");
    }

    @Override public synchronized int recordFirstAnswer(UUID sessionId, UUID studentId,
            UUID questionId, int selectedIndex) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(questionId);
        GameState current = drafts.get(sessionId);
        if (current == null) throw new IllegalStateException("The assessment draft is unavailable.");
        if (!current.studentId().equals(studentId)) throw new SecurityException("Assessment draft belongs to another learner.");
        if (assessments.findCompleted(studentId, current.chapterId()).isPresent())
            throw new IllegalStateException("This assessment is already complete.");
        var chapter = chapters.findChapter(current.chapterId())
                .orElseThrow(() -> new IllegalStateException("The prepared chapter is unavailable."));
        Question question = chapter.questions().stream().filter(item -> item.id().equals(questionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question does not belong to this chapter."));
        if (selectedIndex < 0 || selectedIndex >= question.choices().size())
            throw new IllegalArgumentException("Invalid answer choice.");
        Integer first = current.firstAnswers().get(questionId);
        if (first != null) return first;
        if (current.questionIndex() >= chapter.questions().size()
                || !chapter.questions().get(current.questionIndex()).id().equals(questionId))
            throw new IllegalStateException("Answer the current reading-check question first.");
        Map<UUID, Integer> answers = new HashMap<>(current.firstAnswers());
        answers.put(questionId, selectedIndex);
        drafts.put(sessionId, new GameState(current.studentId(), current.chapterId(), current.storyNodeId(),
                sessionId, answers.size(), answers));
        return selectedIndex;
    }

    @Override public Optional<GameState> findDraft(UUID sessionId) {
        return Optional.ofNullable(drafts.get(Objects.requireNonNull(sessionId)));
    }
}

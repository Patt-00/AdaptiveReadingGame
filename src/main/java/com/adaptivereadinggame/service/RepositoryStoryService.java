package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.GameState;
import com.adaptivereadinggame.model.Question;
import com.adaptivereadinggame.model.StoryNode;
import com.adaptivereadinggame.repository.ChapterRepository;
import com.adaptivereadinggame.repository.AssessmentDraftRepository;
import com.adaptivereadinggame.repository.StoryRepository;
import com.adaptivereadinggame.repository.StudentRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Generic story transitions driven by prepared nodes, without story-specific code. */
public final class RepositoryStoryService implements StoryService {
    private final StudentRepository students;
    private final ChapterRepository chapters;
    private final StoryRepository stories;
    private final AssessmentDraftRepository drafts;
    private final AssessmentService assessments;
    private final Map<UUID, GameState> activeStates = new HashMap<>();

    public RepositoryStoryService(StudentRepository students, ChapterRepository chapters,
            StoryRepository stories, AssessmentDraftRepository drafts, AssessmentService assessments) {
        this.students = Objects.requireNonNull(students);
        this.chapters = Objects.requireNonNull(chapters);
        this.stories = Objects.requireNonNull(stories);
        this.drafts = Objects.requireNonNull(drafts);
        this.assessments = Objects.requireNonNull(assessments);
    }

    @Override public synchronized GameState startChapter(UUID studentId, UUID chapterId) {
        students.findById(Objects.requireNonNull(studentId)).orElseThrow(() -> new IllegalArgumentException("Unknown student"));
        chapters.findChapter(Objects.requireNonNull(chapterId)).orElseThrow(() -> new IllegalArgumentException("Unknown chapter"));
        String firstNodeId = stories.firstNodeId(chapterId)
                .orElseThrow(() -> new IllegalStateException("This chapter has no prepared story."));
        requireNode(chapterId, firstNodeId);
        GameState started = new GameState(studentId, chapterId, firstNodeId, null, 0, Map.of());
        activeStates.put(studentId, started);
        return started;
    }

    @Override public synchronized GameState advanceStory(UUID studentId) {
        GameState current = requireActive(studentId);
        requireStoryPhase(current);
        StoryNode node = requireNode(current.chapterId(), current.storyNodeId());
        if (node.readingCheck()) throw new IllegalStateException("The story is at its reading check.");
        if (!node.storyChoices().isEmpty()) throw new IllegalStateException("Choose one of the story options to continue.");
        if (node.nextNodeId().isEmpty()) throw new IllegalStateException("This story path has ended.");
        return moveTo(current, node.nextNodeId());
    }

    @Override public synchronized GameState chooseStory(UUID studentId, String choiceId) {
        GameState current = requireActive(studentId);
        requireStoryPhase(current);
        StoryNode node = requireNode(current.chapterId(), current.storyNodeId());
        if (node.readingCheck()) throw new IllegalStateException("The story is at its reading check.");
        String nextNodeId = node.storyChoices().stream()
                .filter(choice -> choice.choiceId().equals(Objects.requireNonNull(choiceId)))
                .map(choice -> choice.nextNodeId()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That story choice is not available here."));
        return moveTo(current, nextNodeId);
    }

    @Override public synchronized Optional<GameState> currentState(UUID studentId) {
        return Optional.ofNullable(activeStates.get(Objects.requireNonNull(studentId)));
    }

    @Override public synchronized GameState restoreState(GameState candidate) {
        Objects.requireNonNull(candidate, "candidate");
        students.findById(candidate.studentId()).orElseThrow(() -> new IllegalArgumentException("Unknown student"));
        var chapter = chapters.findChapter(candidate.chapterId())
                .orElseThrow(() -> new IllegalArgumentException("Saved chapter is unavailable."));
        StoryNode node = requireNode(candidate.chapterId(), candidate.storyNodeId());
        validateAnswers(candidate, chapter.questions());
        if (candidate.assessmentSessionId() == null) {
            if (candidate.questionIndex() != 0 || !candidate.firstAnswers().isEmpty())
                throw new IllegalArgumentException("Story progress without an assessment cannot contain answers.");
        } else {
            if (!node.readingCheck())
                throw new IllegalArgumentException("An unfinished reading check must be saved at its reading-check node.");
            GameState storedDraft = drafts.findDraft(candidate.assessmentSessionId())
                    .orElseThrow(() -> new IllegalArgumentException("The original assessment draft is unavailable."));
            if (!storedDraft.equals(candidate))
                throw new IllegalArgumentException("Saved reading-check state does not match its stored draft.");
            assessments.restoreAssessment(candidate);
        }
        activeStates.put(candidate.studentId(), candidate);
        return candidate;
    }

    @Override public synchronized void clearStateFor(UUID studentId) {
        activeStates.remove(Objects.requireNonNull(studentId));
    }

    private GameState moveTo(GameState current, String nextNodeId) {
        requireNode(current.chapterId(), nextNodeId);
        GameState next = new GameState(current.studentId(), current.chapterId(), nextNodeId, null, 0, Map.of());
        activeStates.put(current.studentId(), next);
        return next;
    }

    private GameState requireActive(UUID studentId) {
        GameState state = activeStates.get(Objects.requireNonNull(studentId));
        if (state == null) throw new IllegalStateException("No active story is available for this learner.");
        if (!state.studentId().equals(studentId)) throw new SecurityException("Story state belongs to another learner.");
        return state;
    }

    private StoryNode requireNode(UUID chapterId, String nodeId) {
        return stories.findNode(chapterId, nodeId)
                .orElseThrow(() -> new IllegalStateException("A required story node is unavailable."));
    }

    private static void requireStoryPhase(GameState state) {
        if (state.assessmentSessionId() != null)
            throw new IllegalStateException("The reading check must finish before the story can continue.");
    }

    private static void validateAnswers(GameState state, java.util.List<Question> questions) {
        if (state.questionIndex() != state.firstAnswers().size())
            throw new IllegalArgumentException("Saved question position does not match its first answers.");
        if (state.questionIndex() > questions.size())
            throw new IllegalArgumentException("Saved question position exceeds the chapter length.");
        for (var answer : state.firstAnswers().entrySet()) {
            Question question = questions.stream().filter(item -> item.id().equals(answer.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Saved answer belongs to another chapter."));
            if (answer.getValue() < 0 || answer.getValue() >= question.choices().size())
                throw new IllegalArgumentException("Saved answer choice is not valid for its question.");
        }
        for (int index = 0; index < state.questionIndex(); index++) {
            if (!state.firstAnswers().containsKey(questions.get(index).id()))
                throw new IllegalArgumentException("Saved answers must be a continuous sequence of first attempts.");
        }
    }
}

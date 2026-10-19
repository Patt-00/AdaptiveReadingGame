package com.adaptivereadinggame.backend;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.memory.InMemoryStoryRepository;
import com.adaptivereadinggame.repository.memory.SampleChapter;
import com.adaptivereadinggame.service.AssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Dev 2 tests: the Chapter 0 data, the in-memory story repository, and Chapter 0 scoring
 * through the real AssessmentService. Story-flow tests (StoryService) come in a separate
 * class once StoryService exists.
 */
class Dev2Test {
    private BackendContext backend;
    private InMemoryStoryRepository story;
    private UUID student;

    @BeforeEach void setup() {
        backend = BackendContext.inMemory();
        SampleChapter.load(backend.chapters());
        story = InMemoryStoryRepository.withSampleChapter();
        student = addStudent("Reader");
    }

    private UUID addStudent(String name) {
        UUID id = UUID.randomUUID();
        backend.students().save(new Student(id, name, ReadingLevel.BEGINNER, 0, 0));
        return id;
    }

    /** Walks the story from the first node, taking choice number `pick` (0-based) at every choice node. */
    private List<String> walk(int pick) {
        List<String> path = new ArrayList<>();
        String id = story.firstNodeId(SampleChapter.chapterId).orElseThrow();
        while (path.size() < 50) {
            StoryNode node = story.findNode(SampleChapter.chapterId, id).orElseThrow();
            path.add(id);
            if (node.readingCheck()) return path;
            if (!node.storyChoices().isEmpty())
                id = node.storyChoices().get(Math.min(pick, node.storyChoices().size() - 1)).nextNodeId();
            else if (!node.nextNodeId().isEmpty()) id = node.nextNodeId();
            else fail("The story ended before the reading check at " + id);
        }
        return fail("The story never reached the reading check (possible loop)");
    }

    /** Answers the first `correctCount` questions correctly and the rest wrongly, then completes. */
    private AssessmentResult playQuiz(UUID studentId, int correctCount) {
        AssessmentService assessments = backend.assessmentService();
        UUID session = assessments.start(studentId, SampleChapter.chapterId);
        List<Question> questions = SampleChapter.chapter().questions();
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            int right = question.correctChoiceIndex();
            int pick = i < correctCount ? right : (right + 1) % question.choices().size();
            assessments.submit(session, question.id(), pick);
        }
        return assessments.complete(session);
    }

    // ---------- Chapter 0 data ----------

    @Test void everyDestinationExists() {
        Map<String, StoryNode> nodes = SampleChapter.nodes();
        for (StoryNode node : nodes.values()) {
            if (!node.nextNodeId().isEmpty()) assertTrue(nodes.containsKey(node.nextNodeId()), node.storyNodeId());
            for (StoryChoice choice : node.storyChoices()) assertTrue(nodes.containsKey(choice.nextNodeId()), choice.choiceId());
        }
    }

    @Test void firstNodeExists() {
        assertTrue(SampleChapter.nodes().containsKey(SampleChapter.FIRST_NODE_ID));
    }

    @Test void nodeMapKeysMatchNodeIds() {
        SampleChapter.nodes().forEach((key, node) -> assertEquals(key, node.storyNodeId()));
    }

    @Test void exactlyOneReadingCheckNode() {
        long count = SampleChapter.nodes().values().stream().filter(StoryNode::readingCheck).count();
        assertEquals(1L, count);
    }

    @Test void choiceNodesDoNotAlsoHaveANextNodeOrReadingCheck() {
        for (StoryNode node : SampleChapter.nodes().values()) {
            if (node.storyChoices().isEmpty()) continue;
            assertTrue(node.nextNodeId().isEmpty(), node.storyNodeId());
            assertFalse(node.readingCheck(), node.storyNodeId());
        }
    }

    @Test void everyChoiceRouteEndsAtTheReadingCheck() {
        for (int pick = 0; pick < 3; pick++) assertEquals("endingScene", walk(pick).getLast());
    }

    @Test void lunchChoicesLeadToDifferentNodes() {
        Set<String> targets = new HashSet<>();
        SampleChapter.lunchScene.storyChoices().forEach(choice -> targets.add(choice.nextNodeId()));
        assertEquals(3, targets.size());
    }

    @Test void chapterZeroHasFiveValidEasyQuestions() {
        Chapter chapter = SampleChapter.chapter();
        assertEquals(Difficulty.EASY, chapter.difficulty());
        assertFalse(chapter.passage().isBlank());
        assertEquals(List.of(SampleChapter.q1Id, SampleChapter.q2Id, SampleChapter.q3Id, SampleChapter.q4Id, SampleChapter.q5Id),
                chapter.questions().stream().map(Question::id).toList());
        for (Question question : chapter.questions()) {
            assertEquals(3, question.choices().size(), question.prompt());
            assertTrue(question.correctChoiceIndex() >= 0 && question.correctChoiceIndex() < question.choices().size());
        }
    }

    @Test void idsAreStableWithinOneRun() {
        assertEquals(SampleChapter.chapterId, SampleChapter.chapter().id());
        assertEquals(SampleChapter.chapter().questions().stream().map(Question::id).toList(),
                SampleChapter.chapter().questions().stream().map(Question::id).toList());
    }

    // ---------- InMemoryStoryRepository ----------

    @Test void firstNodeIsIntro() {
        assertEquals("intro", story.firstNodeId(SampleChapter.chapterId).orElseThrow());
    }

    @Test void readingCheckNodeIsFound() {
        assertTrue(story.findNode(SampleChapter.chapterId, "endingScene").orElseThrow().readingCheck());
    }

    @Test void unknownChapterOrNodeIsEmpty() {
        UUID other = UUID.randomUUID();
        assertTrue(story.firstNodeId(other).isEmpty());
        assertTrue(story.findNode(other, "intro").isEmpty());
        assertTrue(story.findNode(SampleChapter.chapterId, "doesNotExist").isEmpty());
        assertTrue(story.firstNodeId(null).isEmpty());
        assertTrue(story.findNode(null, "intro").isEmpty());
        assertTrue(story.findNode(SampleChapter.chapterId, null).isEmpty());
    }

    @Test void nodesAreScopedToTheirOwnChapter() {
        UUID other = UUID.randomUUID();
        StoryNode only = new StoryNode("only", "", "The end.", false, List.of(), "", true);
        story.register(other, "only", Map.of("only", only));
        assertTrue(story.findNode(other, "intro").isEmpty());
        assertTrue(story.findNode(SampleChapter.chapterId, "only").isEmpty());
    }

    @Test void registerRejectsAMissingDestination() {
        StoryNode broken = new StoryNode("a", "", "Text.", false, List.of(), "missing", false);
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryStoryRepository().register(UUID.randomUUID(), "a", Map.of("a", broken)));
    }

    @Test void registerRejectsAMissingFirstNode() {
        StoryNode only = new StoryNode("only", "", "The end.", false, List.of(), "", true);
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryStoryRepository().register(UUID.randomUUID(), "nope", Map.of("only", only)));
    }

    @Test void registerRejectsADuplicateChapter() {
        StoryNode only = new StoryNode("only", "", "The end.", false, List.of(), "", true);
        assertThrows(IllegalStateException.class,
                () -> story.register(SampleChapter.chapterId, "only", Map.of("only", only)));
    }

    // ---------- Chapter 0 scoring (real AssessmentService) ----------

    @Test void allCorrectScoresOneAndMovesUp() {
        var result = playQuiz(student, 5);
        assertEquals(5, result.attempt().correctAnswers());
        assertEquals(1.0, result.attempt().score());
        assertEquals(Difficulty.EASY, result.assessedDifficulty());
        assertEquals(Difficulty.MEDIUM, result.nextDifficulty());
    }

    @Test void eightyPercentMovesUp() {
        var result = playQuiz(student, 4);
        assertEquals(0.8, result.attempt().score());
        assertEquals(Difficulty.MEDIUM, result.nextDifficulty());
    }

    @Test void sixtyPercentKeepsTheDifficulty() {
        var result = playQuiz(student, 3);
        assertEquals(0.6, result.attempt().score());
        assertEquals(Difficulty.EASY, result.nextDifficulty());
    }

    @Test void belowSixtyPercentCannotGoBelowEasy() {
        assertEquals(Difficulty.EASY, playQuiz(student, 2).nextDifficulty());
        assertEquals(Difficulty.EASY, playQuiz(addStudent("Other"), 0).nextDifficulty());
    }

    @Test void onlyTheFirstAnswerCounts() {
        AssessmentService assessments = backend.assessmentService();
        UUID session = assessments.start(student, SampleChapter.chapterId);
        List<Question> questions = SampleChapter.chapter().questions();
        Question first = questions.getFirst();
        int wrong = (first.correctChoiceIndex() + 1) % first.choices().size();
        assertFalse(assessments.submit(session, first.id(), wrong));
        assertFalse(assessments.submit(session, first.id(), first.correctChoiceIndex()));
        for (Question question : questions.subList(1, questions.size()))
            assessments.submit(session, question.id(), question.correctChoiceIndex());
        assertEquals(4, assessments.complete(session).attempt().correctAnswers());
    }

    @Test void incompleteReadingCheckCannotComplete() {
        AssessmentService assessments = backend.assessmentService();
        UUID session = assessments.start(student, SampleChapter.chapterId);
        List<Question> questions = SampleChapter.chapter().questions();
        for (Question question : questions.subList(0, 4))
            assessments.submit(session, question.id(), question.correctChoiceIndex());
        assertThrows(IllegalStateException.class, () -> assessments.complete(session));
        assertTrue(backend.progressService().history(student).isEmpty());
    }

    @Test void completingTwiceOrReplayingKeepsTheOriginalResult() {
        var original = playQuiz(student, 2);
        var replay = playQuiz(student, 5);
        assertEquals(original, replay);
        assertEquals(List.of(original), backend.progressService().history(student));
    }

    @Test void questionPromptsNeverContainTheAnswerKey() {
        UUID session = backend.assessmentService().start(student, SampleChapter.chapterId);
        var prompts = backend.assessmentService().questions(session);
        assertEquals(5, prompts.size());
        for (var component : AssessmentService.QuestionPrompt.class.getRecordComponents())
            assertFalse(component.getName().toLowerCase().contains("correct"), component.getName());
    }

    @Test void storyChoicesDoNotChangeTheScore() {
        UUID second = addStudent("Other");
        assertFalse(walk(0).equals(walk(2)));
        var first = playQuiz(student, 3);
        var other = playQuiz(second, 3);
        assertEquals(first.attempt().correctAnswers(), other.attempt().correctAnswers());
        assertEquals(first.attempt().score(), other.attempt().score());
        assertEquals(first.nextDifficulty(), other.nextDifficulty());
    }
}
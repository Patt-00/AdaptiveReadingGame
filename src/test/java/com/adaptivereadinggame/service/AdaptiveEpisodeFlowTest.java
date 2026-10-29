package com.adaptivereadinggame.service;

import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.content.StoryPackLoader;
import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.memory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveEpisodeFlowTest {
    private static final char[] PASSWORD = "draft-flow-test".toCharArray();
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private InMemoryAccountRepository accounts;
    private InMemoryGameRepository content;
    private InMemorySaveRepository saves;
    private InMemoryAssessmentDraftRepository drafts;
    private BackendContext backend;
    private AccountGameService game;
    private Account reader;
    private Chapter chapter;

    @BeforeEach void setUp() throws Exception {
        accounts = new InMemoryAccountRepository();
        content = new InMemoryGameRepository();
        saves = new InMemorySaveRepository();
        drafts = new InMemoryAssessmentDraftRepository(accounts, content, content, content);
        backend = newBackend();
        reader = backend.accountGameService().signup("draft-reader", "Draft Reader", PASSWORD);
        backend.accountGameService().login("draft-reader", PASSWORD);
        backend.storyPackImporter().orElseThrow().importFile(Path.of("assets/content/hailey-investigation-draft.json"));
        chapter = content.findByDifficulty(Difficulty.EASY).stream()
                .filter(Chapter::adaptive).findFirst().orElseThrow();
        game = backend.accountGameService();
    }

    @Test void firstAnswerChoosesOutcomeAndTheNextQuestionUsesTheRecommendedDifficulty() {
        game.startChapter(chapter.id(), Difficulty.EASY, false);
        reachCheckpoint("study-first");
        assertTrue(game.history().isEmpty(), "Story choices must not create a comprehension result.");
        UUID session = game.startAssessment(chapter.id());
        var easy = game.questions(session).getFirst();
        assertTrue(easy.prompt().contains("Empty Velvet Vials"));
        assertTrue(game.submit(session, easy.id(), 2));
        AssessmentResult first = game.complete(session);
        assertEquals(1, first.attempt().correctAnswers());
        assertEquals(Difficulty.MEDIUM, first.nextDifficulty());

        GameState afterStudy = game.continueAfterCheckpoint();
        assertEquals("study-first-correct", afterStudy.storyNodeId());
        assertEquals("study-first", afterStudy.routeId());
        assertEquals(Difficulty.MEDIUM, afterStudy.activeDifficulty());
        reachCheckpoint("hall-second");
        UUID secondSession = game.startAssessment(chapter.id());
        var medium = game.questions(secondSession).getFirst();
        assertTrue(medium.prompt().contains("Scratched Goblet Rim"));
        assertTrue(game.submit(secondSession, medium.id(), 1));
        AssessmentResult second = game.complete(secondSession);
        assertTrue(game.checkpointEndsSection());
        assertEquals(2, game.episodeResults().size());
        assertEquals(2, game.episodeResults().stream().mapToInt(r -> r.attempt().correctAnswers()).sum());

        GameState complete = game.continueAfterCheckpoint();
        assertEquals("hall-second-correct", complete.storyNodeId());
        assertEquals("draft-complete", game.advanceStory().storyNodeId());
        assertTrue(game.currentGameView().orElseThrow().storyNode().storyComplete());
        assertEquals(second, game.currentResult().orElseThrow());
    }

    @Test void wrongFirstAnswerUsesTheIncorrectStoryBranchAndNarrativeChoicesDoNotScore() {
        game.startChapter(chapter.id(), Difficulty.EASY, false);
        reachCheckpoint("hall-first");
        UUID session = game.startAssessment(chapter.id());
        var question = game.questions(session).getFirst();
        assertFalse(game.submit(session, question.id(), 0));
        assertFalse(game.submit(session, question.id(), 1), "A changed retry must still report the locked first answer.");
        assertEquals(0, game.complete(session).attempt().correctAnswers());
        assertEquals(0, game.episodeResults().getFirst().attempt().correctAnswers());
        GameState afterHall = game.continueAfterCheckpoint();
        assertEquals("hall-first-incorrect", afterHall.storyNodeId());
        assertEquals("hall-first", afterHall.routeId());
        assertTrue(game.history().getFirst().attempt().score() == 0.0);
    }

    @Test void saveRestoreKeepsRouteCheckpointDifficultyAndAuthoritativeFirstAnswer() {
        game.startChapter(chapter.id(), Difficulty.EASY, false);
        reachCheckpoint("hall-first");
        UUID session = game.startAssessment(chapter.id());
        var question = game.questions(session).getFirst();
        assertFalse(game.submit(session, question.id(), 0));
        SaveSnapshot saved = game.save(1, false);
        assertEquals("hall-first", saved.gameState().routeId());
        assertEquals("hall-first", saved.gameState().activeCheckpointId());
        assertEquals(Difficulty.EASY, saved.gameState().activeDifficulty());

        game.logout(false);
        backend = newBackend();
        game = backend.accountGameService();
        game.login("draft-reader", PASSWORD);
        GameState restored = game.load(1, false).orElseThrow();
        assertEquals("hall-first", restored.routeId());
        assertEquals("hall-first", restored.activeCheckpointId());
        assertEquals(Difficulty.EASY, restored.activeDifficulty());
        assertEquals(Map.of(question.id(), 0), restored.firstAnswers());
        assertFalse(game.submit(session, question.id(), 1));
        assertEquals(0, game.complete(session).attempt().correctAnswers());
        assertEquals("hall-first-incorrect", game.continueAfterCheckpoint().storyNodeId());
    }

    @Test void savedResultAndCompletedStoryRestoreAcrossBackendRecreation() {
        game.startChapter(chapter.id(), Difficulty.EASY, false);
        reachCheckpoint("study-first");
        UUID session = game.startAssessment(chapter.id());
        var prompt = game.questions(session).getFirst();
        int correct = chapter.questions("study-first", Difficulty.EASY).getFirst().correctChoiceIndex();
        assertTrue(game.submit(session, prompt.id(), correct));
        AssessmentResult first = game.complete(session);
        game.save(1, false);
        game.logout(false);

        backend = newBackend();
        game = backend.accountGameService();
        game.login("draft-reader", PASSWORD);
        game.continueGame(false).orElseThrow();
        assertEquals(first, game.currentResult().orElseThrow());
        assertNull(game.currentGame().orElseThrow().assessmentSessionId());
        game.continueAfterCheckpoint();
        answerCheckpoint("hall-second", true);
        reachBoundary();
        game.save(1, true);
        game.logout(false);

        backend = newBackend();
        game = backend.accountGameService();
        game.login("draft-reader", PASSWORD);
        game.continueGame(false).orElseThrow();
        assertTrue(game.currentGameView().orElseThrow().storyNode().storyComplete());
        assertEquals(2, game.episodeResults().size());
        assertTrue(game.currentResult().isPresent());
    }

    @ParameterizedTest
    @MethodSource("routeOutcomes")
    void fullDraftRoutesReachBoundaryAndLockBothFirstAnswers(String route, Difficulty initial,
            boolean firstCorrect, boolean secondCorrect) {
        game.startChapter(chapter.id(), initial, false);
        String second = route.equals("study-first") ? "hall-second" : "study-second";
        answerCheckpoint(route, firstCorrect);
        answerCheckpoint(second, secondCorrect);
        reachBoundary();
        assertEquals(2, game.episodeResults().size());
        assertEquals((firstCorrect ? 1 : 0) + (secondCorrect ? 1 : 0), game.episodeResults().stream()
                .mapToInt(result -> result.attempt().correctAnswers()).sum());
        assertTrue(game.currentGameView().orElseThrow().storyNode().dialogueText().contains("CURRENT STORY DRAFT ENDS HERE"));
        game.save(3, false);
        game.logout(false);
        game.login("draft-reader", PASSWORD);
        assertTrue(game.continueGame(false).isPresent());
        assertTrue(game.currentGameView().orElseThrow().storyNode().storyComplete());
        assertTrue(game.currentResult().isPresent());
    }

    @Test void replayingTheOtherRouteDoesNotCombineBothRoutesInTheSectionScore() {
        game.startChapter(chapter.id(), Difficulty.EASY, false);
        answerCheckpoint("study-first", true);
        answerCheckpoint("hall-second", true);
        reachBoundary();
        game.startChapter(chapter.id(), Difficulty.EASY, true);
        answerCheckpoint("hall-first", false);
        answerCheckpoint("study-second", false);
        reachBoundary();
        assertEquals(4, game.history().size());
        assertEquals(2, game.episodeResults().size());
        assertTrue(game.episodeResults().stream().allMatch(result -> result.attempt().correctAnswers() == 0));
    }

    private void answerCheckpoint(String checkpointId, boolean correct) {
        reachCheckpoint(checkpointId);
        Difficulty difficulty = game.currentGame().orElseThrow().activeDifficulty();
        Question keyed = chapter.questions(checkpointId, difficulty).getFirst();
        UUID session = game.startAssessment(chapter.id());
        var prompt = game.questions(session).getFirst();
        int selected = correct ? keyed.correctChoiceIndex() : (keyed.correctChoiceIndex() + 1) % keyed.choices().size();
        assertEquals(correct, game.submit(session, prompt.id(), selected));
        assertEquals(correct, game.submit(session, prompt.id(), (selected + 1) % keyed.choices().size()));
        assertTrue(game.feedback(session, prompt.id()).explanation().contains("Provisional answer key"));
        AssessmentResult result = game.complete(session);
        assertEquals(correct ? 1 : 0, result.attempt().correctAnswers());
        assertEquals(checkpointId + (correct ? "-correct" : "-incorrect"), game.continueAfterCheckpoint().storyNodeId());
    }

    private void reachBoundary() {
        for (int guard = 0; guard < 1000; guard++) {
            StoryNode node = game.currentGameView().orElseThrow().storyNode();
            if (node.storyComplete()) return;
            assertFalse(node.readingCheck());
            assertTrue(node.storyChoices().isEmpty());
            game.advanceStory();
        }
        fail("The available story did not end at its draft boundary.");
    }

    private static Stream<Arguments> routeOutcomes() {
        var cases = new java.util.ArrayList<Arguments>();
        for (String route : List.of("study-first", "hall-first"))
            for (Difficulty level : Difficulty.values())
                for (boolean first : List.of(false, true))
                    for (boolean second : List.of(false, true)) cases.add(Arguments.of(route, level, first, second));
        return cases.stream();
    }

    private void reachCheckpoint(String checkpointId) {
        for (int guard = 0; guard < 1000; guard++) {
            var view = game.currentGameView().orElseThrow();
            StoryNode node = view.storyNode();
            if (node.readingCheck()) {
                assertEquals(checkpointId, view.state().activeCheckpointId().isEmpty()
                        ? chapter.checkpointAtNode(node.storyNodeId()).orElseThrow().id()
                        : view.state().activeCheckpointId());
                return;
            }
            if (!node.storyChoices().isEmpty()) {
                String choiceId = node.storyChoices().stream()
                        .filter(choice -> choice.routeId().equals(checkpointId.equals("study-first") || checkpointId.equals("hall-second")
                                ? "study-first" : "hall-first"))
                        .map(StoryChoice::choiceId).findFirst()
                        .orElse(node.storyChoices().getFirst().choiceId());
                game.chooseStory(choiceId);
            } else {
                game.advanceStory();
            }
        }
        fail("The story did not reach checkpoint " + checkpointId);
    }

    private BackendContext newBackend() {
        PasswordHasher fastHasher = new PasswordHasher() {
            @Override public String encode(char[] value) { return "test-only:" + Arrays.hashCode(value); }
            @Override public boolean matches(char[] value, String verifier) { return encode(value).equals(verifier); }
        };
        return BackendContext.create(accounts, accounts, accounts, accounts, content, content, saves, saves,
                content, drafts, assessment -> new RepositoryStoryService(accounts, content, content, drafts, assessment),
                fastHasher, new BaselineDifficultyPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}

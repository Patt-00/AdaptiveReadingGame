package com.adaptivereadinggame.service;

import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.model.Chapter;
import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.Question;
import com.adaptivereadinggame.model.StoryChoice;
import com.adaptivereadinggame.model.StoryNode;
import com.adaptivereadinggame.repository.memory.InMemoryAccountRepository;
import com.adaptivereadinggame.repository.memory.InMemoryAssessmentDraftRepository;
import com.adaptivereadinggame.repository.memory.InMemoryGameRepository;
import com.adaptivereadinggame.repository.memory.InMemorySaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryStoryServiceTest {
    private InMemoryAccountRepository accounts;
    private InMemoryGameRepository game;
    private InMemoryAssessmentDraftRepository drafts;
    private InMemorySaveRepository saves;
    private BackendContext backend;
    private UUID chapterId;
    private UUID firstQuestionId;
    private UUID secondQuestionId;

    @BeforeEach void setUp() {
        accounts = new InMemoryAccountRepository();
        game = new InMemoryGameRepository();
        drafts = new InMemoryAssessmentDraftRepository(accounts, game, game, game);
        saves = new InMemorySaveRepository();
        backend = newBackend();
        backend.accountGameService().signup("reader", "Reader", "test-passphrase".toCharArray());
        backend.accountGameService().login("reader", "test-passphrase".toCharArray());
        chapterId = UUID.randomUUID();
        firstQuestionId = UUID.randomUUID();
        secondQuestionId = UUID.randomUUID();
        game.saveChapter(new Chapter(chapterId, "Fixture chapter", "Test passage.", Difficulty.MEDIUM,
                List.of(new Question(firstQuestionId, "Question one?", List.of("A", "B"), 1, "Fixture feedback."),
                        new Question(secondQuestionId, "Question two?", List.of("A", "B"), 0))));
        game.saveStoryNodes(chapterId, List.of(
                new StoryNode("opening", "", "Fixture opening.", false, List.of(), "choice", false),
                new StoryNode("choice", "Guide", "Choose a fixture path.", false,
                        List.of(new StoryChoice("help", "Help", "help-route"),
                                new StoryChoice("wait", "Wait", "wait-route")), "", false),
                new StoryNode("help-route", "Guide", "Fixture help route.", false, List.of(), "check", false),
                new StoryNode("wait-route", "Guide", "Fixture wait route.", false, List.of(), "check", false),
                new StoryNode("check", "", "Fixture reading check.", false, List.of(), "", true)), "opening");
    }

    @Test void storyChoiceRoutesNarrativeAndDoesNotCreateAReadingScore() {
        var accountGame = backend.accountGameService();
        assertEquals("opening", accountGame.startChapter(chapterId, false).storyNodeId());
        assertEquals("choice", accountGame.advanceStory().storyNodeId());
        assertThrows(IllegalStateException.class, accountGame::advanceStory);
        assertEquals("help-route", accountGame.chooseStory("help").storyNodeId());
        assertEquals("check", accountGame.advanceStory().storyNodeId());
        assertTrue(accountGame.history().isEmpty());
    }

    @Test void questionSequenceLocksFirstAnswersAndScoresOnlyComprehension() {
        var accountGame = backend.accountGameService();
        reachReadingCheck(accountGame, "wait");
        UUID sessionId = accountGame.startAssessment(chapterId);
        assertThrows(IllegalStateException.class, () -> accountGame.feedback(sessionId, firstQuestionId));
        assertThrows(IllegalStateException.class,
                () -> accountGame.submit(sessionId, secondQuestionId, 0));
        assertFalse(accountGame.submit(sessionId, firstQuestionId, 0));
        assertFalse(accountGame.submit(sessionId, firstQuestionId, 1), "A retry reports the first answer, not the changed answer.");
        var feedback = accountGame.feedback(sessionId, firstQuestionId);
        assertFalse(feedback.correct());
        assertEquals("B", feedback.correctAnswer());
        assertEquals("Fixture feedback.", feedback.explanation());
        assertTrue(accountGame.submit(sessionId, secondQuestionId, 0));
        var result = accountGame.complete(sessionId);
        assertEquals(1, result.attempt().correctAnswers());
        assertEquals(2, result.attempt().totalQuestions());
        assertEquals(Difficulty.EASY, result.nextDifficulty());
    }

    @Test void unfinishedAssessmentRestoresOriginalSessionAndStoredAnswers() {
        var accountGame = backend.accountGameService();
        reachReadingCheck(accountGame, "help");
        UUID sessionId = accountGame.startAssessment(chapterId);
        assertFalse(accountGame.submit(sessionId, firstQuestionId, 0));
        accountGame.save(1, false);
        accountGame.logout(false);

        backend = newBackend();
        accountGame = backend.accountGameService();
        accountGame.login("reader", "test-passphrase".toCharArray());
        var restored = accountGame.load(1, false).orElseThrow();
        assertEquals(sessionId, restored.assessmentSessionId());
        assertEquals(1, restored.questionIndex());
        assertEquals(Map.of(firstQuestionId, 0), restored.firstAnswers());
        assertTrue(accountGame.submit(sessionId, secondQuestionId, 0));
        assertEquals(1, accountGame.complete(sessionId).attempt().correctAnswers());
    }

    @Test void restoreRejectsUnknownSessionWithoutReplacingTheCurrentStory() {
        var accountGame = backend.accountGameService();
        var active = accountGame.startChapter(chapterId, false);
        var service = new RepositoryStoryService(accounts, game, game, drafts, backend.assessmentService());
        var serviceState = service.startChapter(active.studentId(), active.chapterId());
        var invalid = new com.adaptivereadinggame.model.GameState(serviceState.studentId(), serviceState.chapterId(),
                "check", UUID.randomUUID(), 0, Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.restoreState(invalid));
        assertEquals(serviceState, service.currentState(active.studentId()).orElseThrow());
    }

    private void reachReadingCheck(AccountGameService accountGame, String choice) {
        accountGame.startChapter(chapterId, false);
        accountGame.advanceStory();
        accountGame.chooseStory(choice);
        accountGame.advanceStory();
    }

    private BackendContext newBackend() {
        return BackendContext.create(accounts, accounts, accounts, accounts, game, game, saves, saves,
                game, drafts, assessment -> new RepositoryStoryService(accounts, game, game, drafts, assessment),
                new Pbkdf2PasswordHasher(), new BaselineDifficultyPolicy(), Clock.systemUTC());
    }
}

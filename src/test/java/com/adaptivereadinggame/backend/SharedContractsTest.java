package com.adaptivereadinggame.backend;

import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.GameState;
import com.adaptivereadinggame.model.SaveGame;
import com.adaptivereadinggame.model.SaveSnapshot;
import com.adaptivereadinggame.model.StoryChoice;
import com.adaptivereadinggame.model.StoryNode;
import com.adaptivereadinggame.service.GameStateCodec;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SharedContractsTest {
    private final UUID studentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID chapterId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID sessionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID questionId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final GameStateCodec codec = new GameStateCodec();

    private GameState assessmentState() {
        return new GameState(studentId, chapterId, "reading-check", sessionId, 1, Map.of(questionId, 1));
    }

    @Test void narrativeChoiceHasItsOwnStableNamesAndDestination() {
        var choice = new StoryChoice("help-mia", "Help Mia", "garden");
        assertEquals("help-mia", choice.choiceId());
        assertEquals("garden", choice.nextNodeId());
        assertThrows(IllegalArgumentException.class, () -> new StoryChoice(" ", "Help", "garden"));
        assertThrows(IllegalArgumentException.class, () -> new StoryChoice("help", " ", "garden"));
        assertThrows(IllegalArgumentException.class, () -> new StoryChoice("help", "Help", ""));
    }

    @Test void storyNodesCopyTheirChoiceLists() {
        var choices = new ArrayList<>(List.of(new StoryChoice("help", "Help Mia", "garden")));
        var node = new StoryNode("choice", "Mia", "Can you help?", false, choices, "", false);
        choices.clear();
        assertEquals(1, node.storyChoices().size());
        assertThrows(UnsupportedOperationException.class, () -> node.storyChoices().clear());
    }

    @Test void everyValidNodeKindHasOneClearNextAction() {
        assertDoesNotThrow(() -> new StoryNode("dialogue", "", "A quiet garden.", false, List.of(), "choice", false));
        assertDoesNotThrow(() -> new StoryNode("thought", "Mia", "I should read.", true, List.of(), "", true));
        assertDoesNotThrow(() -> new StoryNode("ending", "", "The end.", false, List.of(), "", false));
    }

    @Test void conflictingTransitionsAndDuplicateChoicesAreRejected() {
        var choice = new StoryChoice("help", "Help Mia", "garden");
        assertThrows(IllegalArgumentException.class,
                () -> new StoryNode("choice", "Mia", "Choose.", false, List.of(choice), "garden", false));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryNode("choice", "Mia", "Choose.", false, List.of(choice), "", true));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryNode("check", "Mia", "Read.", false, List.of(), "garden", true));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryNode("choice", "Mia", "Choose.", false, List.of(choice, choice), "", false));
    }

    @Test void gameStateCopiesSubmittedAnswers() {
        Map<UUID, Integer> answers = new HashMap<>(Map.of(questionId, 1));
        var state = new GameState(studentId, chapterId, "check", sessionId, 1, answers);
        answers.put(questionId, 0);
        assertEquals(1, state.firstAnswers().get(questionId));
        assertThrows(UnsupportedOperationException.class, () -> state.firstAnswers().put(questionId, 0));
    }

    @Test void unstartedAssessmentCannotPretendToHaveQuestionProgress() {
        assertDoesNotThrow(() -> new GameState(studentId, chapterId, "opening", null, 0, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new GameState(studentId, chapterId, "opening", null, 1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new GameState(studentId, chapterId, "opening", null, 0, Map.of(questionId, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new GameState(studentId, chapterId, "check", sessionId, -1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new GameState(studentId, chapterId, "check", sessionId, 0, Map.of(questionId, -1)));
    }

    @Test void fullSnapshotRequiresMatchingBasicCheckpoint() {
        GameState state = assessmentState();
        var matching = new SaveGame(studentId, 1, chapterId, state.storyNodeId(), Difficulty.EASY, Instant.EPOCH);
        assertEquals(state, new SaveSnapshot(matching, state).gameState());
        var differentStudent = new SaveGame(UUID.randomUUID(), 1, chapterId, state.storyNodeId(), Difficulty.EASY, Instant.EPOCH);
        var differentChapter = new SaveGame(studentId, 1, UUID.randomUUID(), state.storyNodeId(), Difficulty.EASY, Instant.EPOCH);
        var differentNode = new SaveGame(studentId, 1, chapterId, "other", Difficulty.EASY, Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> new SaveSnapshot(differentStudent, state));
        assertThrows(IllegalArgumentException.class, () -> new SaveSnapshot(differentChapter, state));
        assertThrows(IllegalArgumentException.class, () -> new SaveSnapshot(differentNode, state));
    }

    @Test void versionOneJsonRoundTripsAllFields() {
        GameState state = assessmentState();
        String json = codec.encode(state);
        assertTrue(json.startsWith("{\"formatVersion\":1,\"gameState\":{"));
        assertTrue(json.contains("\"" + questionId + "\":1"));
        assertEquals(state, codec.decode(json));
    }

    @Test void emptyStoryStateRoundTripsNullSessionAndEmptyAnswers() {
        var state = new GameState(studentId, chapterId, "opening", null, 0, Map.of());
        String json = codec.encode(state);
        assertTrue(json.contains("\"assessmentSessionId\":null"));
        assertTrue(json.contains("\"firstAnswers\":{}"));
        assertEquals(state, codec.decode(json));
    }

    @Test void jsonEscapesStoryNodeTextCorrectly() {
        var state = new GameState(studentId, chapterId, "node-\"quoted\"-\\path", null, 0, Map.of());
        assertEquals(state, codec.decode(codec.encode(state)));
    }

    @Test void wrongVersionDuplicateOrUnknownFieldsAreRejected() {
        String valid = codec.encode(assessmentState());
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace("\"formatVersion\":1", "\"formatVersion\":2")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace("\"questionIndex\":1", "\"questionIndex\":1,\"extra\":true")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace("\"formatVersion\":1", "\"formatVersion\":1,\"extra\":true")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace("\"questionIndex\":1,", "")));
    }

    @Test void numericStringsFractionsOverflowAndNullsAreRejected() {
        String valid = codec.encode(assessmentState());
        for (String invalidNumber : List.of("\"1\"", "1.0", "2147483648", "null", "true")) {
            assertThrows(IllegalArgumentException.class,
                    () -> codec.decode(valid.replace("\"questionIndex\":1", "\"questionIndex\":" + invalidNumber)));
        }
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace("\"firstAnswers\":{\"" + questionId + "\":1}", "\"firstAnswers\":null")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace("\"firstAnswers\":{\"" + questionId + "\":1}", "\"firstAnswers\":[]")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace("\"" + questionId + "\":1", "\"" + questionId + "\":\"1\"")));
    }

    @Test void malformedUuidAndShortenedUuidAreRejected() {
        String valid = codec.encode(assessmentState());
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace(studentId.toString(), "not-a-uuid")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace(studentId.toString(), "1-1-1-1-1")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace(questionId.toString(), "bad-question")));
    }

    @Test void duplicateCanonicalQuestionIdsAndNegativeAnswersAreRejected() {
        String valid = codec.encode(assessmentState());
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace(
                "\"" + questionId + "\":1", "\"" + questionId + "\":1,\"" + questionId + "\":0")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace(
                "\"" + questionId + "\":1", "\"" + questionId + "\":-1")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace(
                "\"assessmentSessionId\":\"" + sessionId + "\"", "\"assessmentSessionId\":null")));
        String lowercaseId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String uppercaseId = "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA";
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid.replace(
                "\"" + questionId + "\":1", "\"" + lowercaseId + "\":1,\"" + uppercaseId + "\":0")));
    }

    @Test void invalidShapeTrailingTokensAndOversizedJsonAreRejected() {
        String valid = codec.encode(assessmentState());
        for (String invalid : List.of("null", "[]", "{}", "false", "not JSON", valid + " {}"))
            assertThrows(IllegalArgumentException.class, () -> codec.decode(invalid));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(" ".repeat(GameStateCodec.MAX_DOCUMENT_LENGTH + 1)));
        var huge = new GameState(studentId, chapterId, "x".repeat(GameStateCodec.MAX_DOCUMENT_LENGTH), null, 0, Map.of());
        assertThrows(IllegalArgumentException.class, () -> codec.encode(huge));
    }

    @Test void codecRejectsMoreAnswersThanAnyPreparedChapterCanContain() {
        Map<UUID, Integer> answers = new HashMap<>();
        for (int index = 0; index < 101; index++) answers.put(UUID.randomUUID(), 0);
        var state = new GameState(studentId, chapterId, "check", sessionId, 0, answers);
        assertThrows(IllegalArgumentException.class, () -> codec.encode(state));
    }
}

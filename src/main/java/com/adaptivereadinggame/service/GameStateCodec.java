package com.adaptivereadinggame.service;

import com.adaptivereadinggame.model.GameState;
import com.adaptivereadinggame.model.Difficulty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Approved version-1 save JSON for the game_state_json column. This codec
 * validates the data shape, not account authorization or live chapter membership.
 * Restore through StoryService using the authoritative stored draft answers.
 */
public final class GameStateCodec {
    public static final int FORMAT_VERSION = 2;
    public static final int LEGACY_FORMAT_VERSION = 1;
    public static final int MAX_DOCUMENT_LENGTH = 65_536;
    private static final int MAX_ANSWERS = 100; // Existing Chapter limits question count to 100.
    private static final Set<String> ENVELOPE_FIELDS = Set.of("formatVersion", "gameState");
    private static final Set<String> LEGACY_STATE_FIELDS = Set.of("studentId", "chapterId", "storyNodeId",
            "assessmentSessionId", "questionIndex", "firstAnswers");
    private static final Set<String> STATE_FIELDS = Set.of("studentId", "chapterId", "storyNodeId",
            "assessmentSessionId", "questionIndex", "firstAnswers", "routeId", "activeCheckpointId",
            "activeDifficulty", "lastCompletedCheckpointId");
    private final JsonMapper mapper;

    public GameStateCodec() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(8).maxStringLength(MAX_DOCUMENT_LENGTH)
                        .maxNumberLength(16).build())
                .build();
        mapper = JsonMapper.builder(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    public String encode(GameState gameState) {
        Objects.requireNonNull(gameState, "gameState");
        requireAnswerLimit(gameState.firstAnswers().size());
        ObjectNode state = mapper.createObjectNode();
        state.put("studentId", gameState.studentId().toString());
        state.put("chapterId", gameState.chapterId().toString());
        state.put("storyNodeId", gameState.storyNodeId());
        if (gameState.assessmentSessionId() == null) state.putNull("assessmentSessionId");
        else state.put("assessmentSessionId", gameState.assessmentSessionId().toString());
        state.put("questionIndex", gameState.questionIndex());
        state.put("routeId", gameState.routeId());
        state.put("activeCheckpointId", gameState.activeCheckpointId());
        state.put("lastCompletedCheckpointId", gameState.lastCompletedCheckpointId());
        if (gameState.activeDifficulty() == null) state.putNull("activeDifficulty");
        else state.put("activeDifficulty", gameState.activeDifficulty().name());
        ObjectNode answers = mapper.createObjectNode();
        gameState.firstAnswers().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> answers.put(entry.getKey().toString(), entry.getValue()));
        state.set("firstAnswers", answers);
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("formatVersion", FORMAT_VERSION);
        envelope.set("gameState", state);
        try {
            String encoded = mapper.writeValueAsString(envelope);
            requireDocumentLength(encoded);
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode game state", exception);
        }
    }

    /** Strictly read version 1 and 2 saves; version 1 is upgraded in memory on the next save. */
    public GameState decode(String document) {
        requireDocumentLength(document);
        try {
            JsonNode envelope = mapper.readTree(document);
            requireFields(envelope, ENVELOPE_FIELDS, "save envelope");
            int version = requireInt(envelope.get("formatVersion"), "formatVersion");
            if (version != FORMAT_VERSION && version != LEGACY_FORMAT_VERSION)
                throw new IllegalArgumentException("Unsupported save format version");
            JsonNode state = envelope.get("gameState");
            requireFields(state, version == LEGACY_FORMAT_VERSION ? LEGACY_STATE_FIELDS : STATE_FIELDS, "gameState");
            UUID studentId = requireUuid(state.get("studentId"), "studentId");
            UUID chapterId = requireUuid(state.get("chapterId"), "chapterId");
            String storyNodeId = requireText(state.get("storyNodeId"), "storyNodeId");
            JsonNode sessionNode = state.get("assessmentSessionId");
            UUID sessionId = sessionNode.isNull() ? null : requireUuid(sessionNode, "assessmentSessionId");
            int questionIndex = requireInt(state.get("questionIndex"), "questionIndex");
            JsonNode answerNode = state.get("firstAnswers");
            if (!answerNode.isObject()) throw new IllegalArgumentException("firstAnswers must be an object");
            requireAnswerLimit(answerNode.size());
            Map<UUID, Integer> firstAnswers = new HashMap<>();
            var fields = answerNode.fields();
            while (fields.hasNext()) {
                var answer = fields.next();
                UUID questionId = parseUuid(answer.getKey(), "firstAnswers question ID");
                int selectedIndex = requireInt(answer.getValue(), "firstAnswers option index");
                if (firstAnswers.putIfAbsent(questionId, selectedIndex) != null)
                    throw new IllegalArgumentException("Question IDs must not occur twice");
            }
            if (version == LEGACY_FORMAT_VERSION)
                return new GameState(studentId, chapterId, storyNodeId, sessionId, questionIndex, firstAnswers);
            String routeId = requireText(state.get("routeId"), "routeId");
            String checkpointId = requireText(state.get("activeCheckpointId"), "activeCheckpointId");
            String lastCompletedCheckpointId = requireText(state.get("lastCompletedCheckpointId"), "lastCompletedCheckpointId");
            JsonNode difficultyNode = state.get("activeDifficulty");
            Difficulty difficulty = difficultyNode == null || difficultyNode.isNull()
                    ? null : parseDifficulty(requireText(difficultyNode, "activeDifficulty"));
            return new GameState(studentId, chapterId, storyNodeId, sessionId, questionIndex,
                    firstAnswers, routeId, checkpointId, difficulty, lastCompletedCheckpointId);
        } catch (JsonProcessingException exception) {
            // Never include source JSON: it can contain learner progress or malicious text.
            throw new IllegalArgumentException("Save JSON is malformed or exceeds parsing limits");
        }
    }

    private static Difficulty parseDifficulty(String value) {
        try { return Difficulty.valueOf(value); }
        catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("activeDifficulty must be EASY, MEDIUM, or HARD.");
        }
    }

    private static void requireDocumentLength(String document) {
        if (document == null || document.isBlank() || document.length() > MAX_DOCUMENT_LENGTH)
            throw new IllegalArgumentException("Save JSON must be present and at most 65536 characters");
    }

    private static void requireAnswerLimit(int size) {
        if (size > MAX_ANSWERS)
            throw new IllegalArgumentException("A saved assessment cannot contain more than 100 answers");
    }

    private static void requireFields(JsonNode node, Set<String> expected, String name) {
        if (node == null || !node.isObject())
            throw new IllegalArgumentException(name + " must be an object");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected))
            throw new IllegalArgumentException(name + " must contain exactly the approved fields");
    }

    private static int requireInt(JsonNode node, String name) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt())
            throw new IllegalArgumentException(name + " must be a Java integer");
        return node.intValue();
    }

    private static String requireText(JsonNode node, String name) {
        if (node == null || !node.isTextual())
            throw new IllegalArgumentException(name + " must be text");
        return node.textValue();
    }

    private static UUID requireUuid(JsonNode node, String name) {
        return parseUuid(requireText(node, name), name);
    }

    private static UUID parseUuid(String value, String name) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value))
                throw new IllegalArgumentException("Not canonical UUID text");
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a full UUID string");
        }
    }
}

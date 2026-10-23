package com.adaptivereadinggame.content;

import com.adaptivereadinggame.model.Chapter;
import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.Question;
import com.adaptivereadinggame.model.StoryChoice;
import com.adaptivereadinggame.model.StoryGraphValidator;
import com.adaptivereadinggame.model.StoryNode;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reads the versioned local JSON pack used to add approved story and question content. */
public final class StoryPackLoader {
    public static final int FORMAT_VERSION = 1;
    public static final long MAX_FILE_BYTES = 5_242_880;
    private static final Set<String> ROOT_FIELDS = Set.of("formatVersion", "chapters");
    private static final Set<String> CHAPTER_FIELDS = Set.of("id", "title", "difficulty", "passage",
            "startNodeId", "nodes", "questions");
    private static final Set<String> NODE_FIELDS = Set.of("id", "speakerName", "dialogueText", "thought",
            "readingCheck", "nextNodeId", "choices");
    private static final Set<String> CHOICE_FIELDS = Set.of("id", "text", "nextNodeId");
    private static final Set<String> QUESTION_FIELDS = Set.of("id", "prompt", "choices", "correctChoiceIndex");
    private static final Set<String> NODE_OPTIONAL_FIELDS = Set.of("speakerAssetKey");
    private static final Set<String> QUESTION_OPTIONAL_FIELDS = Set.of("explanation");
    private static final JsonMapper MAPPER = createMapper();

    public StoryPack read(Path path) throws IOException {
        if (path == null) throw new IllegalArgumentException("A content-pack path is required.");
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_BYTES)
            throw new IllegalArgumentException("A story pack must be between 1 byte and 5 MiB.");
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public StoryPack parse(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_FILE_BYTES)
            throw new IllegalArgumentException("A story pack must be present and at most 5 MiB.");
        try {
            JsonNode root = MAPPER.readTree(json);
            requireFields(root, ROOT_FIELDS, Set.of(), "pack");
            if (integer(root.get("formatVersion"), "formatVersion") != FORMAT_VERSION)
                throw new IllegalArgumentException("This story-pack version is not supported.");
            List<JsonNode> sourceChapters = array(root.get("chapters"), "chapters", 200);
            if (sourceChapters.isEmpty()) throw new IllegalArgumentException("A story pack must contain at least one chapter.");
            Set<String> chapterIds = new HashSet<>();
            List<StoryPack.ChapterPack> chapters = new ArrayList<>(sourceChapters.size());
            for (JsonNode source : sourceChapters) {
                StoryPack.ChapterPack chapter = readChapter(source);
                if (!chapterIds.add(chapter.sourceId()))
                    throw new IllegalArgumentException("Chapter IDs must be unique within a story pack.");
                chapters.add(chapter);
            }
            return new StoryPack(chapters);
        } catch (JsonProcessingException malformed) {
            throw new IllegalArgumentException("Story-pack JSON is malformed or exceeds parsing limits.");
        }
    }

    private StoryPack.ChapterPack readChapter(JsonNode source) {
        requireFields(source, CHAPTER_FIELDS, Set.of(), "chapter");
        String sourceId = identifier(source.get("id"), "chapter ID");
        String title = boundedText(source.get("title"), "chapter title", 200, false);
        String passage = boundedText(source.get("passage"), "chapter passage", 1_000_000, false);
        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(boundedText(source.get("difficulty"), "difficulty", 10, false));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Difficulty must be EASY, MEDIUM, or HARD.");
        }
        String startNodeId = identifier(source.get("startNodeId"), "start node ID");
        List<JsonNode> sourceNodes = array(source.get("nodes"), "nodes", 1000);
        List<StoryNode> nodes = sourceNodes.stream().map(this::readNode).toList();
        StoryGraphValidator.validate(nodes, startNodeId);

        List<JsonNode> sourceQuestions = array(source.get("questions"), "questions", 100);
        List<Question> questions = new ArrayList<>(sourceQuestions.size());
        Set<String> questionIds = new HashSet<>();
        for (JsonNode question : sourceQuestions) {
            String sourceQuestionId = identifier(question.get("id"), "question ID");
            if (!questionIds.add(sourceQuestionId))
                throw new IllegalArgumentException("Question IDs must be unique within a chapter.");
            questions.add(readQuestion(question, stableId("question", sourceId + "/" + sourceQuestionId)));
        }
        Chapter chapter = new Chapter(stableId("chapter", sourceId), title, passage, difficulty, questions);
        return new StoryPack.ChapterPack(sourceId, chapter, startNodeId, nodes);
    }

    private StoryNode readNode(JsonNode source) {
        requireFields(source, NODE_FIELDS, NODE_OPTIONAL_FIELDS, "story node");
        String id = identifier(source.get("id"), "story node ID");
        String speaker = boundedText(source.get("speakerName"), "speaker name", 100, true);
        String text = boundedText(source.get("dialogueText"), "story text", 1_000_000, false);
        boolean thought = bool(source.get("thought"), "thought");
        boolean readingCheck = bool(source.get("readingCheck"), "readingCheck");
        String nextNodeId = optionalDestination(source.get("nextNodeId"));
        String speakerAssetKey = source.has("speakerAssetKey")
                ? boundedText(source.get("speakerAssetKey"), "speakerAssetKey", 80, true) : "";
        List<JsonNode> sourceChoices = array(source.get("choices"), "story choices", 50);
        List<StoryChoice> choices = new ArrayList<>(sourceChoices.size());
        for (JsonNode choice : sourceChoices) {
            requireFields(choice, CHOICE_FIELDS, Set.of(), "story choice");
            choices.add(new StoryChoice(identifier(choice.get("id"), "story choice ID"),
                    boundedText(choice.get("text"), "story choice text", 500, false),
                    identifier(choice.get("nextNodeId"), "story choice destination")));
        }
        return new StoryNode(id, speaker, text, thought, choices, nextNodeId, readingCheck, speakerAssetKey);
    }

    private Question readQuestion(JsonNode source, UUID id) {
        requireFields(source, QUESTION_FIELDS, QUESTION_OPTIONAL_FIELDS, "question");
        String prompt = boundedText(source.get("prompt"), "question prompt", 1_000_000, false);
        List<JsonNode> values = array(source.get("choices"), "answer choices", 100);
        List<String> choices = values.stream()
                .map(value -> boundedText(value, "answer choice", 1000, false)).toList();
        int correctChoice = integer(source.get("correctChoiceIndex"), "correctChoiceIndex");
        String explanation = source.has("explanation")
                ? boundedText(source.get("explanation"), "answer explanation", 1_000_000, true) : "";
        return new Question(id, prompt, choices, correctChoice, explanation);
    }

    private static void requireFields(JsonNode node, Set<String> required, Set<String> optional, String label) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Each " + label + " must be an object.");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.containsAll(required) || !required.containsAll(actual.stream()
                .filter(field -> !optional.contains(field)).toList()))
            throw new IllegalArgumentException("A " + label + " contains missing or unknown fields.");
    }

    private static List<JsonNode> array(JsonNode node, String label, int maxItems) {
        if (node == null || !node.isArray() || node.size() > maxItems)
            throw new IllegalArgumentException(label + " must be an array with at most " + maxItems + " items.");
        List<JsonNode> items = new ArrayList<>(node.size());
        node.forEach(items::add);
        return items;
    }

    private static String identifier(JsonNode node, String label) {
        String value = boundedText(node, label, 100, false);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))
            throw new IllegalArgumentException(label + " must use letters, numbers, dots, underscores, or hyphens.");
        return value;
    }

    private static String optionalDestination(JsonNode node) {
        String value = boundedText(node, "nextNodeId", 100, true);
        return value.isEmpty() ? "" : identifier(node, "nextNodeId");
    }

    private static String boundedText(JsonNode node, String label, int maxLength, boolean allowEmpty) {
        if (node == null || !node.isTextual()) throw new IllegalArgumentException(label + " must be text.");
        String value = node.textValue();
        if ((!allowEmpty && value.isBlank()) || value.length() > maxLength)
            throw new IllegalArgumentException(label + " is empty or too long.");
        return value;
    }

    private static int integer(JsonNode node, String label) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt())
            throw new IllegalArgumentException(label + " must be an integer.");
        return node.intValue();
    }

    private static boolean bool(JsonNode node, String label) {
        if (node == null || !node.isBoolean()) throw new IllegalArgumentException(label + " must be true or false.");
        return node.booleanValue();
    }

    private static UUID stableId(String type, String sourceId) {
        return UUID.nameUUIDFromBytes(("AdaptiveReadingGame:" + type + ":" + sourceId)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static JsonMapper createMapper() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(16).maxStringLength(1_000_000).maxNumberLength(12).build())
                .build();
        return JsonMapper.builder(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }
}

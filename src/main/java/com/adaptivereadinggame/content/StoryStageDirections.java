package com.adaptivereadinggame.content;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Optional presentation cues for explicitly authored supporting characters. */
public final class StoryStageDirections {
    public record SupportingCharacter(String name, String assetKey) {}
    private record Key(UUID chapterId, String nodeId) {}

    private static final long MAX_FILE_BYTES = 1_048_576;
    private static final int MAX_DIRECTIONS = 1000;
    private static final Set<String> ROOT_FIELDS = Set.of("formatVersion", "directions");
    private static final Set<String> DIRECTION_FIELDS = Set.of("chapter", "node", "name", "assetKey");
    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(8).maxStringLength(1000).maxNumberLength(10).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private final Map<Key, SupportingCharacter> directions;

    private StoryStageDirections(Map<Key, SupportingCharacter> directions) {
        this.directions = Map.copyOf(directions);
    }

    public static StoryStageDirections empty() { return new StoryStageDirections(Map.of()); }

    public static StoryStageDirections read(Path path) throws IOException {
        long size = Files.size(path);
        if (size < 1 || size > MAX_FILE_BYTES)
            throw new IllegalArgumentException("Stage directions must be between 1 byte and 1 MiB.");
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static StoryStageDirections parse(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_FILE_BYTES)
            throw new IllegalArgumentException("Stage directions are empty or too large.");
        try {
            JsonNode root = MAPPER.readTree(json);
            requireFields(root, ROOT_FIELDS);
            JsonNode version = root.get("formatVersion");
            JsonNode entries = root.get("directions");
            if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != 1 || !entries.isArray()
                    || entries.size() > MAX_DIRECTIONS)
                throw new IllegalArgumentException("Unsupported stage directions format.");
            Map<Key, SupportingCharacter> result = new HashMap<>();
            for (JsonNode entry : entries) {
                requireFields(entry, DIRECTION_FIELDS);
                String chapter = identifier(entry.get("chapter"), 100);
                String node = identifier(entry.get("node"), 100);
                String asset = identifier(entry.get("assetKey"), 80);
                JsonNode nameNode = entry.get("name");
                if (!nameNode.isTextual() || nameNode.textValue().isBlank() || nameNode.textValue().length() > 80
                        || nameNode.textValue().codePoints().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException("A supporting character needs a short name.");
                Key key = new Key(StoryPackLoader.stableId("chapter", chapter), node);
                if (result.putIfAbsent(key, new SupportingCharacter(nameNode.textValue(), asset)) != null)
                    throw new IllegalArgumentException("A story node can have only one supporting character cue.");
            }
            return new StoryStageDirections(result);
        } catch (JsonProcessingException malformed) {
            throw new IllegalArgumentException("Stage directions are not valid JSON.", malformed);
        }
    }

    public Optional<SupportingCharacter> forNode(UUID chapterId, String nodeId) {
        return Optional.ofNullable(directions.get(new Key(chapterId, nodeId)));
    }

    private static String identifier(JsonNode node, int limit) {
        if (node == null || !node.isTextual() || node.textValue().length() > limit
                || !node.textValue().matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
            throw new IllegalArgumentException("Stage direction identifiers must be simple keys.");
        return node.textValue();
    }

    private static void requireFields(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("A stage direction must be an object.");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(fields)) throw new IllegalArgumentException("Stage directions contain missing or unknown fields.");
    }
}

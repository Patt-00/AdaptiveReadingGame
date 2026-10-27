package com.adaptivereadinggame.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AppNavigatorStageDirectionsTest {
    @Test void absentOptionalFileUsesTheSingleSpeakerLayout(@TempDir Path folder) {
        var result = AppNavigator.loadStageDirections(null, folder.resolve("missing.json"));
        assertTrue(result.notice().isEmpty());
        assertTrue(result.directions().forNode(UUID.randomUUID(), "node").isEmpty());
    }

    @Test void explicitMissingOrInvalidFileShowsANotice(@TempDir Path folder) throws Exception {
        var missing = AppNavigator.loadStageDirections(folder.resolve("missing.json").toString(), folder.resolve("default.json"));
        assertFalse(missing.notice().isBlank());
        Path invalid = folder.resolve("invalid.json");
        Files.writeString(invalid, "not json");
        var malformed = AppNavigator.loadStageDirections(invalid.toString(), folder.resolve("default.json"));
        assertFalse(malformed.notice().isBlank());
        assertTrue(malformed.directions().forNode(UUID.randomUUID(), "node").isEmpty());
    }

    @Test void explicitFileOverridesDefaultAndLoadsAuthoredCue(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("custom.json");
        Files.writeString(file, """
                {"formatVersion":1,"directions":[
                  {"chapter":"sample","node":"meeting","name":"Companion","assetKey":"Companion_Neutral"}
                ]}
                """);
        var result = AppNavigator.loadStageDirections(file.toString(), folder.resolve("missing-default.json"));
        UUID chapter = UUID.nameUUIDFromBytes("AdaptiveReadingGame:chapter:sample".getBytes(StandardCharsets.UTF_8));
        assertTrue(result.notice().isEmpty());
        assertEquals("Companion", result.directions().forNode(chapter, "meeting").orElseThrow().name());
    }
}

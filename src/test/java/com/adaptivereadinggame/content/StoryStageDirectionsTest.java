package com.adaptivereadinggame.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StoryStageDirectionsTest {
    @Test void resolvesOnlyTheAuthoredChapterAndNode() {
        var directions = StoryStageDirections.parse("""
                {"formatVersion":1,"directions":[
                  {"chapter":"chapter-one","node":"meeting","name":"Companion","assetKey":"Companion_Neutral"}
                ]}
                """);
        var chapter = StoryPackLoader.stableId("chapter", "chapter-one");
        var support = directions.forNode(chapter, "meeting").orElseThrow();
        assertEquals("Companion", support.name());
        assertEquals("Companion_Neutral", support.assetKey());
        assertTrue(directions.forNode(chapter, "other-node").isEmpty());
        assertTrue(directions.forNode(StoryPackLoader.stableId("chapter", "other-chapter"), "meeting").isEmpty());
    }

    @Test void rejectsAmbiguousOrUnsafeCues() {
        assertThrows(IllegalArgumentException.class, () -> StoryStageDirections.parse("""
                {"formatVersion":1,"directions":[
                  {"chapter":"one","node":"meeting","name":"Companion","assetKey":"../outside"}
                ]}
                """));
        assertThrows(IllegalArgumentException.class, () -> StoryStageDirections.parse("""
                {"formatVersion":1,"directions":[
                  {"chapter":"one","node":"meeting","name":"A","assetKey":"A"},
                  {"chapter":"one","node":"meeting","name":"B","assetKey":"B"}
                ]}
                """));
        assertThrows(IllegalArgumentException.class, () -> StoryStageDirections.parse("""
                {"formatVersion":1,"directions":[],"unexpected":true}
                """));
        assertThrows(IllegalArgumentException.class, () -> StoryStageDirections.parse("""
                {"formatVersion":1,"formatVersion":1,"directions":[]}
                """));
        assertThrows(IllegalArgumentException.class, () -> StoryStageDirections.parse("""
                {"formatVersion":4294967297,"directions":[]}
                """));
    }

    @Test void rejectsOversizedFiles(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("directions.json");
        Files.writeString(file, " ".repeat(1_048_577));
        assertThrows(IllegalArgumentException.class, () -> StoryStageDirections.read(file));
    }
}

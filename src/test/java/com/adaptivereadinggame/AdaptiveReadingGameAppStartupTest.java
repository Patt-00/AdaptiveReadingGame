package com.adaptivereadinggame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveReadingGameAppStartupTest {
    @TempDir Path projectRoot;

    @Test void missingDefaultPackFailsClearlyInsteadOfStartingAnEmptyGame() {
        IOException failure = assertThrows(IOException.class,
                () -> AdaptiveReadingGameApp.resolveContentPack(null, projectRoot));
        assertTrue(failure.getMessage().contains("default story pack is missing"));
        assertTrue(failure.getMessage().contains("ARG_CONTENT_PACK"));
    }

    @Test void configuredPackIsKeptAndMustExist() throws Exception {
        Path configured = projectRoot.resolve("custom/story.json");
        Files.createDirectories(configured.getParent());
        Files.writeString(configured, "{}");

        assertEquals(configured, AdaptiveReadingGameApp.resolveContentPack("custom/story.json", projectRoot));
        IOException failure = assertThrows(IOException.class,
                () -> AdaptiveReadingGameApp.resolveContentPack("missing.json", projectRoot));
        assertTrue(failure.getMessage().contains("ARG_CONTENT_PACK"));
        assertTrue(failure.getMessage().contains("was not found"));
    }
}

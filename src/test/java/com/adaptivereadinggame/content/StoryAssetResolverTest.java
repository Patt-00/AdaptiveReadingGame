package com.adaptivereadinggame.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StoryAssetResolverTest {
    @Test void resolvesOptionalAssetsOnlyInsideTheirFolders(@TempDir Path assets) throws IOException {
        Path scene = Files.createFile(Files.createDirectories(assets.resolve("scenes")).resolve("hall.jpg"));
        Path cg = Files.createFile(Files.createDirectories(assets.resolve("cg")).resolve("poisoning.png"));
        StoryAssetResolver resolver = new StoryAssetResolver();

        assertEquals(scene.toRealPath(), resolver.findSceneImage("hall", assets).orElseThrow());
        assertEquals(cg.toRealPath(), resolver.findCgImage("poisoning", assets).orElseThrow());
        assertTrue(resolver.findSceneImage("../secret", assets).isEmpty());
        assertTrue(resolver.findCgImage("../../outside", assets).isEmpty());
    }

    @Test void missingDirectoriesAndKeysAreSilentOptionalMisses(@TempDir Path assets) {
        StoryAssetResolver resolver = new StoryAssetResolver();
        assertTrue(resolver.findSceneImage("hall", assets).isEmpty());
        assertTrue(resolver.findCgImage("poisoning", assets).isEmpty());
    }
}

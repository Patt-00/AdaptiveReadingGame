package com.adaptivereadinggame.content;

import com.adaptivereadinggame.repository.memory.InMemoryGameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StoryPackLoaderTest {
    private static final String PACK = """
            {
              "formatVersion": 1,
              "chapters": [
                {
                  "id": "chapter-01-easy",
                  "title": "Fixture chapter",
                  "difficulty": "EASY",
                  "passage": "Fixture passage text.",
                  "startNodeId": "opening",
                  "nodes": [
                    {"id":"opening","speakerName":"Guide","speakerAssetKey":"guide","dialogueText":"Fixture dialogue.","thought":false,"readingCheck":false,"nextNodeId":"choice","choices":[]},
                    {"id":"choice","speakerName":"Guide","dialogueText":"Choose a test path.","thought":false,"readingCheck":false,"nextNodeId":"","choices":[{"id":"help","text":"Help","nextNodeId":"check"},{"id":"wait","text":"Wait","nextNodeId":"check"}]},
                    {"id":"check","speakerName":"","dialogueText":"Fixture reading check.","thought":false,"readingCheck":true,"nextNodeId":"","choices":[]}
                  ],
                  "questions": [
                    {"id":"question-01","prompt":"Fixture question?","choices":["Choice A","Choice B"],"correctChoiceIndex":1,"explanation":"Fixture explanation."}
                  ]
                }
              ]
            }
            """;

    private final StoryPackLoader loader = new StoryPackLoader();

    @Test void characterAssetKeysResolveOnlyWithinTheCharactersFolder(@TempDir Path assets) throws IOException {
        Path characters = Files.createDirectories(assets.resolve("characters"));
        Path image = Files.createFile(characters.resolve("guide.png"));
        var resolver = new CharacterAssetResolver();
        assertEquals(image.toAbsolutePath().normalize(), resolver.findCharacterImage("guide", assets).orElseThrow());
        assertTrue(resolver.findCharacterImage("../outside", assets).isEmpty());
        assertTrue(resolver.findCharacterImage("missing", assets).isEmpty());
    }

    @Test void parsesStableIdsFeedbackAndAssetKeysAndImportsIdempotently() {
        StoryPack first = loader.parse(PACK);
        StoryPack second = loader.parse(PACK);
        var item = first.chapters().getFirst();
        assertEquals(item.chapter().id(), second.chapters().getFirst().chapter().id());
        assertEquals(item.chapter().questions().getFirst().id(), second.chapters().getFirst().chapter().questions().getFirst().id());
        assertEquals("Fixture explanation.", item.chapter().questions().getFirst().explanation());
        assertEquals("guide", item.nodes().getFirst().speakerAssetKey());

        var repository = new InMemoryGameRepository();
        var importer = new StoryPackImporter(repository, repository);
        assertEquals(1, importer.importPack(first));
        assertEquals(1, importer.importPack(second));
        assertEquals(item.chapter(), repository.findChapter(item.chapter().id()).orElseThrow());
        assertEquals(item.startNodeId(), repository.firstNodeId(item.chapter().id()).orElseThrow());
        assertEquals(item.nodes().getFirst(), repository.findNode(item.chapter().id(), "opening").orElseThrow());
    }

    @Test void importsTheLocalPackFileUsedByApplicationConfiguration(@TempDir Path folder) throws IOException {
        Path packFile = Files.writeString(folder.resolve("story-pack.json"), PACK);
        var repository = new InMemoryGameRepository();
        var importer = new StoryPackImporter(repository, repository);
        assertEquals(1, importer.importFile(packFile));
        StoryPack imported = loader.read(packFile);
        assertEquals(imported.chapters().getFirst().chapter(),
                repository.findChapter(imported.chapters().getFirst().chapter().id()).orElseThrow());
    }

    @Test void testOnlyStartupPackIsValidAndAvailableForEntrypointSmokeTest() throws Exception {
        var resource = getClass().getResource("/com/adaptivereadinggame/content/startup-content-smoke.json");
        assertNotNull(resource);
        StoryPack pack = loader.read(Path.of(resource.toURI()));
        assertEquals("startup-smoke-chapter", pack.chapters().getFirst().sourceId());
        assertEquals("Startup Content Check", pack.chapters().getFirst().chapter().title());
    }

    @Test void rejectsUnsupportedVersionUnknownFieldsAndTrailingJson() {
        assertThrows(IllegalArgumentException.class, () -> loader.parse(PACK.replace("\"formatVersion\": 1", "\"formatVersion\": 4")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(PACK.replace("\"formatVersion\": 1", "\"formatVersion\": 1, \"unexpected\": true")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(PACK + " {}"));
    }

    @Test void rejectsBrokenChoicesCyclesAndPathsThatSkipReadingCheck() {
        String missingDestination = PACK.replace("\"nextNodeId\":\"check\"", "\"nextNodeId\":\"missing\"");
        assertThrows(IllegalArgumentException.class, () -> loader.parse(missingDestination));

        String loop = PACK.replace("\"nextNodeId\":\"choice\"", "\"nextNodeId\":\"opening\"");
        assertThrows(IllegalArgumentException.class, () -> loader.parse(loop));

        String deadEnd = PACK.replace("\"nextNodeId\":\"choice\"", "\"nextNodeId\":\"\"");
        assertThrows(IllegalArgumentException.class, () -> loader.parse(deadEnd));
    }

    @Test void rejectsAssetPathsAndInvalidAnswerKeys() {
        String pathTraversal = PACK.replace("\"speakerAssetKey\":\"guide\"", "\"speakerAssetKey\":\"../guide\"");
        assertThrows(IllegalArgumentException.class, () -> loader.parse(pathTraversal));
        String badAnswer = PACK.replace("\"correctChoiceIndex\":1", "\"correctChoiceIndex\":8");
        assertThrows(IllegalArgumentException.class, () -> loader.parse(badAnswer));
    }

    @Test void loadsTheCurrentDraftWithBothInvestigationOrdersAndThreeDifficultyVariants() throws IOException {
        StoryPack pack = loader.read(Path.of("assets/content/hailey-investigation-draft.json"));
        assertEquals(StoryPackLoader.ADAPTIVE_FORMAT_VERSION, pack.formatVersion());
        var chapter = pack.chapters().getFirst().chapter();
        assertTrue(chapter.adaptive());
        assertEquals(4, chapter.checkpoints().size());
        assertTrue(chapter.checkpoints().stream().allMatch(checkpoint ->
                checkpoint.questionsByDifficulty().keySet().equals(java.util.Set.of(
                        com.adaptivereadinggame.model.Difficulty.EASY,
                        com.adaptivereadinggame.model.Difficulty.MEDIUM,
                        com.adaptivereadinggame.model.Difficulty.HARD))));
        assertTrue(pack.chapters().getFirst().nodes().stream().anyMatch(node -> node.storyChoices().stream()
                .anyMatch(choice -> choice.routeId().equals("study-first"))));
        assertTrue(pack.chapters().getFirst().nodes().stream().anyMatch(node -> node.storyChoices().stream()
                .anyMatch(choice -> choice.routeId().equals("hall-first"))));
        assertTrue(pack.chapters().getFirst().nodes().stream().anyMatch(com.adaptivereadinggame.model.StoryNode::storyComplete));
        assertTrue(pack.chapters().getFirst().availableForNewGame(), "Version 2 packs default to selectable chapters.");
    }

    @Test void availabilityFieldIsRequiredOnlyByVersionThreePacks() throws IOException {
        String current = Files.readString(Path.of("assets/content/hailey-investigation-complete.json"));
        assertThrows(IllegalArgumentException.class,
                () -> loader.parse(current.replaceAll(",\\s*\"availableForNewGame\": true", "")));

        StoryPack legacyAdaptive = loader.read(Path.of("assets/content/hailey-investigation-draft.json"));
        assertTrue(legacyAdaptive.chapters().getFirst().availableForNewGame());
    }
}

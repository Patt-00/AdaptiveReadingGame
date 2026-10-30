package com.adaptivereadinggame.content;

import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.repository.memory.InMemoryGameRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CompletedStoryTest {
    @Test void newEditionHasACompleteQuestionBankAndFourOutcomes() throws Exception {
        var pack = new StoryPackLoader().read(Path.of("assets/content/hailey-investigation-complete.json"));
        assertEquals(3, pack.formatVersion());
        assertEquals(3, pack.chapters().size());

        var draft = pack.chapters().getFirst();
        var completedV1 = pack.chapters().get(1);
        var completed = pack.chapters().get(2);
        assertFalse(draft.availableForNewGame());
        assertFalse(completedV1.availableForNewGame());
        assertTrue(completed.availableForNewGame());
        assertNotEquals(draft.chapter().id(), completed.chapter().id());
        assertNotEquals(completedV1.chapter().id(), completed.chapter().id());
        assertEquals(12, completedV1.chapter().questions().size(), "The previous chapter version remains unchanged.");

        assertEquals(36, completed.chapter().questions().size());
        assertTrue(completed.chapter().checkpoints().stream().allMatch(checkpoint ->
                checkpoint.questionsByDifficulty().keySet().equals(Set.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD))
                        && checkpoint.questionsByDifficulty().values().stream().allMatch(questions -> questions.size() == 3)));
        assertTrue(completed.chapter().questions().stream().allMatch(question ->
                question.choices().size() == 4 && !question.explanation().isBlank()));

        var nodes = completed.nodes().stream().collect(Collectors.toMap(
                node -> node.storyNodeId(), node -> node));
        assertEquals(4, completed.nodes().stream().filter(node -> node.storyNodeId().startsWith("finish-")
                && node.storyComplete()).count());
        assertEquals("search-correct", nodes.get("conclusion-suspect").storyChoices().getFirst().nextNodeId());
        assertEquals("search-wrong", nodes.get("conclusion-suspect").storyChoices().get(1).nextNodeId());
        assertEquals("ending-correct-found", nodes.get("search-correct").storyChoices().getFirst().nextNodeId());
        assertEquals("ending-correct-lost", nodes.get("search-correct").storyChoices().get(1).nextNodeId());
        assertEquals("ending-wrong-found", nodes.get("search-wrong").storyChoices().getFirst().nextNodeId());
        assertEquals("ending-wrong-lost", nodes.get("search-wrong").storyChoices().get(1).nextNodeId());
        for (String outcome : Set.of("correct-found", "correct-lost", "wrong-found", "wrong-lost")) {
            assertTrue(nodes.get("ending-" + outcome).nextNodeId().equals("aftermath-" + outcome));
            assertTrue(nodes.get("aftermath-" + outcome).nextNodeId().equals("finish-" + outcome));
            assertTrue(nodes.get("finish-" + outcome).storyComplete());
        }
    }

    @Test void importedLegacyChaptersStayRestorableButOnlyTheNewEditionIsSelectable() throws Exception {
        var pack = new StoryPackLoader().read(Path.of("assets/content/hailey-investigation-complete.json"));
        var repository = new InMemoryGameRepository();
        new StoryPackImporter(repository, repository).importPack(pack);

        var availableIds = repository.findByDifficulty(Difficulty.EASY).stream()
                .map(chapter -> chapter.id()).collect(Collectors.toSet());
        assertEquals(Set.of(pack.chapters().get(2).chapter().id()), availableIds);
        assertTrue(repository.findChapter(pack.chapters().getFirst().chapter().id()).isPresent());
        assertTrue(repository.findChapter(pack.chapters().get(1).chapter().id()).isPresent());
    }
}

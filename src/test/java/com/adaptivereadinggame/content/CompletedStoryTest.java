package com.adaptivereadinggame.content;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CompletedStoryTest {
    @Test void loadsBothVersionsAndFourEndings() throws Exception {
        var pack = new StoryPackLoader().read(Path.of("assets/content/hailey-investigation-complete.json"));
        assertEquals(2, pack.chapters().size());
        var completed = pack.chapters().get(1);
        assertNotEquals(pack.chapters().getFirst().chapter().id(), completed.chapter().id());
        assertEquals(4, completed.nodes().stream().filter(n -> n.storyNodeId().startsWith("finish-") && n.storyComplete()).count());
        assertEquals(12, completed.chapter().questions().size(), "Two route-specific checkpoints each need three question levels.");
        assertTrue(completed.chapter().questions().stream().allMatch(q -> !q.explanation().startsWith("Provisional")));
        var nodes = completed.nodes().stream().collect(java.util.stream.Collectors.toMap(
                n -> n.storyNodeId(), n -> n));
        assertEquals("search-correct", nodes.get("conclusion-suspect").storyChoices().getFirst().nextNodeId());
        assertEquals("search-wrong", nodes.get("conclusion-suspect").storyChoices().get(1).nextNodeId());
        assertEquals("ending-correct-found", nodes.get("search-correct").storyChoices().getFirst().nextNodeId());
        assertEquals("ending-correct-lost", nodes.get("search-correct").storyChoices().get(1).nextNodeId());
        assertEquals("ending-wrong-found", nodes.get("search-wrong").storyChoices().getFirst().nextNodeId());
        assertEquals("ending-wrong-lost", nodes.get("search-wrong").storyChoices().get(1).nextNodeId());
        assertTrue(nodes.get("ending-correct-found").cgAssetKey().equals("cg-killer-caught"));
    }
}

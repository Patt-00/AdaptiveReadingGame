package com.adaptivereadinggame.content;

import com.adaptivereadinggame.audio.GameAudio.Track;
import com.adaptivereadinggame.model.StoryNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StoryMusicCuesTest {
    @Test void draftUsesInvestigationAndTenseCuesAndReadingChecksStayQuiet() {
        UUID chapter = StoryPackLoader.stableId("chapter", "hailey-investigation-reading-draft");
        assertEquals(Track.INVESTIGATION, StoryMusicCues.forNode(chapter, node("study-first-01", false)));
        assertEquals(Track.TENSE, StoryMusicCues.forNode(chapter, node("poisoning-cg", false)));
        assertEquals(Track.TENSE, StoryMusicCues.forNode(chapter, node("accusation-02", false)));
        assertEquals(Track.SILENT, StoryMusicCues.forNode(chapter, node("study-check", true)));
        assertEquals(Track.SILENT, StoryMusicCues.forNode(UUID.randomUUID(), node("poisoning-cg", false)));
    }
    @Test void completedConclusionUsesTenseCueAndCompletionFallsSilent() throws Exception {
        UUID chapter = StoryPackLoader.stableId("chapter", "hailey-investigation-final-v2");
        var pack = new StoryPackLoader().read(java.nio.file.Path.of("assets/content/hailey-investigation-complete.json"));
        var completed = pack.chapters().stream().filter(item -> item.sourceId().equals("hailey-investigation-final-v2"))
                .findFirst().orElseThrow();
        var accusation = completed.nodes().stream().filter(n -> n.storyNodeId().equals("conclusion-suspect")).findFirst().orElseThrow();
        var search = completed.nodes().stream().filter(n -> n.storyNodeId().equals("search-correct")).findFirst().orElseThrow();
        var aftermath = completed.nodes().stream().filter(n -> n.storyNodeId().equals("aftermath-correct-found")).findFirst().orElseThrow();
        var finish = completed.nodes().stream().filter(n -> n.storyNodeId().equals("finish-correct-found")).findFirst().orElseThrow();
        assertEquals(Track.TENSE, StoryMusicCues.forNode(chapter, accusation));
        assertEquals(Track.TENSE, StoryMusicCues.forNode(chapter, search));
        assertEquals(Track.TENSE, StoryMusicCues.forNode(chapter, aftermath));
        assertEquals(Track.SILENT, StoryMusicCues.forNode(chapter, finish));
    }
    private static StoryNode node(String id, boolean check) {
        return new StoryNode(id, "", "Test dialogue", false, List.of(), "", check);
    }
}

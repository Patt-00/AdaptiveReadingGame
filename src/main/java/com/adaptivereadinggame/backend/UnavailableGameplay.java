package com.adaptivereadinggame.backend;

import com.adaptivereadinggame.model.*;
import com.adaptivereadinggame.repository.*;
import com.adaptivereadinggame.service.StoryService;
import java.util.*;

/** Honest integration boundary until Dev 2 supplies gameplay and Dev 3 supplies prepared nodes/drafts. */
public final class UnavailableGameplay implements StoryService, StoryRepository, AssessmentDraftRepository {
    private static UnsupportedOperationException unavailable() {
        return new UnsupportedOperationException("Dev 2's story/reading-check implementation is not connected yet.");
    }
    @Override public GameState startChapter(UUID studentId, UUID chapterId) { throw unavailable(); }
    @Override public GameState advanceStory(UUID studentId) { throw unavailable(); }
    @Override public GameState chooseStory(UUID studentId, String choiceId) { throw unavailable(); }
    @Override public Optional<GameState> currentState(UUID studentId) { return Optional.empty(); }
    @Override public GameState restoreState(GameState gameState) { throw unavailable(); }
    @Override public void clearStateFor(UUID studentId) { /* No process-local game state is kept here. */ }
    @Override public Optional<String> firstNodeId(UUID chapterId) { return Optional.empty(); }
    @Override public Optional<StoryNode> findNode(UUID chapterId, String storyNodeId) { return Optional.empty(); }
    @Override public void createDraft(GameState gameState) { throw unavailable(); }
    @Override public int recordFirstAnswer(UUID sessionId, UUID studentId, UUID questionId, int selectedIndex) { throw unavailable(); }
    @Override public Optional<GameState> findDraft(UUID sessionId) { return Optional.empty(); }
}

package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.service.AccountGameService.ChapterSummary;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import java.io.IOException;
import java.util.Objects;

/** Explains the score and navigation rules before a learner starts a chapter. */
public final class InstructionsController {
    private final AppNavigator navigator;
    private ChapterSummary chapter;
    @FXML private BorderPane instructionsRoot;
    @FXML private Label chapterLabel;
    @FXML private Label difficultyLabel;

    public InstructionsController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        chapter = navigator.pendingChapter().orElse(null);
        if (chapter == null) {
            navigator.notice("Chapter unavailable", "Choose a prepared chapter before opening its instructions.");
            backToMenu();
            return;
        }
        chapterLabel.setText(chapter.title());
        difficultyLabel.setText("Starting difficulty: " + titleCase(chapter.difficulty().name()));
    }

    @FXML private void startChapter() {
        if (chapter == null || navigator.workInProgress()) return;
        var accountGame = navigator.backend().accountGameService();
        boolean discardConfirmed = !accountGame.hasUnsavedProgress();
        if (!discardConfirmed) {
            if (!navigator.confirm("Start a new chapter?", "Starting this chapter replaces unsaved story progress.")) return;
            discardConfirmed = true;
        }
        boolean confirmed = discardConfirmed;
        instructionsRoot.setDisable(true);
        navigator.runAsync("Start chapter",
                () -> navigator.backend().accountGameService().startChapter(chapter.id(), chapter.difficulty(), confirmed),
                ignored -> {
                    navigator.clearPendingChapter();
                    navigator.clearStoryLog();
                    try { navigator.showGameplay(); }
                    catch (IOException failure) {
                        navigator.notice("Game screen unavailable", "The chapter started, but its screen could not open.");
                        backToMenu();
                    }
                }, failure -> {
                    instructionsRoot.setDisable(false);
                    navigator.notice("Chapter unavailable", "The selected chapter could not be started. Your existing progress remains unchanged.");
                });
    }

    @FXML private void backToMenu() {
        navigator.clearPendingChapter();
        try { navigator.showMenu(); }
        catch (IOException failure) { navigator.notice("Menu unavailable", "The main menu could not open."); }
    }

    private static String titleCase(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

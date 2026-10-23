package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.model.AssessmentResult;
import javafx.fxml.FXML;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Runs result calculation off the JavaFX thread and then opens the saved result. */
public final class AnalysisController {
    private final AppNavigator navigator;

    public AnalysisController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        UUID sessionId;
        try {
            sessionId = navigator.backend().accountGameService().currentGame().orElseThrow()
                    .assessmentSessionId();
            if (sessionId == null) throw new IllegalStateException("No reading check is ready to complete.");
        } catch (RuntimeException unavailable) {
            navigator.notice("Results unavailable", "The current reading check could not be found.");
            returnToGame();
            return;
        }
        navigator.runAsync("Complete reading check",
                () -> navigator.backend().accountGameService().complete(sessionId),
                this::showResults, failure -> {
                    navigator.notice("Results unavailable", "The result could not be stored. Your first answers remain saved.");
                    returnToGame();
                });
    }

    private void showResults(AssessmentResult ignored) {
        try { navigator.showResults(); }
        catch (IOException failure) {
            navigator.notice("Results screen unavailable", "Your result was stored. Open Reading History from the main menu.");
            returnToGame();
        }
    }

    private void returnToGame() {
        try { navigator.showGameplay(); }
        catch (IOException failure) {
            try { navigator.showMenu(); }
            catch (IOException ignored) { navigator.notice("Screen unavailable", "Please restart the application."); }
        }
    }
}

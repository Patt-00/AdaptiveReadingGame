package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.model.AssessmentResult;
import com.adaptivereadinggame.model.DifficultyDecisionSource;
import com.adaptivereadinggame.service.AccountGameService.ChapterSummary;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Displays the stored score and lets the learner choose a prepared next-level chapter. */
public final class ResultsController {
    private record ResultsData(AssessmentResult result, String chapterTitle, List<ChapterSummary> nextChapters) {}

    private final AppNavigator navigator;
    private List<ChapterSummary> nextChapters = List.of();
    @FXML private Label chapterTitle;
    @FXML private Label scoreLabel;
    @FXML private Label answerCountLabel;
    @FXML private Label nextDifficultyLabel;
    @FXML private Label decisionSourceLabel;
    @FXML private Label nextChapterStatus;
    @FXML private Button nextChapterButton;

    public ResultsController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        navigator.runAsync("Load chapter result", () -> {
            var game = navigator.backend().accountGameService().currentGameView()
                    .orElseThrow(() -> new IllegalStateException("No chapter is active."));
            var result = game.completedResult()
                    .orElseThrow(() -> new IllegalStateException("The chapter result has not been stored."));
            var next = navigator.backend().accountGameService().availableChapters(result.nextDifficulty());
            return new ResultsData(result, game.chapter().title(), next);
        }, this::render, failure -> {
            navigator.notice("Results unavailable", "The saved chapter result could not be opened.");
            mainMenu();
        });
    }

    private void render(ResultsData data) {
        AssessmentResult result = data.result();
        chapterTitle.setText(data.chapterTitle());
        scoreLabel.setText("Comprehension score: " + Math.round(result.attempt().score() * 100) + "%");
        answerCountLabel.setText(result.attempt().correctAnswers() + " of "
                + result.attempt().totalQuestions() + " first answers correct");
        nextDifficultyLabel.setText("Next difficulty: " + titleCase(result.nextDifficulty().name()));
        decisionSourceLabel.setText(sourceMessage(result.decisionSource()));
        nextChapters = List.copyOf(data.nextChapters());
        boolean available = !nextChapters.isEmpty();
        nextChapterButton.setDisable(!available);
        nextChapterButton.setText(available ? "NEXT CHALLENGE" : "NO CHAPTER READY");
        nextChapterStatus.setText(available
                ? "Your result is recorded. Choose a prepared chapter at the recommended difficulty."
                : "Your result is recorded. No chapter is loaded at the recommended difficulty yet.");
    }

    @FXML private void nextChapter() {
        if (nextChapters.isEmpty()) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Next challenge");
        dialog.setHeaderText("Choose a " + titleCase(nextChapters.getFirst().difficulty().name()) + " chapter");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(620);
        VBox choices = new VBox(12);
        ChapterSummary[] selectedChapter = new ChapterSummary[1];
        for (ChapterSummary chapter : nextChapters) {
            Button button = new Button(chapter.title() + "  ·  " + titleCase(chapter.difficulty().name()));
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("chapter-choice-button");
            button.setOnAction(event -> {
                selectedChapter[0] = chapter;
                dialog.close();
            });
            choices.getChildren().add(button);
        }
        dialog.getDialogPane().setContent(choices);
        navigator.styleDialog(dialog);
        dialog.showAndWait();
        ChapterSummary selected = selectedChapter[0];
        if (selected == null || !navigator.confirm("Start next chapter?",
                "Your result is saved. Starting a new chapter replaces any remaining unsaved story state.")) return;
        navigator.runAsync("Start next chapter",
                () -> navigator.backend().accountGameService().startChapter(selected.id(), true),
                ignored -> showGameplay(), failure -> {
                    navigator.notice("Chapter unavailable", "The selected chapter could not be started.");
                });
    }

    @FXML private void mainMenu() {
        try { navigator.showMenu(); }
        catch (IOException failure) { navigator.notice("Screen unavailable", "The main menu could not open."); }
    }

    private void showGameplay() {
        try { navigator.showGameplay(); }
        catch (IOException failure) { navigator.notice("Game screen unavailable", "Return to the main menu and resume the game."); }
    }

    private static String sourceMessage(DifficultyDecisionSource source) {
        return switch (source) {
            case MODEL -> "The model suggested this level. Java checked the recommendation before applying it.";
            case JAVA_FALLBACK -> "The model was unavailable. Java used its fallback rule to select this level.";
            case JAVA_GUARDRAIL -> "Java kept this level because the model suggestion did not pass its accuracy checks.";
            case JAVA_RULES -> "Java selected this level from the completed reading results.";
        };
    }

    private static String titleCase(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

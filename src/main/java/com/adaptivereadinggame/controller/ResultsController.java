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
    private record ResultsData(AssessmentResult result, String chapterTitle, List<ChapterSummary> nextChapters,
                               boolean adaptive, boolean sectionEnd, boolean terminal, String completionMessage,
                               int totalCorrect, int totalQuestions, int checkpointCount) {}

    private final AppNavigator navigator;
    private List<ChapterSummary> nextChapters = List.of();
    @FXML private Label resultsHeading;
    @FXML private Label chapterTitle;
    @FXML private Label scoreLabel;
    @FXML private Label answerCountLabel;
    @FXML private Label episodeProgressLabel;
    @FXML private Label nextDifficultyLabel;
    @FXML private Label decisionSourceLabel;
    @FXML private Label nextChapterStatus;
    @FXML private Button nextChapterButton;

    public ResultsController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        navigator.runAsync("Load chapter result", () -> {
            var accountGame = navigator.backend().accountGameService();
            var game = accountGame.currentGameView()
                    .orElseThrow(() -> new IllegalStateException("No chapter is active."));
            var result = accountGame.currentResult()
                    .orElseThrow(() -> new IllegalStateException("The chapter result has not been stored."));
            boolean adaptive = !game.state().activeCheckpointId().isEmpty()
                    || !game.state().lastCompletedCheckpointId().isEmpty()
                    || game.storyNode().storyComplete() || !game.state().routeId().isEmpty();
            List<AssessmentResult> episodeResults = adaptive ? accountGame.episodeResults() : List.of(result);
            int totalCorrect = episodeResults.stream().mapToInt(item -> item.attempt().correctAnswers()).sum();
            int totalQuestions = episodeResults.stream().mapToInt(item -> item.attempt().totalQuestions()).sum();
            boolean terminal = game.storyNode().storyComplete();
            boolean sectionEnd = terminal || adaptive && !terminal && accountGame.checkpointEndsSection();
            var next = adaptive || terminal ? List.<ChapterSummary>of()
                    : accountGame.availableChapters(result.nextDifficulty());
            return new ResultsData(result, game.chapter().title(), next, adaptive, sectionEnd, terminal, game.storyNode().dialogueText(),
                    totalCorrect, totalQuestions, episodeResults.size());
        }, this::render, failure -> {
            navigator.notice("Results unavailable", "The saved chapter result could not be opened.");
            mainMenu();
        });
    }

    private void render(ResultsData data) {
        AssessmentResult result = data.result();
        resultsHeading.setText(data.adaptive() ? (data.terminal() ? "STORY SECTION COMPLETE" : "CHECKPOINT RESULTS")
                : "CHAPTER RESULTS");
        chapterTitle.setText(data.chapterTitle());
        scoreLabel.setText(data.adaptive() && (data.terminal() || data.sectionEnd())
                ? "Story section score: " + percent(data.totalCorrect(), data.totalQuestions()) + "%"
                : "Comprehension score: " + Math.round(result.attempt().score() * 100) + "%");
        answerCountLabel.setText(data.adaptive() && !data.terminal()
                ? checkpointTitle(result.attempt().checkpointId()) + " checkpoint: "
                        + result.attempt().correctAnswers() + " of " + result.attempt().totalQuestions() + " correct"
                : data.totalCorrect() + " of " + data.totalQuestions() + " first answers correct");
        episodeProgressLabel.setVisible(data.adaptive());
        episodeProgressLabel.setManaged(data.adaptive());
        if (data.adaptive()) episodeProgressLabel.setText("Reading checks completed: " + data.checkpointCount()
                + "  ·  Story choices do not affect your score.");
        nextDifficultyLabel.setText("Next difficulty: " + titleCase(result.nextDifficulty().name()));
        decisionSourceLabel.setText(sourceMessage(result.decisionSource()));
        nextChapters = List.copyOf(data.nextChapters());
        boolean adaptiveAction = data.adaptive();
        boolean available = adaptiveAction || !nextChapters.isEmpty();
        nextChapterButton.setDisable(!available);
        nextChapterButton.setText(adaptiveAction
                ? data.terminal() ? "MAIN MENU" : "CONTINUE STORY"
                : available ? "NEXT CHALLENGE" : "NO CHAPTER READY");
        nextChapterButton.setVisible(!data.terminal());
        nextChapterButton.setManaged(!data.terminal());
        nextChapterStatus.setText(data.adaptive()
                ? data.terminal()
                    ? data.completionMessage()
                    : data.sectionEnd()
                        ? "This is the last available checkpoint. Continue to the end of this story section."
                        : "Your first answer is recorded. Continue to the other investigation area at the recommended difficulty."
                : available
                    ? "Your result is recorded. Choose a prepared chapter at the recommended difficulty."
                    : "Your result is recorded. No chapter is loaded at the recommended difficulty yet.");
    }

    @FXML private void nextChapter() {
        var accountGame = navigator.backend().accountGameService();
        var current = accountGame.currentGameView().orElse(null);
        if (current != null && (current.storyNode().storyComplete()
                || !current.state().activeCheckpointId().isEmpty() || !current.state().routeId().isEmpty())) {
            if (current.storyNode().storyComplete()) { mainMenu(); return; }
            navigator.runAsync("Continue story", accountGame::continueAfterCheckpoint, ignored -> {
                showGameplay();
            }, failure -> navigator.notice("Story unavailable", "The saved checkpoint outcome could not be opened."));
            return;
        }
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
                ignored -> { navigator.clearStoryLog(); showGameplay(); }, failure -> {
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
            case MODEL -> "The game checked a reading helper's suggestion before choosing this level.";
            case JAVA_FALLBACK -> "The reading helper was unavailable, so the game used its usual rule.";
            case JAVA_GUARDRAIL -> "Your level stayed the same after the game checked the suggestion.";
            case JAVA_RULES -> "Your next reading level is based on your reading-check results.";
        };
    }

    private static String titleCase(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String checkpointTitle(String value) {
        return java.util.Arrays.stream(value.split("[-_.]"))
                .filter(part -> !part.isBlank()).map(ResultsController::titleCase)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static long percent(int correct, int total) {
        return total == 0 ? 0 : Math.round((double) correct / total * 100);
    }
}

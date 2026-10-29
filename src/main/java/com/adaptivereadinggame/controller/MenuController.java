package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.model.AssessmentResult;
import com.adaptivereadinggame.service.AccountGameService.ChapterSummary;
import com.adaptivereadinggame.service.ConfirmationRequiredException;
import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.SaveSnapshot;
import com.adaptivereadinggame.view.MenuArtworkPane;
import javafx.concurrent.Task;
import javafx.scene.Group;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Account-scoped menu actions; storage, scoring, and story ownership stay in services. */
public final class MenuController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
            .withZone(ZoneId.systemDefault());
    private final AppNavigator navigator;
    private List<SaveSnapshot> slots = List.of();
    private List<AssessmentResult> history = List.of();
    @FXML private StackPane menuRoot;
    @FXML private MenuArtworkPane menuArtwork;
    @FXML private Group menuFlourish;
    @FXML private VBox menuOptions;
    @FXML private HBox accountActions;
    @FXML private Button continueButton;
    @FXML private Label accountLabel;
    @FXML private Label continueStatusLabel;
    @FXML private Label runtimeLabel;
    @FXML private Label fontLabel;

    public MenuController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        menuArtwork.fitToWindowWidth(menuRoot.getWidth());
        menuRoot.widthProperty().addListener((observable, before, after) -> menuArtwork.fitToWindowWidth(after.doubleValue()));
        menuRoot.heightProperty().addListener((observable, before, after) -> updateCompactLayout(after.doubleValue()));
        accountLabel.setText(navigator.backend().accountGameService().currentAccount()
                .map(account -> "Welcome, " + account.displayName()).orElse("No account signed in"));
        runtimeLabel.setText(navigator.storageDescription());
        fontLabel.setText(navigator.fontNotice());
        refresh();
    }

    private void updateCompactLayout(double height) {
        boolean compact = height > 0 && height < 620;
        menuRoot.getStyleClass().remove("compact-menu");
        if (compact) menuRoot.getStyleClass().add("compact-menu");
        menuFlourish.setVisible(!compact);
        menuFlourish.setManaged(!compact);
    }

    private void refresh() {
        run("Read progress", () -> {
            var accountGame = navigator.backend().accountGameService();
            boolean activeGame = accountGame.currentGame().isPresent();
            return new MenuState(accountGame.snapshots(), accountGame.history(), activeGame);
        }, state -> {
            slots = List.copyOf(state.slots());
            history = List.copyOf(state.history());
            continueButton.setText(state.activeGame() ? "RETURN TO GAME" : "CONTINUE");
            continueButton.setDisable(!state.activeGame() && slots.isEmpty());
            continueStatusLabel.setText(state.activeGame()
                    ? "Return to your active story, reading check, or results."
                    : slots.isEmpty() ? "Save progress in Save/Load to enable Continue."
                            : "Continue resumes your most recently saved progress.");
            continueButton.setAccessibleHelp(state.activeGame() ? "Return to the active story or results."
                    : slots.isEmpty() ? "Open Save/Load and save progress to enable Continue."
                            : "Resume the most recently saved checkpoint.");
        });
    }

    @FXML private void newGame() {
        run("New Game", () -> {
            List<AssessmentResult> completed = navigator.backend().accountGameService().history();
            Difficulty nextDifficulty = completed.isEmpty() ? Difficulty.EASY : completed.getLast().nextDifficulty();
            return navigator.backend().accountGameService().availableChapters(nextDifficulty);
        }, chapters -> {
            if (chapters.isEmpty()) {
                navigator.notice("No chapter available", "No prepared chapter is available at the recommended difficulty. "
                        + "Load approved story content and prepare a chapter for that level. No pretend story or score has been created.");
                return;
            }
            Dialog<Void> dialog = dialog("New Game", "Choose an available chapter");
            VBox content = new VBox(12);
            for (ChapterSummary chapter : chapters) {
                Button button = new Button(chapter.title() + " — " + chapter.difficulty());
                button.setMaxWidth(Double.MAX_VALUE);
                button.setOnAction(event -> {
                    dialog.close();
                    try { navigator.showInstructions(chapter); }
                    catch (IOException failure) { navigator.notice("Instructions unavailable", "The chapter instructions could not open."); }
                });
                content.getChildren().add(button);
            }
            dialog.getDialogPane().setContent(content);
            dialog.showAndWait();
        });
    }

    @FXML private void continueGame() {
        var active = navigator.backend().accountGameService().currentGame();
        if (active.isPresent()) {
            showGameplay();
            return;
        }
        if (!allowDiscard("Continue saved progress?")) return;
        run("Continue", () -> navigator.backend().accountGameService().continueGame(true), state -> {
            if (state.isEmpty()) navigator.notice("No saved progress", "Create a checkpoint in Save/Load first.");
            else { navigator.clearStoryLog(); showGameplay(); }
        });
    }

    @FXML private void saveLoad() {
        Dialog<Void> dialog = dialog("Save / Load", "Three account-specific save slots");
        VBox content = new VBox(14);
        var accountGame = navigator.backend().accountGameService();
        boolean hasActiveGame = accountGame.currentGame().isPresent();
        boolean hasGame = hasActiveGame && accountGame.currentResult().isEmpty();
        for (int slot = 1; slot <= 3; slot++) {
            final int chosenSlot = slot;
            Optional<SaveSnapshot> snapshot = slots.stream().filter(item -> item.checkpoint().slot() == chosenSlot).findFirst();
            Label details = new Label(snapshot.map(item -> "Slot " + chosenSlot + " — " + item.checkpoint().difficulty()
                    + "\nSaved " + TIME.format(item.checkpoint().savedAt())
                    + "\nStory progress saved").orElse("Slot " + chosenSlot + " — Empty"));
            details.setWrapText(true);
            details.setId("slotDetails" + chosenSlot);
            details.setPrefWidth(400);
            details.setMinHeight(Region.USE_PREF_SIZE);
            details.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(details, Priority.ALWAYS);
            Button save = new Button("SAVE");
            save.setId("saveSlot" + chosenSlot);
            save.setAccessibleText("Save progress to slot " + chosenSlot);
            save.setDisable(!hasGame);
            save.setAccessibleHelp(hasGame ? "Save current progress to slot " + chosenSlot
                    : "Start a playable chapter before saving.");
            save.setOnAction(event -> {
                if (snapshot.isPresent() && !navigator.confirm("Overwrite slot " + chosenSlot + "?",
                        "Replace the existing checkpoint in this slot?")) return;
                dialog.close();
                run("Save", () -> navigator.backend().accountGameService().save(chosenSlot, snapshot.isPresent()), saved -> {
                    refresh();
                    navigator.notice("Save successful", "Your checkpoint was stored in slot " + chosenSlot + ".\n"
                            + navigator.storageDescription());
                });
            });
            Button load = new Button("LOAD");
            load.setId("loadSlot" + chosenSlot);
            load.setAccessibleText("Load progress from slot " + chosenSlot);
            load.setDisable(snapshot.isEmpty());
            load.setAccessibleHelp(snapshot.isPresent() ? "Load the saved progress in slot " + chosenSlot + "."
                    : "Slot " + chosenSlot + " is empty.");
            load.setOnAction(event -> {
                if (!allowDiscard("Load slot " + chosenSlot + "?")) return;
                dialog.close();
                run("Load", () -> navigator.backend().accountGameService().load(chosenSlot, true), state -> {
                    if (state.isEmpty()) navigator.notice("Empty slot", "There is no checkpoint in this slot.");
                    else { navigator.clearStoryLog(); showGameplay(); }
                });
            });
            HBox row = new HBox(12, details, save, load);
            row.getStyleClass().add("slot-row");
            row.setPadding(new Insets(16));
            content.getChildren().add(row);
        }
        String saveNote = hasGame ? navigator.storageDescription()
                : hasActiveGame ? "This chapter result is already recorded. Start the next challenge before saving a new checkpoint.\n"
                    + navigator.storageDescription()
                : "No active chapter. Saving becomes available after a prepared story is started.\n"
                    + navigator.storageDescription();
        Label explanation = new Label(saveNote);
        explanation.setWrapText(true);
        explanation.setId("saveExplanation");
        explanation.setPrefWidth(640);
        explanation.setMinHeight(Region.USE_PREF_SIZE);
        explanation.getStyleClass().add("dialog-note");
        content.getChildren().add(explanation);
        ScrollPane slotsScroll = new ScrollPane(content);
        slotsScroll.setFitToWidth(true);
        slotsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        double slotViewportLimit = Math.min(360, navigator.stage().getHeight() * 0.55);
        slotsScroll.setPrefViewportHeight(Math.min(slotViewportLimit, Math.max(300, content.prefHeight(640))));
        dialog.getDialogPane().setContent(slotsScroll);
        dialog.showAndWait();
    }

    @FXML private void history() {
        Dialog<Void> dialog = dialog("Reading History", "Completed comprehension checks");
        VBox rows = new VBox(14);
        if (history.isEmpty()) {
            Label empty = new Label("No completed reading checks yet.");
            empty.setWrapText(true);
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.getStyleClass().add("dialog-empty-state");
            rows.getChildren().add(empty);
        }
        for (AssessmentResult result : history) {
            String text = TIME.format(result.attempt().completedAt()) + "\n"
                    + result.attempt().correctAnswers() + " / " + result.attempt().totalQuestions()
                    + " correct — " + Math.round(result.attempt().score() * 100) + "%\n"
                    + "Assessed difficulty: " + result.assessedDifficulty()
                    + "  ·  Next difficulty: " + result.nextDifficulty();
            Label record = new Label(text);
            record.setWrapText(true);
            record.getStyleClass().add("history-record");
            rows.getChildren().add(record);
        }
        Label rule = new Label("Story choices do not affect scores. Only the first submitted answer is scored.");
        rule.setWrapText(true);
        rule.getStyleClass().add("dialog-note");
        rows.getChildren().add(rule);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(320, Math.max(90, rows.prefHeight(640))));
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    @FXML private void logout() {
        boolean unsaved = navigator.backend().accountGameService().hasUnsavedProgress();
        if (!navigator.confirm("Log out?", unsaved
                ? "Unsaved progress will be lost. Log out of this account?" : "Return to the login screen?")) return;
        run("Logout", () -> {
            navigator.backend().accountGameService().logout(true);
            return true;
        }, ignored -> {
            try { navigator.showAccount(); }
            catch (IOException failure) { navigator.notice("Screen unavailable", "The login screen could not open."); }
        }, failure -> {
            if (navigator.backend().accountGameService().currentAccount().isEmpty()) {
                try { navigator.showAccount(); }
                catch (IOException unavailable) {
                    navigator.notice("Screen unavailable", "The login screen could not open. Please restart the application.");
                    return;
                }
                navigator.notice("Logged out", "Your account was signed out, but game cleanup could not finish. "
                        + "Please restart before beginning a new game.");
            } else navigator.notice("Logout unavailable", "The account could not be signed out. Please try again.");
        });
    }

    @FXML private void settings() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reading settings");
        dialog.setHeaderText("Adjust the reading view");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
        Slider textSize = new Slider(0.8, 1.6, navigator.readingFontScale());
        textSize.setId("readingTextSize");
        textSize.setShowTickMarks(true);
        textSize.setShowTickLabels(true);
        textSize.setMajorTickUnit(0.2);
        textSize.setBlockIncrement(0.1);
        textSize.setAccessibleText("Reading text size");
        textSize.setAccessibleHelp("Adjust the size of story, passage, and answer text.");
        CheckBox artwork = new CheckBox("Show character artwork when available");
        artwork.setId("showCharacterArtwork");
        artwork.setSelected(navigator.characterArtworkEnabled());
        artwork.setAccessibleHelp("Turn this off to show character initials instead of character artwork.");
        CheckBox reducedMotion = new CheckBox("Reduce story animations");
        reducedMotion.setId("reducedMotion");
        reducedMotion.setSelected(navigator.reducedMotion());
        reducedMotion.setAccessibleHelp("Show story artwork and dialogue without animated transitions.");
        Label textSizeLabel = new Label("Reading text size");
        textSizeLabel.setLabelFor(textSize);
        VBox content = new VBox(14, textSizeLabel, textSize, artwork, reducedMotion);
        if (!navigator.fontNotice().isEmpty()) {
            Label fontNotice = new Label(navigator.fontNotice());
            fontNotice.setWrapText(true);
            fontNotice.getStyleClass().add("dialog-note");
            content.getChildren().add(fontNotice);
        }
        if (!navigator.stageDirectionsNotice().isEmpty()) {
            Label stageNotice = new Label(navigator.stageDirectionsNotice());
            stageNotice.setWrapText(true);
            stageNotice.getStyleClass().add("dialog-note");
            content.getChildren().add(stageNotice);
        }
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        navigator.styleDialog(dialog);
        if (dialog.showAndWait().filter(ButtonType.APPLY::equals).isPresent()) {
            navigator.setReadingFontScale(textSize.getValue());
            navigator.setCharacterArtworkEnabled(artwork.isSelected());
            navigator.setReducedMotion(reducedMotion.isSelected());
        }
    }

    @FXML private void audioSettings() { navigator.showAudioSettings(); }

    @FXML private void characterSettings() {
        try { navigator.showAppearanceSelection(); }
        catch (IOException failure) { navigator.notice("Appearance unavailable", "Please try again."); }
    }

    @FXML private void quit() { navigator.requestQuit(); }

    private boolean allowDiscard(String title) {
        return !navigator.backend().accountGameService().hasUnsavedProgress()
                || navigator.confirm(title, "Unsaved progress will be lost. Continue?");
    }

    private void showGameplay() {
        try { navigator.showCurrentGame(); }
        catch (IOException failure) { navigator.notice("Game screen unavailable", "The game screen could not open."); }
    }

    private Dialog<Void> dialog(String title, String header) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(680);
        navigator.styleDialog(dialog);
        return dialog;
    }

    private <T> void run(String action, Supplier<T> operation, Consumer<T> success) {
        run(action, operation, success, failure -> {
            // Repository/SQL exception messages may include connection details; do not display them.
            String message;
            if (failure instanceof ConfirmationRequiredException)
                message = "This action needs confirmation. Reopen Save/Load and try again.";
            else if (failure instanceof UnsupportedOperationException)
                message = "The requested story action is not available. Your existing progress is unchanged.";
            else if (failure instanceof IllegalArgumentException)
                message = "The selected chapter or checkpoint is not valid for this action. Your existing progress is unchanged.";
            else if (failure instanceof IllegalStateException)
                message = "The selected progress is not available for this action. Check your account or chapter state.";
            else message = "The operation failed. Your previous progress has not been replaced.";
            navigator.notice(action + " unavailable", message);
        });
    }

    private <T> void run(String action, Supplier<T> operation, Consumer<T> success, Consumer<Throwable> failure) {
        if (navigator.workInProgress()) return;
        navigator.setWorkInProgress(true);
        menuOptions.setDisable(true);
        accountActions.setDisable(true);
        Task<T> task = new Task<>() {
            @Override protected T call() { return operation.get(); }
        };
        task.setOnSucceeded(event -> {
            finishTask();
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            finishTask();
            failure.accept(task.getException());
        });
        Thread worker = new Thread(task, "menu-" + action.replace(' ', '-').toLowerCase());
        worker.setDaemon(true);
        worker.start();
    }

    private void finishTask() {
        navigator.setWorkInProgress(false);
        menuOptions.setDisable(false);
        accountActions.setDisable(false);
    }

    private record MenuState(List<SaveSnapshot> slots, List<AssessmentResult> history, boolean activeGame) {}
}

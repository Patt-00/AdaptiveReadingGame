package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.AdaptiveReadingGameApp;
import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.content.StoryStageDirections;
import com.adaptivereadinggame.model.StoryNode;
import com.adaptivereadinggame.service.AccountGameService.ChapterSummary;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One application context and navigation boundary shared by all Dev 1 screens. */
public final class AppNavigator {
    public record StoryLogEntry(String nodeId, String speaker, String text, boolean thought) {}
    record StageDirectionsLoad(StoryStageDirections directions, String notice) {}
    private static final int STORY_LOG_LIMIT = 500;
    private final Stage stage;
    private final BackendContext backend;
    private final String storageDescription;
    private final StoryStageDirections stageDirections;
    private final String stageDirectionsNotice;
    private final boolean georgiaMissing = Font.getFamilies().stream()
            .noneMatch(family -> family.equalsIgnoreCase("Georgia"));
    private final boolean timesMissing = Font.getFamilies().stream()
            .noneMatch(family -> family.equalsIgnoreCase("Times New Roman"));
    private boolean closing;
    private boolean workInProgress;
    private double readingFontScale = 1.0;
    private boolean characterArtworkEnabled = true;
    private boolean reducedMotion;
    private final List<StoryLogEntry> storyLog = new ArrayList<>();
    private UUID storyLogAccount;
    private UUID storyLogChapter;
    private ChapterSummary pendingChapter;

    public AppNavigator(Stage stage, BackendContext backend, String storageDescription) {
        this(stage, backend, storageDescription, loadStageDirections());
    }

    public AppNavigator(Stage stage, BackendContext backend, String storageDescription,
                        StoryStageDirections stageDirections) {
        this(stage, backend, storageDescription, new StageDirectionsLoad(Objects.requireNonNull(stageDirections), ""));
    }

    private AppNavigator(Stage stage, BackendContext backend, String storageDescription,
                         StageDirectionsLoad stageLoad) {
        this.stage = Objects.requireNonNull(stage);
        this.backend = Objects.requireNonNull(backend);
        this.storageDescription = Objects.requireNonNull(storageDescription);
        this.stageDirections = stageLoad.directions();
        this.stageDirectionsNotice = stageLoad.notice();
    }

    public BackendContext backend() { return backend; }
    public Stage stage() { return stage; }
    public String storageDescription() { return storageDescription; }
    public StoryStageDirections stageDirections() { return stageDirections; }
    public String stageDirectionsNotice() { return stageDirectionsNotice; }
    public boolean workInProgress() { return workInProgress; }
    public void setWorkInProgress(boolean busy) { workInProgress = busy; }
    public double readingFontScale() { return readingFontScale; }
    public void setReadingFontScale(double scale) {
        if (scale < 0.8 || scale > 1.6) throw new IllegalArgumentException("Reading text scale must be 0.8 to 1.6.");
        readingFontScale = scale;
    }
    public boolean characterArtworkEnabled() { return characterArtworkEnabled; }
    public void setCharacterArtworkEnabled(boolean enabled) { characterArtworkEnabled = enabled; }
    public boolean reducedMotion() { return reducedMotion; }
    public void setReducedMotion(boolean reduced) { reducedMotion = reduced; }
    public Optional<ChapterSummary> pendingChapter() { return Optional.ofNullable(pendingChapter); }

    public void clearStoryLog() {
        storyLog.clear();
        storyLogAccount = null;
        storyLogChapter = null;
    }

    public void recordStoryLine(UUID accountId, UUID chapterId, StoryNode node) {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(chapterId);
        Objects.requireNonNull(node);
        if (!accountId.equals(storyLogAccount) || !chapterId.equals(storyLogChapter)) {
            clearStoryLog();
            storyLogAccount = accountId;
            storyLogChapter = chapterId;
        }
        if (node.readingCheck() || node.storyComplete()) return;
        if (!storyLog.isEmpty() && storyLog.getLast().nodeId().equals(node.storyNodeId())) return;
        if (storyLog.size() == STORY_LOG_LIMIT) storyLog.removeFirst();
        storyLog.add(new StoryLogEntry(node.storyNodeId(), node.speakerName(), node.dialogueText(), node.thought()));
    }

    public List<StoryLogEntry> storyLog() { return List.copyOf(storyLog); }

    public String fontNotice() {
        if (timesMissing) return "Reference fonts unavailable: using system Serif.";
        return georgiaMissing ? "Georgia unavailable: body text uses Times New Roman." : "";
    }

    public void start() throws IOException {
        stage.setTitle("Adaptive Reading Game");
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.setOnCloseRequest(event -> {
            if (!closing) {
                event.consume();
                requestQuit();
            }
        });
        showAccount();
        stage.show();
        if (!fontNotice().isEmpty()) System.err.println(fontNotice());
    }

    public void showAccount() throws IOException { clearStoryLog(); show("account.fxml"); }
    public void showMenu() throws IOException { show("main-menu.fxml"); }
    public void showGameplay() throws IOException { show("gameplay.fxml"); }
    public void showCurrentGame() throws IOException { showGameplay(); }
    public void showResults() throws IOException { show("results.fxml"); }
    public void showAnalysis() throws IOException { show("analysis.fxml"); }
    public void showInstructions(ChapterSummary chapter) throws IOException {
        pendingChapter = Objects.requireNonNull(chapter);
        show("how-to-play.fxml");
    }
    public void clearPendingChapter() { pendingChapter = null; }

    public <T> void runAsync(String action, Supplier<T> operation, Consumer<T> success,
                             Consumer<Throwable> failure) {
        if (workInProgress) return;
        setWorkInProgress(true);
        Task<T> task = new Task<>() {
            @Override protected T call() { return operation.get(); }
        };
        task.setOnSucceeded(event -> {
            setWorkInProgress(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setWorkInProgress(false);
            failure.accept(task.getException());
        });
        Thread worker = new Thread(task, "game-" + action.replace(' ', '-').toLowerCase(java.util.Locale.ROOT));
        worker.setDaemon(true);
        worker.start();
    }

    private void show(String resource) throws IOException {
        FXMLLoader loader = new FXMLLoader(AdaptiveReadingGameApp.class.getResource(
                "/com/adaptivereadinggame/view/" + resource));
        loader.setControllerFactory(type -> {
            if (type == AccountController.class) return new AccountController(this);
            if (type == MenuController.class) return new MenuController(this);
            if (type == GameplayController.class) return new GameplayController(this);
            if (type == ResultsController.class) return new ResultsController(this);
            if (type == AnalysisController.class) return new AnalysisController(this);
            if (type == InstructionsController.class) return new InstructionsController(this);
            throw new IllegalArgumentException("No controller registered for " + type.getName());
        });
        Parent root = loader.load();
        applyFontFallback(root);
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, 1280, 720);
            scene.getStylesheets().add(stylesheet());
            stage.setScene(scene);
        } else stage.getScene().setRoot(root);
    }

    public void styleDialog(Dialog<?> dialog) {
        dialog.initOwner(stage);
        dialog.getDialogPane().getStylesheets().add(stylesheet());
        dialog.getDialogPane().getStyleClass().add("reference-dialog");
        applyFontFallback(dialog.getDialogPane());
    }

    public boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(title);
        styleDialog(alert);
        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }

    public void notice(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        styleDialog(alert);
        alert.showAndWait();
    }

    public void requestQuit() {
        if (workInProgress) {
            notice("Please wait", "An account or save operation is still running. Please wait before closing.");
            return;
        }
        boolean signedIn = backend.accountGameService().currentAccount().isPresent();
        boolean unsaved = signedIn && backend.accountGameService().hasUnsavedProgress();
        String message = unsaved
                ? "Unsaved progress will be lost. Close Adaptive Reading Game?"
                : "Close Adaptive Reading Game?";
        if (!confirm("Quit the game?", message)) return;
        try {
            if (signedIn) backend.accountGameService().logout(true);
        } catch (RuntimeException cleanupFailure) {
            // The facade signs out in finally. Never expose storage or credential details.
            System.err.println("Session cleanup could not complete; the account has been signed out.");
        } finally {
            closing = true;
            stage.close();
        }
    }

    private void applyFontFallback(Parent root) {
        if (georgiaMissing) root.getStyleClass().add("georgia-fallback");
        if (timesMissing) root.getStyleClass().add("serif-fallback");
    }

    private String stylesheet() {
        return AdaptiveReadingGameApp.class.getResource(
                "/com/adaptivereadinggame/style/application.css").toExternalForm();
    }

    private static StageDirectionsLoad loadStageDirections() {
        return loadStageDirections(System.getenv("ARG_STAGE_DIRECTIONS"), Path.of("assets/content/stage-directions.json"));
    }

    static StageDirectionsLoad loadStageDirections(String configured, Path defaultPath) {
        boolean explicit = configured != null && !configured.isBlank();
        try {
            Path path = explicit ? Path.of(configured) : defaultPath;
            if (!Files.exists(path) && !explicit) return new StageDirectionsLoad(StoryStageDirections.empty(), "");
            return new StageDirectionsLoad(StoryStageDirections.read(path), "");
        } catch (IOException | RuntimeException unavailable) {
            System.err.println("Optional character staging could not be loaded; story dialogue remains available.");
            return new StageDirectionsLoad(StoryStageDirections.empty(),
                    "Character staging could not be loaded. The story remains available.");
        }
    }
}

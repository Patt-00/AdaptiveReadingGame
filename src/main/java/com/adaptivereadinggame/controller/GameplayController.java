package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.content.CharacterAssetResolver;
import com.adaptivereadinggame.content.StoryAssetResolver;
import com.adaptivereadinggame.content.StoryMusicCues;
import com.adaptivereadinggame.model.StoryNode;
import com.adaptivereadinggame.model.StoryChoice;
import com.adaptivereadinggame.service.AccountGameService;
import com.adaptivereadinggame.service.AssessmentService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Control;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import javafx.util.Duration;
import com.adaptivereadinggame.view.StoryArtworkPane;
import javafx.scene.control.ScrollPane;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Displays prepared story nodes and comprehension questions from the account-safe services. */
public final class GameplayController {
    private record LoadedGame(AccountGameService.GameView view,
                              List<AssessmentService.QuestionPrompt> questions) {}
    private record Feedback(AssessmentService.QuestionFeedback answer, AccountGameService.GameView view) {}

    private final AppNavigator navigator;
    private final CharacterAssetResolver assets = new CharacterAssetResolver();
    private final StoryAssetResolver storyAssets = new StoryAssetResolver();
    private final Map<Path, Image> characterImageCache = new HashMap<>();
    private final Map<Path, Image> sceneImageCache = new HashMap<>();
    private final Map<Path, Image> illustrationImageCache = new HashMap<>();
    private final ToggleGroup answerGroup = new ToggleGroup();
    private LoadedGame loaded;
    private AssessmentService.QuestionPrompt activeQuestion;
    private boolean showingFeedback;
    private boolean spaceHeld;
    private Boolean narrowStoryLayout;
    private FadeTransition storyPanelFade;
    private FadeTransition portraitFade;
    private FadeTransition sceneFade;
    private TranslateTransition portraitSlide;
    private Path currentScenePath;

    @FXML private StackPane gameplayRoot;
    @FXML private StackPane centerRoot;
    @FXML private HBox gameplayHeader;
    @FXML private VBox gameplayFooter;
    @FXML private StoryArtworkPane artworkLayer;
    @FXML private StackPane storyRow;
    @FXML private StackPane portraitFrame;
    @FXML private ScrollPane storyScroll;
    @FXML private ScrollPane dialogueScroll;
    @FXML private ScrollPane assessmentScroll;
    @FXML private ScrollPane passageScroll;
    @FXML private VBox storyPanel;
    @FXML private VBox portraitColumn;
    @FXML private VBox storyChoices;
    @FXML private VBox answerChoices;
    @FXML private VBox feedbackPane;
    @FXML private Label chapterTitle;
    @FXML private Label difficultyLabel;
    @FXML private Label speakerName;
    @FXML private Label speakerInitials;
    @FXML private Label storyModeLabel;
    @FXML private Label storyText;
    @FXML private Label passageText;
    @FXML private Label questionProgress;
    @FXML private Label questionPrompt;
    @FXML private Label feedbackHeading;
    @FXML private Label correctAnswerText;
    @FXML private Label explanationText;
    @FXML private Label footerText;
    @FXML private Label fontNotice;
    @FXML private ImageView speakerImage;
    @FXML private ImageView supportingImage;
    @FXML private Button storyNextButton;
    @FXML private Button submitButton;
    @FXML private Button nextQuestionButton;
    @FXML private Button illustrationButton;
    @FXML private Button storyLogButton;

    public GameplayController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        storyScroll.viewportBoundsProperty().addListener((observable, before, after) -> updateStoryLayout(after.getWidth()));
        gameplayRoot.heightProperty().addListener((observable, before, after) -> {
            if (Boolean.TRUE.equals(narrowStoryLayout)) updatePortraitImageSizes();
            if (storyScroll.isVisible()) sizeDialogueViewport();
        });
        storyPanel.widthProperty().addListener((observable, before, after) -> {
            if (storyScroll.isVisible() && after.doubleValue() > 0) sizeDialogueViewport();
        });
        updateStoryLayout(1280);
        gameplayRoot.setFocusTraversable(true);
        gameplayRoot.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (!navigator.workInProgress()) openMenu();
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE
                    && !(gameplayRoot.getScene().getFocusOwner() instanceof Control)) {
                if (!spaceHeld && !navigator.workInProgress()) {
                    spaceHeld = true;
                    if (showingFeedback) nextQuestion();
                    else if (storyScroll.isVisible() && storyNextButton.isVisible()) advanceStory();
                }
                event.consume();
            }
        });
        EventHandler<KeyEvent> releaseShortcut = event -> {
            if (event.getCode() == KeyCode.SPACE) spaceHeld = false;
        };
        // A release can arrive while the content is disabled by an asynchronous action.
        gameplayRoot.sceneProperty().addListener((observable, previous, current) -> {
            if (previous != null) previous.removeEventFilter(KeyEvent.KEY_RELEASED, releaseShortcut);
            if (current != null) current.addEventFilter(KeyEvent.KEY_RELEASED, releaseShortcut);
        });
        fontNotice.setText(navigator.fontNotice());
        reload();
    }

    private void updateStoryLayout(double width) {
        boolean narrow = width < 900;
        if (narrowStoryLayout != null && narrowStoryLayout == narrow) return;
        narrowStoryLayout = narrow;
        if (!storyRow.getChildren().isEmpty() && storyRow.getChildren().getFirst() instanceof Pane previous
                && previous != portraitColumn) previous.getChildren().clear();
        storyRow.getChildren().clear();
        Pane row;
        if (narrow) {
            VBox column = new VBox(16, portraitColumn, storyPanel);
            column.setAlignment(Pos.CENTER);
            row = column;
        } else {
            HBox line = new HBox(24, portraitColumn, storyPanel);
            line.setAlignment(Pos.BOTTOM_CENTER);
            line.setFillHeight(false);
            HBox.setHgrow(storyPanel, Priority.ALWAYS);
            row = line;
        }
        row.setMinWidth(0);
        storyRow.getChildren().setAll(row);
        updatePortraitImageSizes();
    }

    private void updatePortraitImageSizes() {
        boolean narrow = Boolean.TRUE.equals(narrowStoryLayout);
        double portraitWidth = narrow ? 240 : 320;
        double portraitHeight = narrow ? Math.clamp(gameplayRoot.getHeight() * 0.29, 140, 220) : 460;
        portraitFrame.setPrefWidth(portraitWidth);
        portraitFrame.setMaxWidth(portraitWidth);
        portraitFrame.setPrefHeight(portraitHeight);
        boolean twoCharacters = supportingImage.isVisible() && speakerImage.isVisible();
        speakerImage.setFitWidth(twoCharacters ? portraitWidth * 0.68 : portraitWidth);
        speakerImage.setFitHeight(portraitHeight);
        supportingImage.setFitWidth(portraitWidth * 0.68);
        supportingImage.setFitHeight(portraitHeight * 0.94);
        StackPane.setAlignment(speakerImage, twoCharacters ? Pos.BOTTOM_RIGHT : Pos.CENTER);
        StackPane.setAlignment(supportingImage, Pos.BOTTOM_LEFT);
    }

    private LoadedGame loadGame() {
        AccountGameService game = navigator.backend().accountGameService();
        var view = game.currentGameView().orElseThrow(() -> new IllegalStateException("No chapter is active."));
        int skippedCompletedCheckpoints = 0;
        while (view.completedResult().isEmpty() && view.state().assessmentSessionId() == null
                && view.storyNode().readingCheck()) {
            if (game.advanceCompletedCheckpointIfPresent()) {
                if (++skippedCompletedCheckpoints > 20)
                    throw new IllegalStateException("Too many saved checkpoint results form an invalid story path.");
                view = game.currentGameView().orElseThrow(() -> new IllegalStateException("The story could not resume."));
                continue;
            }
            game.startAssessment(view.chapter().id());
            view = game.currentGameView().orElseThrow(() -> new IllegalStateException("The reading check could not be opened."));
        }
        if (view.completedResult().isPresent()) return new LoadedGame(view, List.of());
        List<AssessmentService.QuestionPrompt> questions = view.state().assessmentSessionId() == null
                ? List.of() : game.questions(view.state().assessmentSessionId());
        return new LoadedGame(view, questions);
    }

    private void reload() {
        gameplayRoot.setDisable(true);
        navigator.runAsync("Load gameplay", this::loadGame, data -> {
            gameplayRoot.setDisable(false);
            loaded = data;
            if (data.view().completedResult().isPresent()) {
                openResults();
                return;
            }
            chapterTitle.setText(data.view().chapter().title());
            difficultyLabel.setText(data.view().chapter().difficulty().name());
            if (data.view().state().assessmentSessionId() == null) showStory(data.view().storyNode());
            else showAssessment(data.view(), data.questions());
        }, failure -> {
            gameplayRoot.setDisable(false);
            showLoadFailure(failure);
        });
    }

    private void showStory(StoryNode node) {
        navigator.audio().setTrack(StoryMusicCues.forNode(loaded.view().state().chapterId(), node));
        setStoryPresentation(true);
        storyScroll.setVisible(true);
        storyScroll.setManaged(true);
        assessmentScroll.setVisible(false);
        assessmentScroll.setManaged(false);
        storyChoices.getChildren().clear();
        storyScroll.setVvalue(0);
        dialogueScroll.setVvalue(0);
        storyPanel.getStyleClass().remove("thought-panel");
        if (node.thought()) storyPanel.getStyleClass().add("thought-panel");
        storyModeLabel.setText(node.thought() ? "THOUGHT" : node.speakerName().isBlank() ? "NARRATION" : "DIALOGUE");
        speakerName.setText(node.speakerName().isBlank() ? "Narration" : node.speakerName());
        speakerName.setVisible(!node.speakerName().isBlank());
        speakerName.setManaged(!node.speakerName().isBlank());
        speakerInitials.setText(initials(node.speakerName()));
        boolean showCg = !node.cgAssetKey().isBlank();
        boolean showPortrait = !showCg && !node.speakerName().isBlank();
        portraitColumn.setVisible(showPortrait);
        portraitColumn.setManaged(showPortrait);
        storyPanel.getStyleClass().remove("cg-caption-panel");
        storyPanel.setPrefWidth(showPortrait ? 760 : 980);
        storyPanel.setMaxWidth(showPortrait ? 800 : 980);
        storyText.setMaxWidth(showPortrait ? 700 : 920);
        if (showCg) storyPanel.getStyleClass().add("cg-caption-panel");
        showSceneAssets(node);
        showCharacterImage(node);
        storyText.setText(node.dialogueText());
        navigator.recordStoryLine(loaded.view().state().studentId(), loaded.view().state().chapterId(), node);
        applyReadingFontScale();
        sizeDialogueViewport();
        animateStoryPanelEntrance();

        if (!node.storyChoices().isEmpty()) {
            storyNextButton.setVisible(false);
            storyNextButton.setManaged(false);
            gameplayFooter.setVisible(false);
            gameplayFooter.setManaged(false);
            for (StoryChoice choice : node.storyChoices()) {
                Button button = new Button(choice.choiceText());
                button.setId("story-choice-" + choice.choiceId());
                button.setAccessibleText(choice.choiceText());
                button.setMaxWidth(Double.MAX_VALUE);
                button.setWrapText(true);
                button.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                button.getStyleClass().add("story-choice-button");
                button.setOnAction(event -> chooseStory(choice.choiceId()));
                storyChoices.getChildren().add(button);
            }
        } else {
            gameplayFooter.setVisible(true);
            gameplayFooter.setManaged(true);
            storyNextButton.setText("NEXT");
            storyNextButton.setVisible(true);
            storyNextButton.setManaged(true);
            storyNextButton.setDisable(node.readingCheck() || node.nextNodeId().isEmpty());
        }
        gameplayRoot.requestFocus();
    }

    private void showCharacterImage(StoryNode node) {
        Image previous = speakerImage.getImage();
        Image active = null;
        Image supporting = null;
        String supportingName = "";
        try {
            if (navigator.characterArtworkEnabled() && node.cgAssetKey().isBlank()) {
                Path root = Path.of(System.getenv().getOrDefault("ARG_ASSET_DIR", "assets"));
                String activeKey = "You".equals(node.speakerName())
                        ? navigator.playerAppearance().map(com.adaptivereadinggame.model.PlayerAppearance::assetKey).orElse("")
                        : node.speakerAssetKey();
                active = characterImage(activeKey, root);
                var direction = navigator.stageDirections().forNode(loaded.view().state().chapterId(), node.storyNodeId());
                if (active != null && !node.speakerName().isBlank() && direction.isPresent()) {
                    supporting = characterImage(direction.get().assetKey(), root);
                    supportingName = direction.get().name();
                }
            }
        } catch (RuntimeException invalidAssetDirectory) {
            // Missing or invalid optional artwork falls back to the speaker's initials.
        }
        if (supporting == active) supporting = null;
        speakerImage.setImage(active);
        speakerImage.setAccessibleText(node.speakerName());
        speakerImage.setVisible(active != null);
        speakerImage.setManaged(active != null);
        supportingImage.setImage(supporting);
        supportingImage.setAccessibleText(supporting == null ? "" : "Supporting character: " + supportingName);
        supportingImage.setVisible(supporting != null);
        supportingImage.setManaged(supporting != null);
        speakerInitials.setVisible(active == null && !node.speakerName().isBlank());
        speakerInitials.setManaged(active == null && !node.speakerName().isBlank());
        updatePortraitImageSizes();
        if (active != null && active != previous) animatePortraitEntrance();
        else if (active == null) {
            if (portraitFade != null) portraitFade.stop();
            if (portraitSlide != null) portraitSlide.stop();
            speakerImage.setOpacity(1);
            speakerImage.setTranslateX(0);
        }
    }

    private Image characterImage(String key, Path root) {
        return assets.findCharacterImage(key, root).map(path -> {
            Image image = characterImageCache.computeIfAbsent(path,
                    item -> new Image(item.toUri().toString(), 640, 900, true, true, false));
            return image.isError() ? null : image;
        }).orElse(null);
    }

    private void animatePortraitEntrance() {
        if (portraitFade != null) portraitFade.stop();
        if (portraitSlide != null) portraitSlide.stop();
        if (navigator.reducedMotion()) {
            speakerImage.setOpacity(1);
            speakerImage.setTranslateX(0);
            return;
        }
        speakerImage.setOpacity(0.72);
        speakerImage.setTranslateX(14);
        portraitFade = new FadeTransition(Duration.millis(180), speakerImage);
        portraitFade.setFromValue(0.72);
        portraitFade.setToValue(1.0);
        portraitFade.play();
        portraitSlide = new TranslateTransition(Duration.millis(180), speakerImage);
        portraitSlide.setFromX(14);
        portraitSlide.setToX(0);
        portraitSlide.play();
    }

    private void animateStoryPanelEntrance() {
        if (storyPanelFade != null) storyPanelFade.stop();
        if (navigator.reducedMotion()) {
            storyPanel.setOpacity(1);
            return;
        }
        storyPanel.setOpacity(0.88);
        storyPanelFade = new FadeTransition(Duration.millis(150), storyPanel);
        storyPanelFade.setFromValue(0.88);
        storyPanelFade.setToValue(1.0);
        storyPanelFade.play();
    }

    private void sizeDialogueViewport() {
        storyText.applyCss();
        double panelWidth = storyPanel.getWidth() > 0 ? storyPanel.getWidth()
                : storyPanel.getPrefWidth();
        double availableTextWidth = Math.max(120, Math.min(storyText.getMaxWidth(), panelWidth - 76));
        double neededHeight = storyText.prefHeight(availableTextWidth) + 12;
        double stageHeight = gameplayRoot.getHeight() > 0 ? gameplayRoot.getHeight() : 720;
        double maximumViewportHeight = Math.clamp(stageHeight * 0.40, 180, 280);
        double viewportHeight = Math.max(Math.min(120, maximumViewportHeight),
                Math.min(maximumViewportHeight, neededHeight));
        dialogueScroll.setPrefViewportHeight(viewportHeight);
        dialogueScroll.setMaxHeight(viewportHeight);
    }

    private void showSceneAssets(StoryNode node) {
        Path scenePath = null;
        Path illustrationPath = null;
        try {
            Path root = Path.of(System.getenv().getOrDefault("ARG_ASSET_DIR", "assets"));
            if (!node.sceneAssetKey().isBlank()) {
                scenePath = storyAssets.findSceneImage(node.sceneAssetKey(), root).orElse(null);
            }
            if (!node.cgAssetKey().isBlank()) {
                illustrationPath = storyAssets.findCgImage(node.cgAssetKey(), root).orElse(null);
            }
        } catch (RuntimeException invalidAssetDirectory) {
            // Optional art never prevents the story text from being displayed.
        }
        Image scene = loadOptionalStoryImage(sceneImageCache, scenePath);
        Path nextScenePath = scene == null ? null : scenePath;
        if (!Objects.equals(currentScenePath, nextScenePath)) {
            artworkLayer.setSceneImage(scene);
            currentScenePath = nextScenePath;
            animateSceneEntrance(scene != null);
        }
        Image illustration = loadOptionalStoryImage(illustrationImageCache, illustrationPath);
        artworkLayer.setIllustration(illustration);
        illustrationButton.setVisible(illustration != null);
        illustrationButton.setManaged(illustration != null);
    }

    private Image loadOptionalStoryImage(Map<Path, Image> cache, Path path) {
        if (path == null) return null;
        try {
            Image image = cache.computeIfAbsent(path,
                    item -> new Image(item.toUri().toString(), 1280, 720, true, true, false));
            return image.isError() ? null : image;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private void animateSceneEntrance(boolean hasScene) {
        if (sceneFade != null) sceneFade.stop();
        artworkLayer.setOpacity(1);
        if (!hasScene || navigator.reducedMotion()) return;
        sceneFade = new FadeTransition(Duration.millis(180), artworkLayer);
        sceneFade.setFromValue(0.82);
        sceneFade.setToValue(1.0);
        sceneFade.play();
    }

    private void clearSceneAssets() {
        if (sceneFade != null) sceneFade.stop();
        artworkLayer.setOpacity(1);
        artworkLayer.clear();
        illustrationButton.setVisible(false);
        illustrationButton.setManaged(false);
        currentScenePath = null;
    }

    @FXML private void viewIllustration() {
        Image image = artworkLayer.illustrationImage();
        if (image == null || navigator.workInProgress()) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Story illustration");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ImageView fullImage = new ImageView(image);
        fullImage.setPreserveRatio(true);
        fullImage.setFitWidth(navigator.stage().getWidth() * 0.8);
        fullImage.setFitHeight(navigator.stage().getHeight() * 0.7);
        fullImage.setAccessibleText("Full story illustration");
        dialog.getDialogPane().setContent(new StackPane(fullImage));
        navigator.styleDialog(dialog);
        dialog.showAndWait();
    }

    @FXML private void openStoryLog() {
        if (navigator.workInProgress()) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Story log");
        dialog.setHeaderText("Lines read in this session");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(700);
        VBox entries = new VBox(12);
        Label note = new Label("The latest 500 story lines read in this session appear here. Loading a save starts a new log.");
        note.setWrapText(true);
        note.getStyleClass().add("dialog-note");
        note.setStyle("-fx-font-size: " + (14 * navigator.readingFontScale()) + "px;");
        entries.getChildren().add(note);
        if (navigator.storyLog().isEmpty()) {
            Label empty = new Label("No story lines have been read in this session yet.");
            empty.setWrapText(true);
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.getStyleClass().add("dialog-empty-state");
            entries.getChildren().add(empty);
        }
        for (AppNavigator.StoryLogEntry entry : navigator.storyLog()) {
            String speaker = entry.speaker().isBlank() ? "NARRATION" : entry.speaker();
            if (entry.thought()) speaker += " - THOUGHT";
            Label heading = new Label(speaker);
            heading.getStyleClass().add("story-log-speaker");
            heading.setStyle("-fx-font-size: " + (16 * navigator.readingFontScale()) + "px;");
            Label text = new Label(entry.text());
            text.setWrapText(true);
            text.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            text.getStyleClass().add("story-log-text");
            text.setStyle("-fx-font-size: " + (18 * navigator.readingFontScale()) + "px;");
            VBox row = new VBox(5, heading, text);
            row.getStyleClass().add("history-record");
            entries.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(entries);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        double logViewportLimit = Math.min(380, navigator.stage().getHeight() * 0.55);
        scroll.setPrefViewportHeight(Math.min(logViewportLimit, Math.max(150, entries.prefHeight(660))));
        scroll.setAccessibleText("Story lines read in this session");
        dialog.getDialogPane().setContent(scroll);
        navigator.styleDialog(dialog);
        dialog.setOnShown(event -> javafx.application.Platform.runLater(() -> scroll.setVvalue(scroll.getVmax())));
        dialog.showAndWait();
    }

    private void setStoryPresentation(boolean story) {
        setStyleClass(gameplayRoot, "story-screen", story);
        setStyleClass(centerRoot, "story-stage", story);
        setStyleClass(gameplayHeader, "story-header", story);
        setStyleClass(gameplayFooter, "story-footer", story);
        storyLogButton.setVisible(true);
        storyLogButton.setManaged(true);
        footerText.setVisible(!story);
        footerText.setManaged(!story);
        fontNotice.setVisible(!story && !fontNotice.getText().isEmpty());
        fontNotice.setManaged(!story && !fontNotice.getText().isEmpty());
        gameplayFooter.setAlignment(story ? Pos.CENTER_RIGHT : Pos.CENTER);
        gameplayFooter.setVisible(true);
        gameplayFooter.setManaged(true);
    }

    private static void setStyleClass(javafx.scene.Node node, String style, boolean enabled) {
        node.getStyleClass().remove(style);
        if (enabled) node.getStyleClass().add(style);
    }

    private void showAssessment(AccountGameService.GameView view,
                                List<AssessmentService.QuestionPrompt> questions) {
        navigator.audio().setTrack(com.adaptivereadinggame.audio.GameAudio.Track.SILENT);
        setStoryPresentation(false);
        clearSceneAssets();
        storyScroll.setVisible(false);
        storyScroll.setManaged(false);
        storyNextButton.setVisible(false);
        storyNextButton.setManaged(false);
        assessmentScroll.setVvalue(0);
        assessmentScroll.setVisible(true);
        assessmentScroll.setManaged(true);
        footerText.setText("No timer. Only your first submitted answer counts toward the score.");
        setPassagePreview(false);
        passageScroll.setVvalue(0);
        passageText.setText(view.passage());
        int index = view.state().questionIndex();
        answerChoices.getChildren().clear();
        feedbackPane.setVisible(false);
        feedbackPane.setManaged(false);
        submitButton.setVisible(false);
        submitButton.setManaged(false);
        nextQuestionButton.setVisible(false);
        nextQuestionButton.setManaged(false);
        showingFeedback = false;
        answerGroup.getToggles().clear();

        if (index >= questions.size()) {
            activeQuestion = null;
            questionProgress.setText("All questions answered");
            questionPrompt.setText("Your first answers are recorded and ready for results.");
            nextQuestionButton.setVisible(true);
            nextQuestionButton.setManaged(true);
            nextQuestionButton.setText("VIEW RESULTS");
            nextQuestionButton.setAccessibleHelp("Calculate and display the stored comprehension result.");
            return;
        }

        activeQuestion = questions.get(index);
        questionProgress.setText("Question " + (index + 1) + " of " + questions.size()
                + "  ·  " + view.chapter().difficulty().name());
        questionPrompt.setText(activeQuestion.prompt());
        for (int option = 0; option < activeQuestion.choices().size(); option++) {
            RadioButton choice = new RadioButton(letter(option) + ".  " + activeQuestion.choices().get(option));
            choice.setId("answer-" + option);
            choice.setAccessibleText("Choice " + letter(option) + ": " + activeQuestion.choices().get(option));
            choice.setWrapText(true);
            choice.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            choice.setMaxWidth(Double.MAX_VALUE);
            choice.setUserData(option);
            choice.setToggleGroup(answerGroup);
            choice.getStyleClass().add("quiz-option");
            choice.setOnAction(event -> {
                boolean unanswered = answerGroup.getSelectedToggle() == null;
                submitButton.setDisable(unanswered);
                submitButton.setAccessibleHelp(unanswered
                        ? "Choose an answer before submitting. Only your first submission counts."
                        : "Submit this answer to receive feedback. Only the first submission counts.");
            });
            answerChoices.getChildren().add(choice);
        }
        submitButton.setDisable(true);
        submitButton.setAccessibleHelp("Choose an answer before submitting. Only your first submission counts.");
        submitButton.setVisible(true);
        submitButton.setManaged(true);
        nextQuestionButton.setText("NEXT QUESTION");
        nextQuestionButton.setAccessibleHelp("Continue after the feedback is shown.");
        applyReadingFontScale();
    }

    @FXML private void submitAnswer() {
        if (loaded == null || activeQuestion == null) return;
        Toggle selected = answerGroup.getSelectedToggle();
        if (selected == null) return;
        int option = (int) selected.getUserData();
        UUID sessionId = loaded.view().state().assessmentSessionId();
        UUID questionId = activeQuestion.id();
        runGameTask("Submit answer", () -> {
            navigator.backend().accountGameService().submit(sessionId, questionId, option);
            var feedback = navigator.backend().accountGameService().feedback(sessionId, questionId);
            var view = navigator.backend().accountGameService().currentGameView().orElseThrow();
            return new Feedback(feedback, view);
        }, this::showFeedback);
    }

    private void showFeedback(Feedback data) {
        loaded = new LoadedGame(data.view(), loaded.questions());
        setPassagePreview(true);
        AssessmentService.QuestionFeedback feedback = data.answer();
        answerChoices.getChildren().forEach(node -> node.setDisable(true));
        submitButton.setVisible(false);
        submitButton.setManaged(false);
        feedbackHeading.setText(feedback.correct() ? "CORRECT" : "NOT QUITE");
        boolean showCorrectAnswer = !feedback.correct() && !feedback.correctAnswer().isBlank();
        correctAnswerText.setText(showCorrectAnswer ? "Correct answer: " + feedback.correctAnswer() : "");
        correctAnswerText.setVisible(showCorrectAnswer);
        correctAnswerText.setManaged(showCorrectAnswer);
        String explanation = feedback.explanation().isBlank()
                ? "Your first answer has been recorded. Continue when you are ready."
                : feedback.explanation();
        explanationText.setText(explanation);
        feedbackPane.setVisible(true);
        feedbackPane.setManaged(true);
        showingFeedback = true;
        int index = loaded.view().state().questionIndex();
        nextQuestionButton.setText(index >= loaded.questions().size() ? "VIEW RESULTS" : "NEXT QUESTION");
        nextQuestionButton.setVisible(true);
        nextQuestionButton.setManaged(true);
        nextQuestionButton.setAccessibleHelp(index + 1 >= loaded.questions().size()
                ? "Complete the reading check and view your results."
                : "Continue to the next reading-check question.");
        javafx.application.Platform.runLater(() -> {
            assessmentScroll.applyCss();
            assessmentScroll.layout();
            scrollFeedbackIntoView();
        });
    }

    private void scrollFeedbackIntoView() {
        javafx.scene.Node content = assessmentScroll.getContent();
        double viewportHeight = assessmentScroll.getViewportBounds().getHeight();
        double scrollableHeight = content == null ? 0 : content.getBoundsInLocal().getHeight() - viewportHeight;
        if (content == null || scrollableHeight <= 1) {
            assessmentScroll.setVvalue(assessmentScroll.getVmin());
            return;
        }

        javafx.geometry.Bounds feedbackBounds = content.sceneToLocal(
                feedbackPane.localToScene(feedbackPane.getBoundsInLocal()));
        javafx.geometry.Bounds nextButtonBounds = content.sceneToLocal(
                nextQuestionButton.localToScene(nextQuestionButton.getBoundsInLocal()));
        double focusTop = Math.min(feedbackBounds.getMinY(), nextButtonBounds.getMinY());
        double focusBottom = Math.max(feedbackBounds.getMaxY(), nextButtonBounds.getMaxY());
        Toggle selected = answerGroup.getSelectedToggle();
        if (selected instanceof javafx.scene.Node selectedNode) {
            javafx.geometry.Bounds answerBounds = content.sceneToLocal(
                    selectedNode.localToScene(selectedNode.getBoundsInLocal()));
            focusTop = Math.min(focusTop, answerBounds.getMinY());
        }

        double valueRange = assessmentScroll.getVmax() - assessmentScroll.getVmin();
        double currentTop = valueRange <= 0 ? 0
                : (assessmentScroll.getVvalue() - assessmentScroll.getVmin()) / valueRange * scrollableHeight;
        double targetTop = currentTop;
        if (focusBottom - focusTop > viewportHeight || focusBottom > currentTop + viewportHeight) {
            targetTop = focusBottom - viewportHeight;
        }
        if (focusTop < targetTop && focusBottom - focusTop <= viewportHeight) targetTop = focusTop;
        targetTop = Math.clamp(targetTop, 0, scrollableHeight);
        assessmentScroll.setVvalue(assessmentScroll.getVmin() + targetTop / scrollableHeight * valueRange);
    }

    private void setPassagePreview(boolean compact) {
        double viewportHeight = compact ? 65 : 125;
        passageScroll.setPrefViewportHeight(viewportHeight);
        passageScroll.setMaxHeight(compact ? 75 : 145);
    }

    @FXML private void nextQuestion() {
        if (navigator.workInProgress()) return;
        if (!showingFeedback && activeQuestion != null) return;
        if (loaded != null && loaded.view().state().questionIndex() >= loaded.questions().size()) openAnalysis();
        else reload();
    }

    @FXML private void advanceStory() {
        if (loaded == null || navigator.workInProgress()) return;
        runGameTask("Advance story", () -> {
            navigator.backend().accountGameService().advanceStory();
            return loadGame();
        }, data -> { loaded = data; renderLoaded(data); });
    }

    private void chooseStory(String choiceId) {
        if (navigator.workInProgress()) return;
        runGameTask("Choose story path", () -> {
            navigator.backend().accountGameService().chooseStory(choiceId);
            return loadGame();
        }, data -> { loaded = data; renderLoaded(data); });
    }

    private void renderLoaded(LoadedGame data) {
        if (data.view().completedResult().isPresent()) { openResults(); return; }
        chapterTitle.setText(data.view().chapter().title());
        difficultyLabel.setText(data.view().chapter().difficulty().name());
        if (data.view().state().assessmentSessionId() == null) showStory(data.view().storyNode());
        else showAssessment(data.view(), data.questions());
    }

    private <T> void runGameTask(String action, java.util.function.Supplier<T> operation, Consumer<T> success) {
        if (navigator.workInProgress()) return;
        gameplayRoot.setDisable(true);
        navigator.runAsync(action, operation, value -> {
            gameplayRoot.setDisable(false);
            success.accept(value);
        }, failure -> {
            gameplayRoot.setDisable(false);
            showLoadFailure(failure);
        });
    }

    @FXML private void openMenu() {
        if (navigator.workInProgress()) return;
        try { navigator.showMenu(); }
        catch (IOException failure) { navigator.notice("Menu unavailable", "The main menu could not open."); }
    }

    private void openAnalysis() {
        try { navigator.showAnalysis(); }
        catch (IOException failure) { navigator.notice("Analysis unavailable", "The reading result could not be opened."); }
    }

    private void openResults() {
        try { navigator.showResults(); }
        catch (IOException failure) { navigator.notice("Results unavailable", "Open Reading History from the main menu."); }
    }

    private void showLoadFailure(Throwable failure) {
        String message = failure instanceof IllegalStateException || failure instanceof IllegalArgumentException
                ? failure.getMessage() : "The requested game action could not be completed. Stored progress has not been replaced.";
        navigator.notice("Game action unavailable", message == null ? "The game action could not be completed." : message);
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "";
        return name.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    private static String letter(int index) {
        return index < 26 ? String.valueOf((char) ('A' + index)) : Integer.toString(index + 1);
    }

    private void applyReadingFontScale() {
        double scale = navigator.readingFontScale();
        questionPrompt.setStyle("-fx-font-size: " + (26 * scale) + "px;");
        passageText.setStyle("-fx-font-size: " + (20 * scale) + "px;");
        storyText.setStyle("-fx-font-size: " + (25 * scale) + "px;");
        answerChoices.getChildren().forEach(node -> node.setStyle("-fx-font-size: " + (21 * scale) + "px;"));
    }

}

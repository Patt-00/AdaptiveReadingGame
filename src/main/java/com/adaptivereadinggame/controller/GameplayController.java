package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.content.CharacterAssetResolver;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.ScrollPane;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Displays prepared story nodes and comprehension questions from the account-safe services. */
public final class GameplayController {
    private record LoadedGame(AccountGameService.GameView view,
                              List<AssessmentService.QuestionPrompt> questions) {}
    private record Feedback(AssessmentService.QuestionFeedback answer, AccountGameService.GameView view) {}

    private final AppNavigator navigator;
    private final CharacterAssetResolver assets = new CharacterAssetResolver();
    private final ToggleGroup answerGroup = new ToggleGroup();
    private LoadedGame loaded;
    private AssessmentService.QuestionPrompt activeQuestion;
    private boolean showingFeedback;

    @FXML private BorderPane gameplayRoot;
    @FXML private ScrollPane storyScroll;
    @FXML private ScrollPane assessmentScroll;
    @FXML private VBox storyPanel;
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
    @FXML private Button storyNextButton;
    @FXML private Button submitButton;
    @FXML private Button nextQuestionButton;

    public GameplayController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        gameplayRoot.setFocusTraversable(true);
        gameplayRoot.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                openMenu();
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE && showingFeedback) {
                nextQuestion();
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE && storyScroll.isVisible() && storyNextButton.isVisible()) {
                advanceStory();
                event.consume();
            }
        });
        fontNotice.setText(navigator.fontNotice());
        reload();
    }

    private LoadedGame loadGame() {
        AccountGameService game = navigator.backend().accountGameService();
        var view = game.currentGameView().orElseThrow(() -> new IllegalStateException("No chapter is active."));
        if (view.completedResult().isPresent()) return new LoadedGame(view, List.of());
        if (view.state().assessmentSessionId() == null && view.storyNode().readingCheck()) {
            game.startAssessment(view.chapter().id());
            view = game.currentGameView().orElseThrow(() -> new IllegalStateException("The reading check could not be opened."));
        }
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
        storyScroll.setVisible(true);
        storyScroll.setManaged(true);
        assessmentScroll.setVisible(false);
        assessmentScroll.setManaged(false);
        storyChoices.getChildren().clear();
        storyPanel.getStyleClass().remove("thought-panel");
        if (node.thought()) storyPanel.getStyleClass().add("thought-panel");
        storyModeLabel.setText(node.thought() ? "THOUGHT" : node.speakerName().isBlank() ? "NARRATION" : "DIALOGUE");
        speakerName.setText(node.speakerName().isBlank() ? "Narration" : node.speakerName());
        speakerInitials.setText(initials(node.speakerName()));
        showCharacterImage(node);
        storyText.setText(node.dialogueText());
        footerText.setText("Story choices change the narrative. They do not affect your reading score.");
        applyReadingFontScale();

        if (!node.storyChoices().isEmpty()) {
            storyNextButton.setVisible(false);
            storyNextButton.setManaged(false);
            for (StoryChoice choice : node.storyChoices()) {
                Button button = new Button(choice.choiceText());
                button.setId("story-choice-" + choice.choiceId());
                button.setAccessibleText(choice.choiceText());
                button.setMaxWidth(Double.MAX_VALUE);
                button.getStyleClass().add("story-choice-button");
                button.setOnAction(event -> chooseStory(choice.choiceId()));
                storyChoices.getChildren().add(button);
            }
        } else {
            storyNextButton.setText("NEXT");
            storyNextButton.setVisible(true);
            storyNextButton.setManaged(true);
            storyNextButton.setDisable(node.readingCheck() || node.nextNodeId().isEmpty());
        }
        gameplayRoot.requestFocus();
    }

    private void showCharacterImage(StoryNode node) {
        speakerImage.setImage(null);
        speakerImage.setVisible(false);
        speakerImage.setManaged(false);
        if (!navigator.characterArtworkEnabled() || node.speakerAssetKey().isBlank()) {
            speakerInitials.setVisible(!node.speakerName().isBlank());
            speakerInitials.setManaged(!node.speakerName().isBlank());
            return;
        }
        try {
            String configuredRoot = System.getenv().getOrDefault("ARG_ASSET_DIR", "assets");
            assets.findCharacterImage(node.speakerAssetKey(), Path.of(configuredRoot)).ifPresent(path -> {
                Image image = new Image(path.toUri().toString(), 250, 330, true, true, false);
                if (!image.isError()) {
                    speakerImage.setImage(image);
                    speakerImage.setAccessibleText(node.speakerName());
                    speakerImage.setVisible(true);
                    speakerImage.setManaged(true);
                    speakerInitials.setVisible(false);
                    speakerInitials.setManaged(false);
                }
            });
        } catch (RuntimeException invalidAssetDirectory) {
            speakerInitials.setVisible(!node.speakerName().isBlank());
            speakerInitials.setManaged(!node.speakerName().isBlank());
        }
    }

    private void showAssessment(AccountGameService.GameView view,
                                List<AssessmentService.QuestionPrompt> questions) {
        storyScroll.setVisible(false);
        storyScroll.setManaged(false);
        assessmentScroll.setVisible(true);
        assessmentScroll.setManaged(true);
        footerText.setText("No timer. Only your first submitted answer counts toward the score.");
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
            choice.setMaxWidth(Double.MAX_VALUE);
            choice.setUserData(option);
            choice.setToggleGroup(answerGroup);
            choice.getStyleClass().add("quiz-option");
            choice.setOnAction(event -> submitButton.setDisable(answerGroup.getSelectedToggle() == null));
            answerChoices.getChildren().add(choice);
        }
        submitButton.setDisable(true);
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
    }

    @FXML private void nextQuestion() {
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

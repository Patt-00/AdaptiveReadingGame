package com.adaptivereadinggame.ui;

import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.backend.UnavailableGameplay;
import com.adaptivereadinggame.content.StoryPack;
import com.adaptivereadinggame.controller.AppNavigator;
import com.adaptivereadinggame.model.Chapter;
import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.AssessmentResult;
import com.adaptivereadinggame.model.Attempt;
import com.adaptivereadinggame.model.Question;
import com.adaptivereadinggame.model.StoryNode;
import com.adaptivereadinggame.model.GameState;
import com.adaptivereadinggame.model.SaveGame;
import com.adaptivereadinggame.model.SaveSnapshot;
import com.adaptivereadinggame.model.StoryChoice;
import com.adaptivereadinggame.repository.memory.InMemoryAccountRepository;
import com.adaptivereadinggame.repository.memory.InMemoryGameRepository;
import com.adaptivereadinggame.repository.memory.InMemoryAssessmentDraftRepository;
import com.adaptivereadinggame.repository.memory.InMemorySaveRepository;
import com.adaptivereadinggame.repository.mysql.ConnectionProvider;
import com.adaptivereadinggame.repository.mysql.DriverManagerConnectionProvider;
import com.adaptivereadinggame.repository.mysql.MySqlConfig;
import com.adaptivereadinggame.repository.mysql.MySqlSchemaMigrator;
import com.adaptivereadinggame.service.BaselineDifficultyPolicy;
import com.adaptivereadinggame.service.ModelAssistedDifficultyPolicy;
import com.adaptivereadinggame.service.Pbkdf2PasswordHasher;
import com.adaptivereadinggame.service.RepositoryStoryService;
import com.adaptivereadinggame.service.StoryService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in native UI tests: xvfb-run -a mvn -Darg.uiTests=true -Dtest=Dev1UiTest test. */
@EnabledIfSystemProperty(named = "arg.uiTests", matches = "true")
class Dev1UiTest {
    private Stage stage;
    private AppNavigator navigator;

    @BeforeAll static void startJavaFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @AfterEach void closeWindows() throws Exception {
        fx(() -> {
            for (Window window : java.util.List.copyOf(Window.getWindows())) window.hide();
            return null;
        });
    }

    @Test void signupThenLoginUsesOneContextAndShowsHonestEmptyMenu() throws Exception {
        BackendContext backend = launch();
        fx(() -> {
            click("SIGN UP");
            capture(root(), "signup-form");
            field("usernameField").setText("ReaderOne");
            field("displayNameField").setText("Reader One");
            password().setText("StrongPass1!");
            click("SIGN UP");
            assertTrue(navigator.workInProgress());
            assertTrue(((VBox) root().lookup("#formBox")).isDisabled());
            assertEquals("", password().getText());
            return null;
        });
        await(() -> !navigator.workInProgress());
        fx(() -> {
            assertEquals("Account created successfully! Please log in.", label("statusLabel").getText());
            assertTrue(backend.accountGameService().currentAccount().isEmpty());
            assertEquals("LOG IN", ((Button) root().lookup("#submitButton")).getText());
            assertFalse(field("displayNameField").isVisible());
            capture(root(), "signup-success");
            password().setText("StrongPass1!");
            click("LOG IN");
            assertTrue(navigator.workInProgress());
            return null;
        });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        fx(() -> {
            assertEquals("readerone", backend.accountGameService().currentAccount().orElseThrow().username());
            assertEquals("Welcome, Reader One", label("accountLabel").getText());
            assertTrue(((Button) root().lookup("#continueButton")).isDisabled());
            assertTrue(label("runtimeLabel").getText().contains("Temporary storage"));
            assertNotNull(button("LOG OUT").getOnAction());
            assertNotNull(button("READING HISTORY").getOnAction());
            capture(root(), "main-menu");
            return null;
        });
    }

    @Test void newGameWithoutPreparedContentExplainsTheContentBoundary() throws Exception {
        BackendContext backend = launch();
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        backend.accountGameService().login("reader", "StrongPass1!".toCharArray());
        fx(() -> { navigator.showMenu(); return null; });
        await(() -> !navigator.workInProgress());
        Platform.runLater(() -> click("NEW GAME"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            DialogPane pane = dialogPane();
            assertEquals("No chapter available", pane.getHeaderText());
            String message = pane.lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).map(Label::getText).collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(message.contains("No prepared chapter is available at the recommended difficulty."));
            assertTrue(message.contains("No pretend story or score has been created."));
            ((Button) pane.lookupButton(ButtonType.OK)).fire();
            return null;
        });
    }

    @Test void readingSettingsApplyTextSizeAndArtworkPreferenceForTheSession() throws Exception {
        BackendContext backend = launch();
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        backend.accountGameService().login("reader", "StrongPass1!".toCharArray());
        fx(() -> { navigator.showMenu(); return null; });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        Platform.runLater(() -> click("SETTINGS"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        Platform.runLater(() -> {
            DialogPane pane = dialogPane();
            ((Slider) pane.lookup("#readingTextSize")).setValue(1.4);
            ((CheckBox) pane.lookup("#showCharacterArtwork")).setSelected(false);
            ((Button) pane.lookupButton(ButtonType.APPLY)).fire();
        });
        await(() -> Window.getWindows().stream().noneMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            assertEquals(1.4, navigator.readingFontScale(), 0.001);
            assertFalse(navigator.characterArtworkEnabled());
            return null;
        });
    }

    @Test void playerCanReadChooseAnswerGetFeedbackAndReachResults() throws Exception {
        BackendContext backend = launch();
        backend.accountGameService().signup("player", "Story Player", "StrongPass1!".toCharArray());
        backend.accountGameService().login("player", "StrongPass1!".toCharArray());
        StoryPack.ChapterPack first = fixtureChapter("fixture-easy-one", "Fixture Chapter One");
        StoryPack.ChapterPack second = fixtureChapter("fixture-easy-two", "Fixture Chapter Two");
        backend.storyPackImporter().orElseThrow().importPack(new StoryPack(List.of(first, second)));

        fx(() -> { navigator.showMenu(); return null; });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        Platform.runLater(() -> click("NEW GAME"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            dialogButton("Fixture Chapter One — EASY").fire();
            return null;
        });

        await(() -> root().lookup("#instructionsRoot") != null && !navigator.workInProgress());
        fx(() -> {
            assertEquals("Fixture Chapter One", label("chapterLabel").getText());
            capture(root(), "game-how-to-play");
            click("START CHAPTER");
            return null;
        });
        await(() -> root().lookup("#gameplayRoot") != null && !navigator.workInProgress());
        await(() -> root().lookup("#storyText") != null && !navigator.workInProgress());
        fx(() -> {
            assertEquals("Fixture Chapter One", label("chapterTitle").getText());
            assertEquals("A test narrator guides the reader.", label("storyText").getText());
            capture(root(), "game-story-dialogue");
            click("MENU");
            return null;
        });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        Platform.runLater(() -> click("SAVE/LOAD"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> { dialogButton("SAVE").fire(); return null; });
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing())
                && dialogPane().getHeaderText().equals("Save successful"));
        fx(() -> { ((Button) dialogPane().lookupButton(ButtonType.OK)).fire(); return null; });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        fx(() -> {
            assertTrue(root().lookup("#continueButton") instanceof Button continueButton
                    && continueButton.getText().equals("RETURN TO GAME"));
            return null;
        });
        fx(() -> { click("RETURN TO GAME"); return null; });
        await(() -> root().lookup("#storyText") instanceof Label text
                && text.getText().equals("A test narrator guides the reader.") && !navigator.workInProgress());
        fx(() -> { click("NEXT"); return null; });
        await(() -> root().lookup("#story-choice-help") != null && !navigator.workInProgress());
        fx(() -> { capture(root(), "game-story-choice"); return null; });
        fx(() -> {
            button("Help").fire();
            return null;
        });
        await(() -> root().lookup("#storyText") != null && label("storyText").getText().contains("selected story choice")
                && !navigator.workInProgress());
        fx(() -> { click("NEXT"); return null; });
        await(() -> root().lookup("#questionPrompt") != null
                && label("questionPrompt").getText().equals("What did the reader find?")
                && !navigator.workInProgress());
        fx(() -> {
            assertTrue(label("passageText").getText().contains("A small letter"));
            capture(root(), "game-reading-question");
            ((RadioButton) root().lookup("#answer-0")).fire();
            click("SUBMIT");
            return null;
        });
        await(() -> root().lookup("#feedbackHeading") != null
                && label("feedbackHeading").getText().equals("NOT QUITE") && !navigator.workInProgress());
        fx(() -> {
            assertEquals(1, root().lookupAll(".button").stream().filter(Button.class::isInstance)
                    .map(Button.class::cast).filter(button -> "MENU".equals(button.getText())).count());
            assertEquals("Correct answer: The letter", label("correctAnswerText").getText());
            assertEquals("The passage says the reader found a letter.", label("explanationText").getText());
            capture(root(), "game-answer-feedback");
            click("MENU");
            return null;
        });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        Platform.runLater(() -> click("SAVE/LOAD"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        Platform.runLater(() -> ((Button) dialogPane().lookup("#saveSlot2")).fire());
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing())
                && dialogPane().getHeaderText().equals("Save successful"));
        Platform.runLater(() -> ((Button) dialogPane().lookupButton(ButtonType.OK)).fire());
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        Platform.runLater(() -> click("LOG OUT"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing())
                && dialogPane().getHeaderText().equals("Log out?"));
        Platform.runLater(() -> ((Button) dialogPane().lookupButton(ButtonType.YES)).fire());
        await(() -> root().lookup("#selectionBox") != null && !navigator.workInProgress());
        fx(() -> {
            click("LOG IN");
            field("usernameField").setText("player");
            password().setText("StrongPass1!");
            click("LOG IN");
            return null;
        });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        Platform.runLater(() -> click("CONTINUE"));
        await(() -> root().lookup("#gameplayRoot") != null
                && root().lookup("#questionPrompt") instanceof Label prompt
                && prompt.getText().equals("Where was the letter?") && !navigator.workInProgress());
        fx(() -> {
            ((RadioButton) root().lookup("#answer-0")).fire();
            click("SUBMIT");
            return null;
        });
        await(() -> label("feedbackHeading").getText().equals("CORRECT") && !navigator.workInProgress());
        fx(() -> { click("VIEW RESULTS"); return null; });
        await(() -> root().lookup("#resultsRoot") != null
                && label("scoreLabel").getText().contains("50%") && !navigator.workInProgress());
        fx(() -> {
            assertEquals("1 of 2 first answers correct", label("answerCountLabel").getText());
            assertEquals("Next difficulty: Easy", label("nextDifficultyLabel").getText());
            assertFalse(button("NEXT CHALLENGE").isDisabled());
            capture(root(), "game-results");
            return null;
        });
        Platform.runLater(() -> click("NEXT CHALLENGE"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        Platform.runLater(() -> dialogButtonContaining("Fixture Chapter Two").fire());
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing())
                && dialogPane().getHeaderText().equals("Start next chapter?"));
        Platform.runLater(() -> ((Button) dialogPane().lookupButton(ButtonType.YES)).fire());
        await(() -> root().lookup("#gameplayRoot") != null
                && root().lookup("#chapterTitle") instanceof Label title
                && title.getText().equals("Fixture Chapter Two") && !navigator.workInProgress());
    }

    @Test
    @EnabledIfSystemProperty(named = "arg.mysqlTests", matches = "true")
    void mysqlBackedUiResumesAnAssessmentAfterBackendContextRecreation() throws Exception {
        ConnectionProvider connections = mysqlTestConnections();
        new MySqlSchemaMigrator(connections).migrate();
        BackendContext firstBackend = BackendContext.mysql(connections);
        StoryPack.ChapterPack chapter = fixtureChapter("mysql-ui-" + shortId(), "MySQL UI fixture");
        firstBackend.storyPackImporter().orElseThrow().importPack(new StoryPack(List.of(chapter)));
        String username = "mysql-ui-" + shortId();
        String passwordValue = "StrongPass1!";

        launch(firstBackend, "MySQL storage - disposable test database.");
        fx(() -> {
            click("SIGN UP");
            field("usernameField").setText(username);
            field("displayNameField").setText("MySQL UI Reader");
            password().setText(passwordValue);
            click("SIGN UP");
            return null;
        });
        await(() -> !navigator.workInProgress());
        fx(() -> {
            assertEquals("Account created successfully! Please log in.", label("statusLabel").getText());
            password().setText(passwordValue);
            click("LOG IN");
            return null;
        });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        fx(() -> {
            assertTrue(label("runtimeLabel").getText().contains("MySQL storage"));
            click("NEW GAME");
            return null;
        });
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> { dialogButtonContaining("MySQL UI fixture").fire(); return null; });
        await(() -> root().lookup("#instructionsRoot") != null && !navigator.workInProgress());
        fx(() -> { click("START CHAPTER"); return null; });
        await(() -> root().lookup("#gameplayRoot") != null && !navigator.workInProgress());
        fx(() -> { click("NEXT"); return null; });
        await(() -> root().lookup("#story-choice-help") != null && !navigator.workInProgress());
        fx(() -> { button("Help").fire(); return null; });
        await(() -> root().lookup("#storyText") instanceof Label text
                && text.getText().contains("selected story choice") && !navigator.workInProgress());
        fx(() -> { click("NEXT"); return null; });
        await(() -> root().lookup("#questionPrompt") instanceof Label prompt
                && prompt.getText().equals("What did the reader find?") && !navigator.workInProgress());
        fx(() -> {
            ((RadioButton) root().lookup("#answer-1")).fire();
            click("SUBMIT");
            return null;
        });
        await(() -> root().lookup("#feedbackHeading") instanceof Label heading
                && heading.getText().equals("CORRECT") && !navigator.workInProgress());
        fx(() -> { click("MENU"); return null; });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        Platform.runLater(() -> click("SAVE/LOAD"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> { dialogButton("SAVE").fire(); return null; });
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing())
                && dialogPane().getHeaderText().equals("Save successful"));
        fx(() -> { ((Button) dialogPane().lookupButton(ButtonType.OK)).fire(); stage.hide(); return null; });

        BackendContext reopenedBackend = BackendContext.mysql(connections);
        launch(reopenedBackend, "MySQL storage - disposable test database.");
        fx(() -> {
            click("LOG IN");
            field("usernameField").setText(username);
            password().setText(passwordValue);
            click("LOG IN");
            return null;
        });
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        fx(() -> {
            assertFalse(button("CONTINUE").isDisabled());
            click("CONTINUE");
            return null;
        });
        await(() -> root().lookup("#gameplayRoot") != null
                && root().lookup("#questionPrompt") instanceof Label prompt
                && prompt.getText().equals("Where was the letter?") && !navigator.workInProgress());
        fx(() -> {
            capture(root(), "mysql-ui-resumed-question");
            assertEquals(Map.of(chapter.chapter().questions().getFirst().id(), 1),
                    reopenedBackend.accountGameService().currentGame().orElseThrow().firstAnswers());
            ((RadioButton) root().lookup("#answer-0")).fire();
            click("SUBMIT");
            return null;
        });
        await(() -> root().lookup("#feedbackHeading") instanceof Label heading
                && heading.getText().equals("CORRECT") && !navigator.workInProgress());
        fx(() -> { click("VIEW RESULTS"); return null; });
        await(() -> root().lookup("#resultsRoot") != null
                && label("scoreLabel").getText().contains("100%") && !navigator.workInProgress());
        fx(() -> {
            assertEquals("2 of 2 first answers correct", label("answerCountLabel").getText());
            capture(root(), "mysql-ui-results");
            return null;
        });
        assertEquals(1, reopenedBackend.accountGameService().history().size());
    }

    @Test void resultsScreenExplainsThatJavaFallbackWasUsedWhenTheModelFails() throws Exception {
        var accounts = new InMemoryAccountRepository();
        var game = new InMemoryGameRepository();
        var saves = new InMemorySaveRepository();
        var drafts = new InMemoryAssessmentDraftRepository(accounts, game, game, game);
        var policy = new ModelAssistedDifficultyPolicy(game, prompt -> {
            throw new java.io.IOException("Synthetic unavailable model");
        });
        BackendContext backend = BackendContext.create(accounts, accounts, accounts, accounts, game, game,
                saves, saves, game, drafts,
                assessment -> new RepositoryStoryService(accounts, game, game, drafts, assessment),
                new Pbkdf2PasswordHasher(), policy, Clock.systemUTC());
        launch(backend);
        var player = backend.accountGameService();
        var account = player.signup("fallback-reader", "Fallback Reader", "StrongPass1!".toCharArray());
        player.login(account.username(), "StrongPass1!".toCharArray());
        Instant now = Instant.now();
        for (int index = 0; index < 2; index++) {
            UUID previousChapter = UUID.randomUUID();
            game.saveCompleted(new AssessmentResult(new Attempt(UUID.randomUUID(), account.id(), previousChapter,
                    4, 5, now.plusSeconds(index)), Difficulty.MEDIUM, Difficulty.MEDIUM));
        }
        UUID chapterId = UUID.randomUUID();
        UUID firstQuestion = UUID.randomUUID();
        UUID secondQuestion = UUID.randomUUID();
        game.saveChapter(new Chapter(chapterId, "Fallback fixture", "Fixture passage.", Difficulty.MEDIUM,
                List.of(new Question(firstQuestion, "Question one?", List.of("A", "B"), 1),
                        new Question(secondQuestion, "Question two?", List.of("A", "B"), 0))));
        game.saveStoryNodes(chapterId, List.of(
                new StoryNode("check", "", "Fixture reading check.", false, List.of(), "", true)), "check");
        player.startChapter(chapterId, false);
        UUID session = player.startAssessment(chapterId);
        assertFalse(player.submit(session, firstQuestion, 0));
        assertTrue(player.submit(session, secondQuestion, 0));
        AssessmentResult result = player.complete(session);
        assertEquals(com.adaptivereadinggame.model.DifficultyDecisionSource.JAVA_FALLBACK, result.decisionSource());

        fx(() -> { navigator.showResults(); return null; });
        await(() -> root().lookup("#decisionSourceLabel") instanceof Label source
                && source.getText().contains("model was unavailable") && !navigator.workInProgress());
        fx(() -> {
            assertTrue(label("decisionSourceLabel").getText().contains("Java used its fallback"));
            capture(root(), "game-results-model-fallback");
            return null;
        });
    }

    @Test void blankCredentialsStayInFormAndBackClearsPassword() throws Exception {
        launch();
        fx(() -> {
            click("LOG IN");
            password().setText("unusedSecret");
            click("LOG IN");
            assertEquals("Enter your username and password.", label("statusLabel").getText());
            assertEquals("", password().getText());
            assertFalse(navigator.workInProgress());
            capture(root(), "login-validation");
            password().setText("secondSecret");
            click("BACK");
            assertEquals("", password().getText());
            assertTrue(root().lookup("#selectionBox").isVisible());
            return null;
        });
    }

    @Test void signupRequiresDisplayNameAndNeverCreatesPartialAccount() throws Exception {
        BackendContext backend = launch();
        fx(() -> {
            click("SIGN UP");
            field("usernameField").setText("reader");
            password().setText("StrongPass1!");
            click("SIGN UP");
            assertEquals("Enter a username, display name, and password.", label("statusLabel").getText());
            assertEquals("", password().getText());
            assertFalse(navigator.workInProgress());
            assertTrue(backend.accountGameService().currentAccount().isEmpty());
            capture(root(), "signup-validation");
            return null;
        });
    }

    @Test void invalidCredentialsProduceReadableMessageWithoutPasswordRemaining() throws Exception {
        BackendContext backend = launch();
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        fx(() -> {
            click("LOG IN");
            field("usernameField").setText("reader");
            password().setText("WrongPass");
            click("LOG IN");
            return null;
        });
        await(() -> !navigator.workInProgress());
        fx(() -> {
            assertEquals("The username or password is incorrect.", label("statusLabel").getText());
            assertTrue(backend.accountGameService().currentAccount().isEmpty());
            assertEquals("", password().getText());
            capture(root(), "login-incorrect-password");
            return null;
        });
    }

    @Test void cancelledWindowCloseKeepsApplicationAndAccount() throws Exception {
        BackendContext backend = launch();
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        backend.accountGameService().login("reader", "StrongPass1!".toCharArray());
        fx(() -> { navigator.showMenu(); return null; });
        await(() -> !navigator.workInProgress());
        Platform.runLater(() -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            DialogPane pane = dialogPane();
            assertEquals("Quit the game?", pane.getHeaderText());
            capture(pane, "quit-confirmation");
            ((Button) pane.lookupButton(ButtonType.NO)).fire();
            assertTrue(stage.isShowing());
            assertTrue(backend.accountGameService().currentAccount().isPresent());
            return null;
        });
    }

    @Test void emptySaveSlotsNeverAdvertiseSuccessfulPersistence() throws Exception {
        BackendContext backend = launch();
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        backend.accountGameService().login("reader", "StrongPass1!".toCharArray());
        fx(() -> { navigator.showMenu(); return null; });
        await(() -> !navigator.workInProgress());
        Platform.runLater(() -> click("SAVE/LOAD"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            DialogPane pane = dialogPane();
            long saveButtons = pane.lookupAll(".button").stream().filter(node -> node instanceof Button button
                    && button.getText().equals("SAVE") && button.isDisabled()).count();
            long loadButtons = pane.lookupAll(".button").stream().filter(node -> node instanceof Button button
                    && button.getText().equals("LOAD") && button.isDisabled()).count();
            assertEquals(3, saveButtons);
            assertEquals(3, loadButtons);
            capture(pane, "empty-save-slots");
            Label explanation = (Label) pane.lookup("#saveExplanation");
            assertTrue(explanation.getHeight() >= explanation.prefHeight(explanation.getWidth()) - 1,
                    "Save explanation must wrap fully, without truncation.");
            ((Button) pane.lookupButton(ButtonType.CLOSE)).fire();
            assertTrue(backend.accountGameService().snapshots().isEmpty());
            return null;
        });
    }

    @Test void historyShowsRealComprehensionScoreAndNextDifficulty() throws Exception {
        BackendContext backend = launch();
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        backend.accountGameService().login("reader", "StrongPass1!".toCharArray());
        UUID chapterId = UUID.randomUUID();
        UUID firstQuestion = UUID.randomUUID();
        UUID secondQuestion = UUID.randomUUID();
        backend.chapters().saveChapter(new Chapter(chapterId, "UI test fixture only", "Fixture passage.", Difficulty.MEDIUM,
                List.of(new Question(firstQuestion, "Question one", List.of("A", "B"), 0),
                        new Question(secondQuestion, "Question two", List.of("A", "B"), 0))));
        // Test fixture preparation only. Production controllers never bypass AccountGameService.
        UUID studentId = backend.accountGameService().currentAccount().orElseThrow().id();
        UUID session = backend.assessmentService().start(studentId, chapterId);
        backend.assessmentService().submit(session, firstQuestion, 0);
        backend.assessmentService().submit(session, secondQuestion, 1);
        backend.assessmentService().complete(session);
        fx(() -> { navigator.showMenu(); return null; });
        await(() -> !navigator.workInProgress());
        Platform.runLater(() -> click("READING HISTORY"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            DialogPane pane = dialogPane();
            String rendered = pane.lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).map(Label::getText).collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(rendered.contains("1 / 2 correct — 50%"));
            assertTrue(rendered.contains("Next difficulty: EASY"));
            capture(pane, "reading-history");
            ((Button) pane.lookupButton(ButtonType.CLOSE)).fire();
            return null;
        });
    }

    @Test void populatedSlotEnablesContinueAndOnlyItsLoadButton() throws Exception {
        var accounts = new InMemoryAccountRepository();
        var game = new InMemoryGameRepository();
        var saves = new InMemorySaveRepository();
        var pending = new UnavailableGameplay();
        BackendContext backend = launch(BackendContext.create(accounts, accounts, accounts, accounts,
                game, game, saves, saves, pending, pending, ignored -> pending,
                new Pbkdf2PasswordHasher(), new BaselineDifficultyPolicy(), Clock.systemUTC()));
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        UUID studentId = backend.accountGameService().login("reader", "StrongPass1!".toCharArray()).id();
        UUID chapterId = UUID.randomUUID();
        game.saveChapter(new Chapter(chapterId, "UI test fixture only", "Fixture passage.", Difficulty.EASY,
                List.of(new Question(UUID.randomUUID(), "Fixture question", List.of("A", "B"), 0))));
        // Storage fixture only: production Save requires a real Dev 2 GameState and validates it.
        saves.saveSnapshot(new SaveSnapshot(new SaveGame(studentId, 1, chapterId, "chapter-introduction",
                Difficulty.EASY, Instant.now()),
                new GameState(studentId, chapterId, "chapter-introduction", null, 0, Map.of())));
        fx(() -> { navigator.showMenu(); return null; });
        await(() -> !navigator.workInProgress());
        fx(() -> {
            assertFalse(((Button) root().lookup("#continueButton")).isDisabled());
            capture(root(), "main-menu-with-continue");
            return null;
        });
        Platform.runLater(() -> click("SAVE/LOAD"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            DialogPane pane = dialogPane();
            long availableLoadButtons = pane.lookupAll(".button").stream().filter(node -> node instanceof Button button
                    && button.getText().equals("LOAD") && !button.isDisabled()).count();
            assertEquals(1, availableLoadButtons);
            capture(pane, "populated-save-slots");
            Label details = (Label) pane.lookup("#slotDetails1");
            assertTrue(details.getHeight() >= details.prefHeight(details.getWidth()) - 1,
                    "All populated-slot details must be visible without truncation.");
            ((Button) pane.lookupButton(ButtonType.CLOSE)).fire();
            return null;
        });
    }

    @Test void logoutConfirmationSupportsCancelThenReturnsToWelcome() throws Exception {
        BackendContext backend = launch();
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        backend.accountGameService().login("reader", "StrongPass1!".toCharArray());
        fx(() -> { navigator.showMenu(); return null; });
        await(() -> !navigator.workInProgress());
        Platform.runLater(() -> click("LOG OUT"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            DialogPane pane = dialogPane();
            assertEquals("Log out?", pane.getHeaderText());
            capture(pane, "logout-confirmation");
            ((Button) pane.lookupButton(ButtonType.NO)).fire();
            assertTrue(backend.accountGameService().currentAccount().isPresent());
            return null;
        });
        Platform.runLater(() -> click("LOG OUT"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            ((Button) dialogPane().lookupButton(ButtonType.YES)).fire();
            return null;
        });
        await(() -> root().lookup("#selectionBox") != null && !navigator.workInProgress());
        assertTrue(backend.accountGameService().currentAccount().isEmpty());
    }

    @Test void failedGameCleanupStillReturnsLoggedOutUserToAuthentication() throws Exception {
        BackendContext backend = launch(cleanupFailureContext());
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        backend.accountGameService().login("reader", "StrongPass1!".toCharArray());
        fx(() -> { navigator.showMenu(); return null; });
        await(() -> !navigator.workInProgress());
        Platform.runLater(() -> click("LOG OUT"));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            ((Button) dialogPane().lookupButton(ButtonType.YES)).fire();
            return null;
        });
        await(() -> root().lookup("#selectionBox") != null
                && Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            DialogPane pane = dialogPane();
            assertEquals("Logged out", pane.getHeaderText());
            assertFalse(pane.getContentText().contains("secret-database-detail"));
            assertTrue(backend.accountGameService().currentAccount().isEmpty());
            ((Button) pane.lookupButton(ButtonType.OK)).fire();
            assertTrue(stage.isShowing());
            return null;
        });
    }

    @Test void confirmedQuitClosesEvenWhenGameCleanupFails() throws Exception {
        BackendContext backend = launch(cleanupFailureContext());
        backend.accountGameService().signup("reader", "Reader", "StrongPass1!".toCharArray());
        backend.accountGameService().login("reader", "StrongPass1!".toCharArray());
        fx(() -> { navigator.showMenu(); return null; });
        await(() -> !navigator.workInProgress());
        Platform.runLater(() -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()));
        fx(() -> {
            ((Button) dialogPane().lookupButton(ButtonType.YES)).fire();
            return null;
        });
        await(() -> !stage.isShowing());
        assertTrue(backend.accountGameService().currentAccount().isEmpty());
    }

    private BackendContext launch() throws Exception {
        return launch(BackendContext.inMemory());
    }

    private BackendContext launch(BackendContext backend) throws Exception {
        return launch(backend, "Temporary storage - test accounts close with the app.");
    }

    private BackendContext launch(BackendContext backend, String storageDescription) throws Exception {
        fx(() -> {
            stage = new Stage();
            navigator = new AppNavigator(stage, backend, storageDescription);
            navigator.start();
            capture(root(), "welcome");
            return null;
        });
        return backend;
    }

    private static ConnectionProvider mysqlTestConnections() {
        String url = System.getenv("ARG_MYSQL_TEST_URL");
        String username = System.getenv("ARG_MYSQL_TEST_USER");
        String password = System.getenv("ARG_MYSQL_TEST_PASSWORD");
        if (url == null || username == null || password == null)
            throw new IllegalStateException("Set the three ARG_MYSQL_TEST_* variables for the opt-in test.");
        URI address = URI.create("http" + url.substring("jdbc:mysql".length()));
        if (!List.of("localhost", "127.0.0.1", "::1").contains(address.getHost()))
            throw new IllegalArgumentException("MySQL UI tests may connect only to a loopback host.");
        if (!"/adaptive_reading_test".equals(address.getPath()))
            throw new IllegalArgumentException("MySQL UI tests require the adaptive_reading_test schema.");
        return new DriverManagerConnectionProvider(new MySqlConfig(url, username, password));
    }

    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8); }

    private StoryPack.ChapterPack fixtureChapter(String id, String title) {
        UUID chapterId = UUID.randomUUID();
        UUID firstQuestion = UUID.randomUUID();
        UUID secondQuestion = UUID.randomUUID();
        Chapter chapter = new Chapter(chapterId, title,
                "A small letter was found beside the garden gate. The reader placed it in a safe pocket.",
                Difficulty.EASY,
                List.of(new Question(firstQuestion, "What did the reader find?", List.of("A stone", "The letter"), 1,
                                "The passage says the reader found a letter."),
                        new Question(secondQuestion, "Where was the letter?", List.of("Beside the garden gate", "In a book"), 0)));
        List<StoryNode> nodes = List.of(
                new StoryNode("opening", "Mia", "A test narrator guides the reader.", false, List.of(), "choice", false),
                new StoryNode("choice", "Mia", "Choose one of the two test paths.", false,
                        List.of(new StoryChoice("help", "Help", "chosen"),
                                new StoryChoice("wait", "Wait", "chosen")), "", false),
                new StoryNode("chosen", "Mia", "The selected story choice changes only this dialogue.", false,
                        List.of(), "reading-check", false),
                new StoryNode("reading-check", "", "Reading check.", false, List.of(), "", true));
        return new StoryPack.ChapterPack(id, chapter, "opening", nodes);
    }

    private BackendContext cleanupFailureContext() {
        var accounts = new InMemoryAccountRepository();
        var game = new InMemoryGameRepository();
        var saves = new InMemorySaveRepository();
        var pending = new UnavailableGameplay();
        StoryService failingCleanup = new StoryService() {
            @Override public GameState startChapter(UUID studentId, UUID chapterId) { return pending.startChapter(studentId, chapterId); }
            @Override public GameState advanceStory(UUID studentId) { return pending.advanceStory(studentId); }
            @Override public GameState chooseStory(UUID studentId, String choiceId) { return pending.chooseStory(studentId, choiceId); }
            @Override public Optional<GameState> currentState(UUID studentId) { return pending.currentState(studentId); }
            @Override public GameState restoreState(GameState state) { return pending.restoreState(state); }
            @Override public void clearStateFor(UUID studentId) { throw new IllegalStateException("secret-database-detail"); }
        };
        return BackendContext.create(accounts, accounts, accounts, accounts, game, game, saves, saves,
                pending, pending, ignored -> failingCleanup, new Pbkdf2PasswordHasher(),
                new BaselineDifficultyPolicy(), Clock.systemUTC());
    }

    private Parent root() { return stage.getScene().getRoot(); }
    private Label label(String id) { return (Label) root().lookup("#" + id); }
    private TextField field(String id) { return (TextField) root().lookup("#" + id); }
    private PasswordField password() { return (PasswordField) root().lookup("#passwordField"); }

    private Button button(String text) {
        return root().lookupAll(".button").stream().filter(node -> node instanceof Button button
                && button.getText().equals(text) && visible(node)).map(Button.class::cast).findFirst().orElseThrow();
    }

    private boolean visible(Node node) {
        for (Node current = node; current != null; current = current.getParent()) if (!current.isVisible()) return false;
        return true;
    }

    private void click(String text) { button(text).fire(); }

    private DialogPane dialogPane() {
        return Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).findFirst().orElseThrow();
    }

    private Button dialogButton(String text) {
        return dialogPane().lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private Button dialogButtonContaining(String text) {
        return dialogPane().lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> button.getText().contains(text)).findFirst().orElseThrow();
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (fx(condition::getAsBoolean)) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for the JavaFX action.");
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private void capture(Parent view, String name) throws Exception {
        String destination = System.getProperty("arg.ui.captureDir");
        if (destination == null || destination.isBlank()) return;
        view.applyCss();
        view.layout();
        var image = view.snapshot(null, null);
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++)
            for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        Path folder = Path.of(destination).toAbsolutePath();
        Files.createDirectories(folder);
        ImageIO.write(output, "png", folder.resolve(name + ".png").toFile());
    }
}

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
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.geometry.Bounds;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
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
            Label usernameLabel = root().lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).filter(item -> "Username".equals(item.getText())).findFirst().orElseThrow();
            Label displayNameLabel = label("displayNameLabel");
            Label passwordLabel = root().lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).filter(item -> "Password".equals(item.getText())).findFirst().orElseThrow();
            assertSame(field("usernameField"), usernameLabel.getLabelFor());
            assertSame(field("displayNameField"), displayNameLabel.getLabelFor());
            assertSame(password(), passwordLabel.getLabelFor());
            Button submitButton = (Button) root().lookup("#submitButton");
            submitButton.requestFocus();
            root().applyCss();
            assertTrue(submitButton.isFocused());
            assertVisibleFocusRing(submitButton, Color.web("#655438"));
            field("usernameField").requestFocus();
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
            Button continueButton = (Button) root().lookup("#continueButton");
            assertTrue(continueButton.isDisabled());
            assertReadableDisabledButton(continueButton);
            assertTrue(label("continueStatusLabel").isVisible());
            assertTrue(label("continueStatusLabel").getText().contains("Save progress"));
            assertTrue(continueButton.getAccessibleHelp().contains("Open Save/Load"));
            assertTrue(label("runtimeLabel").getText().contains("Temporary storage"));
            assertNotNull(button("LOG OUT").getOnAction());
            assertNotNull(button("READING HISTORY").getOnAction());
            Button historyButton = button("READING HISTORY");
            historyButton.requestFocus();
            root().applyCss();
            assertTrue(historyButton.isFocused());
            assertVisibleFocusRing(historyButton, Color.web("#655438"));
            capture(root(), "accessibility-menu-continue");
            capture(root(), "main-menu");
            new Robot().keyPress(KeyCode.TAB);
            new Robot().keyRelease(KeyCode.TAB);
            return null;
        });
        await(() -> button("LOG OUT").isFocused());
        fx(() -> {
            assertVisibleFocusRing(button("LOG OUT"), Color.web("#655438"));
            return null;
        });
    }

    @Test void illustratedViewportStaysBoundedAcrossWindowSizesAndReadingScales() throws Exception {
        BackendContext backend = launch();
        var game = backend.accountGameService();
        game.signup("viewport-reader", "Viewport Reader", "StrongPass1!".toCharArray());
        game.login("viewport-reader", "StrongPass1!".toCharArray());
        backend.storyPackImporter().orElseThrow().importFile(Path.of("assets/content/hailey-investigation-draft.json"));
        Chapter chapter = backend.chapters().findByDifficulty(Difficulty.EASY).getFirst();
        game.startChapter(chapter.id(), Difficulty.EASY, false);
        for (String nodeId : List.of("solarium-opening", "hailey-arrives", "poisoning-cg", "study-first-search-choice")) {
            advanceDraftTo(game, nodeId);
            for (double scale : new double[]{0.8, 1.0, 1.6}) {
                for (int[] size : new int[][]{{1280, 720}, {1366, 768}, {800, 600}, {760, 520}}) {
                    fx(() -> {
                        stage.setWidth(size[0]);
                        stage.setHeight(size[1]);
                        navigator.setReadingFontScale(scale);
                        navigator.showGameplay();
                        return null;
                    });
                    await(() -> root().lookup("#storyScroll").isVisible() && !navigator.workInProgress());
                    // Waiting through pulses is essential: the original background loop grew while idle.
                    Thread.sleep(150);
                    double width = fx(() -> root().lookup("#centerRoot").getLayoutBounds().getWidth());
                    Thread.sleep(150);
                    fx(() -> {
                        Node center = root().lookup("#centerRoot");
                        assertEquals(width, center.getLayoutBounds().getWidth(), 1, "Idle viewport must not grow");
                        assertTrue(width <= stage.getScene().getWidth() + 1, "Gameplay must fit the actual scene");
                        assertTrue(center.getLayoutBounds().getHeight() <= stage.getScene().getHeight() + 1);
                        ScrollPane scroll = (ScrollPane) root().lookup("#storyScroll");
                        assertTrue(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 1);
                        for (Node choice : ((VBox) root().lookup("#storyChoices")).getChildren()) {
                            Button button = (Button) choice;
                            assertTrue(button.getHeight() >= button.prefHeight(button.getWidth()) - 1);
                        }
                        Node portrait = root().lookup("#portraitColumn");
                        Node panel = root().lookup("#storyPanel");
                        if (portrait.isVisible() && size[0] <= 800)
                            assertTrue(panel.localToScene(0, 0).getY() > portrait.localToScene(0, 0).getY());
                        if (scale == 1.6 && size[0] == 760) captureNative("responsive-" + nodeId);
                        return null;
                    });
                }
            }
        }
        fx(() -> { stage.setMaximized(true); return null; });
        Thread.sleep(250);
        fx(() -> { stage.setMaximized(false); return null; });
        Thread.sleep(250);
        fx(() -> {
            assertTrue(root().lookup("#centerRoot").getLayoutBounds().getWidth() <= stage.getScene().getWidth() + 1);
            return null;
        });
    }

    @Test void robotPlaysStudyFirstThroughTheActualControls() throws Exception { robotPlaysDraft("study-first"); }

    @Test void robotPlaysHallFirstThroughTheActualControls() throws Exception { robotPlaysDraft("hall-first"); }

    private void robotPlaysDraft(String route) throws Exception {
        BackendContext backend = launch();
        backend.storyPackImporter().orElseThrow().importFile(Path.of("assets/content/hailey-investigation-draft.json"));
        robotClick(() -> button("SIGN UP"));
        robotClick(() -> button("SIGN UP"));
        await(() -> label("statusLabel").getText().contains("Enter a username"));
        robotType("usernameField", "robot-" + route);
        robotType("displayNameField", "Robot Reader");
        robotType("passwordField", "StrongPass1!");
        robotClick(() -> button("SIGN UP"));
        await(() -> label("statusLabel").getText().contains("Account created"));
        robotType("passwordField", "StrongPass1!");
        robotClick(() -> button("LOG IN"));
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        assertTrue(fx(() -> ((Button) root().lookup("#continueButton")).isDisabled()));
        robotClick(() -> button("NEW GAME"));
        await(() -> Window.getWindows().stream().anyMatch(w -> w != stage && w.isShowing()));
        robotClick(() -> dialogButtonContaining("Aurelium"));
        await(() -> root().lookup("#instructionsRoot") != null);
        robotClick(() -> button("START CHAPTER"));
        await(() -> root().lookup("#storyText") != null && !navigator.workInProgress());
        int checks = 0;
        var game = backend.accountGameService();
        for (int step = 0; step < 600; step++) {
            await(() -> !navigator.workInProgress());
            var view = game.currentGameView().orElseThrow();
            if (view.storyNode().storyComplete()) {
                await(this::resultsReady);
                assertEquals(2, checks);
                assertEquals(2, game.episodeResults().size());
                assertTrue(fx(() -> label("nextChapterStatus").getText().contains("CURRENT STORY DRAFT ENDS HERE")));
                fx(() -> { captureNative("robot-" + route + "-complete"); return null; });
                System.out.println("PLAYER_FLOW route=" + route + " storySteps=" + step + " checks=" + checks
                        + " terminal=" + view.storyNode().storyNodeId());
                robotClick(() -> button("MAIN MENU"));
                await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
                return;
            }
            if (view.state().assessmentSessionId() != null) {
                await(() -> root().lookup("#assessmentScroll") != null && root().lookup("#assessmentScroll").isVisible());
                assertTrue(fx(() -> ((Button) root().lookup("#submitButton")).isDisabled()));
                // Deliberately choose another option before the final selection.
                robotClick(() -> root().lookup("#answer-0"));
                robotClick(() -> root().lookup("#answer-1"));
                robotClick(() -> button("SUBMIT"));
                await(() -> root().lookup("#feedbackPane").isVisible() && !navigator.workInProgress());
                assertTrue(fx(() -> ((VBox) root().lookup("#answerChoices")).getChildren().stream().allMatch(Node::isDisabled)));
                assertEquals(1, game.currentGame().orElseThrow().firstAnswers().size());
                fx(() -> { captureNative("robot-" + route + "-feedback"); return null; });
                robotClick(() -> button("VIEW RESULTS"));
                await(this::resultsReady);
                checks++;
                robotClick(() -> button("CONTINUE STORY"));
                await(() -> root().lookup("#storyText") != null && !navigator.workInProgress());
                continue;
            }
            String previous = view.storyNode().storyNodeId();
            if (previous.equals("solarium-opening") || previous.equals("hailey-arrives")) {
                Thread.sleep(100);
                fx(() -> { captureNative("robot-" + route + "-" + previous); return null; });
            }
            if (!view.storyNode().storyChoices().isEmpty()) {
                String choice = view.storyNode().storyChoices().stream().filter(c -> c.routeId().equals(route))
                        .map(StoryChoice::choiceId).findFirst().orElse(view.storyNode().storyChoices().getFirst().choiceId());
                robotClick(() -> root().lookup("#story-choice-" + choice));
            } else robotClick(() -> button("NEXT"));
            await(() -> !game.currentGame().orElseThrow().storyNodeId().equals(previous) && !navigator.workInProgress());
            fx(() -> {
                Node center = root().lookup("#centerRoot");
                if (center != null) assertTrue(center.getLayoutBounds().getWidth() <= stage.getScene().getWidth() + 1);
                else assertNotNull(root().lookup("#resultsRoot"), "A terminal story node opens its results screen");
                return null;
            });
        }
        fail("Robot did not complete route " + route);
    }

    @Test void heldSpaceAndFocusedNextButtonAdvanceOnlyOneNode() throws Exception {
        BackendContext backend = launch();
        var game = backend.accountGameService();
        game.signup("keyboard-reader", "Keyboard Reader", "StrongPass1!".toCharArray());
        game.login("keyboard-reader", "StrongPass1!".toCharArray());
        backend.storyPackImporter().orElseThrow().importFile(Path.of("assets/content/hailey-investigation-draft.json"));
        game.startChapter(backend.chapters().findByDifficulty(Difficulty.EASY).getFirst().id(), Difficulty.EASY, false);
        fx(() -> { navigator.showGameplay(); return null; });
        await(() -> root().lookup("#storyText") != null && !navigator.workInProgress());
        fx(() -> { new Robot().keyPress(KeyCode.SPACE); return null; });
        Thread.sleep(1100);
        assertEquals("opening-poem", game.currentGame().orElseThrow().storyNodeId());
        fx(() -> { new Robot().keyRelease(KeyCode.SPACE); button("NEXT").requestFocus(); return null; });
        fx(() -> { new Robot().keyPress(KeyCode.SPACE); return null; });
        fx(() -> { new Robot().keyRelease(KeyCode.SPACE); return null; });
        await(() -> game.currentGame().orElseThrow().storyNodeId().equals("opening-monologue") && !navigator.workInProgress());
        Thread.sleep(200);
        assertEquals("opening-monologue", game.currentGame().orElseThrow().storyNodeId());
        for (String target : List.of("solarium-opening", "solarium-opening-02", "amelia-task")) {
            fx(() -> { root().requestFocus(); new Robot().keyPress(KeyCode.SPACE);
                new Robot().keyRelease(KeyCode.SPACE); return null; });
            await(() -> game.currentGame().orElseThrow().storyNodeId().equals(target) && !navigator.workInProgress());
        }
    }

    @Test void robotNavigatesSaveLoadSettingsAndLogoutAtMinimumWindowSize() throws Exception {
        BackendContext backend = launch();
        var game = backend.accountGameService();
        game.signup("save-robot", "Save Robot", "StrongPass1!".toCharArray());
        game.login("save-robot", "StrongPass1!".toCharArray());
        var fixture = fixtureChapter("robot-save", "Robot Save Fixture");
        backend.storyPackImporter().orElseThrow().importPack(new StoryPack(List.of(fixture)));
        game.startChapter(fixture.chapter().id(), false);
        fx(() -> { stage.setWidth(760); stage.setHeight(520); navigator.showGameplay(); return null; });
        await(() -> root().lookup("#storyText") != null && !navigator.workInProgress());
        robotClick(() -> button("MENU"));
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        robotClick(() -> button("SAVE/LOAD"));
        awaitDialog("Three account-specific save slots");
        assertTrue(fx(() -> ((Button) dialogPane().lookup("#loadSlot1")).isDisabled()));
        robotClick(() -> dialogPane().lookup("#saveSlot1"));
        awaitDialog("Save successful");
        robotClick(() -> dialogPane().lookupButton(ButtonType.OK));
        await(() -> !navigator.workInProgress());
        robotClick(() -> button("SAVE/LOAD"));
        awaitDialog("Three account-specific save slots");
        robotClick(() -> dialogPane().lookup("#saveSlot1"));
        awaitDialog("Overwrite slot 1?");
        robotClick(() -> dialogPane().lookupButton(ButtonType.NO));
        awaitDialog("Three account-specific save slots");
        robotClick(() -> dialogPane().lookup("#saveSlot1"));
        awaitDialog("Overwrite slot 1?");
        robotClick(() -> dialogPane().lookupButton(ButtonType.YES));
        awaitDialog("Save successful");
        robotClick(() -> dialogPane().lookupButton(ButtonType.OK));
        await(() -> !navigator.workInProgress());
        robotClick(() -> button("SETTINGS"));
        awaitDialog("Adjust the reading view");
        fx(() -> {
            DialogPane pane = dialogPane();
            Label textSizeLabel = pane.lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).filter(item -> "Reading text size".equals(item.getText()))
                    .findFirst().orElseThrow();
            assertSame(pane.lookup("#readingTextSize"), textSizeLabel.getLabelFor());
            assertNotNull(((Slider) pane.lookup("#readingTextSize")).getAccessibleHelp());
            return null;
        });
        robotClick(() -> dialogPane().lookup("#showCharacterArtwork"));
        fx(() -> {
            CheckBox artwork = (CheckBox) dialogPane().lookup("#showCharacterArtwork");
            assertTrue(artwork.isFocused());
            assertVisibleFocusRing(artwork.lookup(".box"), Color.web("#6e1428"));
            return null;
        });
        fx(() -> { new Robot().keyPress(KeyCode.SHIFT); new Robot().keyPress(KeyCode.TAB);
            new Robot().keyRelease(KeyCode.TAB); new Robot().keyRelease(KeyCode.SHIFT); return null; });
        await(() -> dialogPane().getScene().getFocusOwner() instanceof Slider);
        fx(() -> {
            Slider textSize = (Slider) dialogPane().lookup("#readingTextSize");
            assertVisibleFocusRing(textSize.lookup(".thumb"), Color.web("#6e1428"));
            capture(dialogPane(), "accessibility-settings-slider");
            return null;
        });
        fx(() -> { new Robot().keyPress(KeyCode.END); new Robot().keyRelease(KeyCode.END); return null; });
        await(() -> ((Slider) dialogPane().lookup("#readingTextSize")).getValue() > 1.59);
        robotClick(() -> dialogPane().lookupButton(ButtonType.APPLY));
        await(() -> Window.getWindows().stream().noneMatch(w -> w != stage && w.isShowing()));
        assertEquals(1.6, navigator.readingFontScale(), 0.01);
        assertFalse(navigator.characterArtworkEnabled());
        robotClick(() -> button("RETURN TO GAME"));
        await(() -> root().lookup("#storyText") != null && !navigator.workInProgress());
        robotClick(() -> button("NEXT"));
        await(() -> game.currentGame().orElseThrow().storyNodeId().equals("choice") && !navigator.workInProgress());
        robotClick(() -> button("MENU"));
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        robotClick(() -> button("NEW GAME"));
        awaitDialog("Choose an available chapter");
        robotClick(() -> dialogButtonContaining("Robot Save Fixture"));
        await(() -> root().lookup("#instructionsRoot") != null);
        robotClick(() -> button("START CHAPTER"));
        awaitDialog("Start a new chapter?");
        robotClick(() -> dialogPane().lookupButton(ButtonType.NO));
        assertEquals("choice", game.currentGame().orElseThrow().storyNodeId());
        robotClick(() -> button("BACK TO MENU"));
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        robotClick(() -> button("SAVE/LOAD"));
        awaitDialog("Three account-specific save slots");
        robotClick(() -> dialogPane().lookup("#loadSlot1"));
        awaitDialog("Load slot 1?");
        robotClick(() -> dialogPane().lookupButton(ButtonType.NO));
        awaitDialog("Three account-specific save slots");
        assertEquals("choice", game.currentGame().orElseThrow().storyNodeId());
        robotClick(() -> dialogPane().lookup("#loadSlot1"));
        awaitDialog("Load slot 1?");
        robotClick(() -> dialogPane().lookupButton(ButtonType.YES));
        await(() -> root().lookup("#storyText") != null && !navigator.workInProgress());
        assertEquals("opening", game.currentGame().orElseThrow().storyNodeId());
        robotClick(() -> button("MENU"));
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        robotClick(() -> button("LOG OUT"));
        awaitDialog("Log out?");
        robotClick(() -> dialogPane().lookupButton(ButtonType.NO));
        assertTrue(game.currentAccount().isPresent());
        robotClick(() -> button("LOG OUT"));
        awaitDialog("Log out?");
        robotClick(() -> dialogPane().lookupButton(ButtonType.YES));
        await(() -> root().lookup("#selectionBox") != null && !navigator.workInProgress());
        robotClick(() -> button("LOG IN"));
        robotType("usernameField", "save-robot");
        robotType("passwordField", "StrongPass1!");
        robotClick(() -> button("LOG IN"));
        await(() -> root().lookup("#menuOptions") != null && !navigator.workInProgress());
        robotClick(() -> button("CONTINUE"));
        await(() -> root().lookup("#storyText") != null && !navigator.workInProgress());
        assertEquals("opening", game.currentGame().orElseThrow().storyNodeId());
    }

    @Test void questionsFeedbackAndResultsFitEverySupportedSizeAndTextScale() throws Exception {
        for (double scale : new double[]{0.8, 1.0, 1.6}) {
            for (int[] size : new int[][]{{1280, 720}, {1366, 768}, {800, 600}, {760, 520}}) {
                // Each layout case needs a fresh learner history: completed keys must not be rescored.
                if (stage != null) fx(() -> { stage.hide(); return null; });
                BackendContext backend = launch();
                var game = backend.accountGameService();
                game.signup("quiz-layout", "Quiz Layout", "StrongPass1!".toCharArray());
                game.login("quiz-layout", "StrongPass1!".toCharArray());
                backend.storyPackImporter().orElseThrow().importFile(Path.of("assets/content/hailey-investigation-draft.json"));
                Chapter chapter = backend.chapters().findByDifficulty(Difficulty.EASY).getFirst();
                game.startChapter(chapter.id(), Difficulty.HARD, true);
                advanceDraftTo(game, "study-first-check");
                fx(() -> {
                    stage.setWidth(size[0]); stage.setHeight(size[1]);
                    navigator.setReadingFontScale(scale); navigator.showGameplay(); return null;
                });
                await(() -> root().lookup("#assessmentScroll") != null && root().lookup("#assessmentScroll").isVisible()
                        && !navigator.workInProgress());
                Thread.sleep(100);
                if (scale == 0.8 && size[0] == 1280) {
                    Node firstAnswer = fx(() -> root().lookup("#answer-0"));
                    for (int attempt = 0; attempt < 20
                            && fx(() -> root().getScene().getFocusOwner()) != firstAnswer; attempt++) {
                        fx(() -> {
                            Robot robot = new Robot();
                            robot.keyPress(KeyCode.TAB);
                            robot.keyRelease(KeyCode.TAB);
                            return null;
                        });
                        Thread.sleep(40);
                    }
                    assertSame(firstAnswer, fx(() -> root().getScene().getFocusOwner()),
                            "Tab navigation must reach the first reading-check answer.");
                    fx(() -> {
                        root().applyCss();
                        assertVisibleFocusRing(firstAnswer, Color.web("#6e1428"));
                        Button submit = (Button) root().lookup("#submitButton");
                        assertReadableDisabledButton(submit);
                        assertTrue(submit.getAccessibleHelp().contains("Choose an answer"));
                        capture(root(), "accessibility-reading-choice");
                        return null;
                    });
                }
                fx(() -> {
                    ScrollPane scroll = (ScrollPane) root().lookup("#assessmentScroll");
                    assertTrue(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 1);
                    for (Node node : ((VBox) root().lookup("#answerChoices")).getChildren()) {
                        RadioButton answer = (RadioButton) node;
                        assertTrue(answer.getHeight() >= answer.prefHeight(answer.getWidth()) - 1);
                        assertEquals(answer.getText(), ((javafx.scene.text.Text) answer.lookup(".text")).getText());
                    }
                    ((RadioButton) root().lookup("#answer-0")).fire(); click("SUBMIT"); return null;
                });
                await(() -> root().lookup("#feedbackPane").isVisible() && !navigator.workInProgress());
                Thread.sleep(100);
                fx(() -> {
                    ScrollPane scroll = (ScrollPane) root().lookup("#assessmentScroll");
                    Label explanation = label("explanationText");
                    assertTrue(explanation.getHeight() >= explanation.prefHeight(explanation.getWidth()) - 1,
                            "Feedback text must remain fully readable after submission.");
                    Bounds viewport = scroll.lookup(".viewport").localToScene(scroll.lookup(".viewport").getLayoutBounds());
                    Bounds action = button("VIEW RESULTS").localToScene(button("VIEW RESULTS").getBoundsInLocal());
                    assertTrue(viewport.contains(action.getCenterX(), action.getCenterY()), "Results action is reachable");
                    if (scale == 1.6 && size[0] == 760) captureNative("responsive-hard-feedback");
                    click("VIEW RESULTS"); return null;
                });
                await(this::resultsReady);
                Thread.sleep(100);
                fx(() -> {
                    assertTrue(root().getLayoutBounds().getWidth() <= stage.getScene().getWidth() + 1);
                    assertTrue(label("scoreLabel").getHeight() >= label("scoreLabel").prefHeight(label("scoreLabel").getWidth()) - 1);
                    if (scale == 1.6 && size[0] == 760) captureNative("responsive-results");
                    return null;
                });
            }
        }
    }

    @Test void robotOpensTheFullIllustrationWithoutChangingStoryProgress() throws Exception {
        BackendContext backend = launch();
        var game = backend.accountGameService();
        game.signup("illustration-reader", "Illustration Reader", "StrongPass1!".toCharArray());
        game.login("illustration-reader", "StrongPass1!".toCharArray());
        backend.storyPackImporter().orElseThrow().importFile(Path.of("assets/content/hailey-investigation-draft.json"));
        game.startChapter(backend.chapters().findByDifficulty(Difficulty.EASY).getFirst().id(), Difficulty.EASY, false);
        advanceDraftTo(game, "poisoning-cg");
        fx(() -> { stage.setWidth(760); stage.setHeight(520); navigator.showGameplay(); return null; });
        await(() -> root().lookup("#illustrationButton") != null && root().lookup("#illustrationButton").isVisible()
                && !navigator.workInProgress());
        robotClick(() -> button("VIEW ILLUSTRATION"));
        await(() -> Window.getWindows().stream().anyMatch(w -> w != stage && w.isShowing()));
        fx(() -> {
            ImageView full = (ImageView) ((javafx.scene.layout.StackPane) dialogPane().getContent()).getChildren().getFirst();
            assertTrue(full.isPreserveRatio());
            assertTrue(full.getBoundsInParent().getWidth() <= stage.getWidth());
            assertTrue(full.getBoundsInParent().getHeight() <= stage.getHeight());
            return null;
        });
        robotClick(() -> dialogPane().lookupButton(ButtonType.CLOSE));
        assertEquals("poisoning-cg", game.currentGame().orElseThrow().storyNodeId());
    }

    private void awaitDialog(String header) throws Exception {
        await(() -> Window.getWindows().stream().anyMatch(window -> window != stage && window.isShowing()
                && window.getScene().getRoot() instanceof DialogPane pane && header.equals(pane.getHeaderText())));
    }

    private void robotType(String id, String text) throws Exception {
        robotClick(() -> root().lookup("#" + id));
        for (char character : text.toCharArray()) {
            KeyCode key = KeyCode.getKeyCode(String.valueOf(character).toUpperCase(java.util.Locale.ROOT));
            boolean shift = Character.isUpperCase(character) || character == '!';
            if (character == '-') key = KeyCode.MINUS;
            if (character == '!') key = KeyCode.DIGIT1;
            if (character == ' ') key = KeyCode.SPACE;
            KeyCode actual = key;
            assertNotNull(actual, "Robot typing supports this fixture character");
            fx(() -> {
                Robot robot = new Robot();
                if (Boolean.getBoolean("arg.ui.nativeTests")) assertTrue(stage.isFocused(), "Do not type outside the QA window");
                if (shift) robot.keyPress(KeyCode.SHIFT);
                robot.keyPress(actual); robot.keyRelease(actual);
                if (shift) robot.keyRelease(KeyCode.SHIFT);
                return null;
            });
        }
        await(() -> ((TextField) root().lookup("#" + id)).getText().equals(text));
    }

    private void robotClick(java.util.function.Supplier<Node> target) throws Exception {
        if (Boolean.getBoolean("arg.ui.nativeTests")) Thread.sleep(180);
        for (int attempt = 0; attempt < 80; attempt++) {
            boolean clicked = fx(() -> {
                Node node = target.get();
                assertNotNull(node);
                if (Boolean.getBoolean("arg.ui.nativeTests") && !node.getScene().getWindow().isFocused()) {
                    node.getScene().getWindow().requestFocus();
                    return false;
                }
                Bounds bounds = node.localToScreen(node.getBoundsInLocal());
                if (Boolean.getBoolean("arg.ui.nativeTests")) System.out.println("NATIVE_CLICK " + node
                        + " screen=" + bounds + " window=" + node.getScene().getWindow().getX() + ","
                        + node.getScene().getWindow().getY());
                Robot robot = new Robot();
                for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
                    if (parent instanceof ScrollPane scroll) {
                        Node viewport = scroll.lookup(".viewport");
                        Bounds visibleBounds = viewport.localToScreen(viewport.getLayoutBounds());
                        double y = bounds.getCenterY();
                        if (y < visibleBounds.getMinY() + 3 || y > visibleBounds.getMaxY() - 3) {
                            robot.mouseMove(visibleBounds.getMaxX() - 12, visibleBounds.getCenterY());
                            robot.mouseWheel(y > visibleBounds.getMaxY() ? 5 : -5);
                            return false;
                        }
                    }
                }
                Bounds sceneBounds = node.getScene().getRoot().localToScreen(node.getScene().getRoot().getLayoutBounds());
                assertTrue(sceneBounds.contains(bounds.getCenterX(), bounds.getCenterY()), "Target outside actual scene: " + node);
                assertFalse(node.isDisabled(), "Cannot click a disabled control");
                robot.mouseMove(bounds.getCenterX(), bounds.getCenterY());
                robot.mouseClick(MouseButton.PRIMARY);
                return true;
            });
            Thread.sleep(60);
            if (clicked) return;
        }
        fx(() -> {
            captureNative("robot-unreachable-control");
            Node node = target.get();
            System.err.println("Unreachable target " + node + " bounds " + node.localToScreen(node.getBoundsInLocal()));
            for (Node parent = node.getParent(); parent != null; parent = parent.getParent())
                if (parent instanceof ScrollPane scroll) System.err.println("Scroll " + scroll.getId()
                        + " vvalue=" + scroll.getVvalue() + " viewport=" + scroll.getViewportBounds()
                        + " bounds=" + scroll.lookup(".viewport").localToScreen(scroll.lookup(".viewport").getLayoutBounds()));
            return null;
        });
        fail("Could not scroll the actual control into view");
    }

    private void captureNative(String name) throws Exception {
        String destination = System.getProperty("arg.ui.captureDir");
        if (destination == null || destination.isBlank()) return;
        var shot = new Robot().getScreenCapture(null, stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        BufferedImage output = new BufferedImage((int) shot.getWidth(), (int) shot.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++)
            for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
        Path folder = Path.of(destination).toAbsolutePath();
        Files.createDirectories(folder);
        ImageIO.write(output, "png", folder.resolve(name + ".png").toFile());
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

    @Test void draftGameplayShowsProvidedCharacterAndSceneArtWithoutAudio() throws Exception {
        BackendContext backend = launch();
        backend.accountGameService().signup("art-reader", "Art Reader", "StrongPass1!".toCharArray());
        backend.accountGameService().login("art-reader", "StrongPass1!".toCharArray());
        backend.storyPackImporter().orElseThrow().importFile(Path.of("assets/content/hailey-investigation-draft.json"));
        var chapter = backend.chapters().findByDifficulty(Difficulty.EASY).stream()
                .filter(Chapter::adaptive).findFirst().orElseThrow();
        backend.accountGameService().startChapter(chapter.id(), Difficulty.EASY, false);
        advanceDraftTo(backend.accountGameService(), "hailey-arrives");

        fx(() -> { navigator.showGameplay(); return null; });
        await(() -> root().lookup("#speakerImage") instanceof ImageView image
                && image.isVisible() && image.getImage() != null && !navigator.workInProgress());
        fx(() -> {
            assertEquals("Hailey", label("speakerName").getText());
            ImageView portrait = (ImageView) root().lookup("#speakerImage");
            assertNotNull(portrait.getImage());
            assertEquals(0, portrait.getImage().getPixelReader().getColor(0, 0).getOpacity(), 0.01,
                    "Character art keeps its transparent cutout around the pose.");
            assertTrue(portrait.getFitHeight() >= 450, "The character pose uses the expanded story stage.");
            Node panel = root().lookup("#storyPanel");
            Node portraitColumn = root().lookup("#portraitColumn");
            assertTrue(panel.getBoundsInParent().getHeight() < portraitColumn.getBoundsInParent().getHeight() - 80,
                    "The dialogue panel remains compact beside the full character pose.");
            StackPane portraitFrame = (StackPane) root().lookup("#portraitFrame");
            assertTrue(portraitFrame.getBackground() == null || portraitFrame.getBackground().getFills().stream()
                            .allMatch(fill -> fill.getFill() instanceof Color color && color.getOpacity() == 0),
                    "The character cutout sits directly over the scene without the old paper card.");
            Node speakerLabel = root().lookup("#speakerName");
            assertEquals("storyPanel", speakerLabel.getParent().getParent().getId(),
                    "The speaker name sits in the dialogue nameplate.");
            ScrollPane dialogue = (ScrollPane) root().lookup("#dialogueScroll");
            assertTrue(dialogue.getPrefViewportHeight() >= 96 && dialogue.getPrefViewportHeight() < 220,
                    "Short lines keep the dialogue panel compact.");
            assertNull(root().lookup("#voiceButton"), "The reading screen has no audio control.");
            capture(root(), "draft-hailey-scene");
            return null;
        });

        advanceDraftTo(backend.accountGameService(), "poisoning-cg");
        fx(() -> { navigator.showGameplay(); return null; });
        await(() -> root().lookup("#cgImage") instanceof ImageView image
                && image.isVisible() && image.getImage() != null && !navigator.workInProgress());
        fx(() -> {
            assertTrue(((ImageView) root().lookup("#cgImage")).isVisible());
            capture(root(), "draft-poisoning-cg");
            return null;
        });
    }

    @Test void suppliedDraftShowsLongChoicesProvisionalFeedbackAndTheBoundary() throws Exception {
        BackendContext backend = launch();
        var game = backend.accountGameService();
        game.signup("full-draft-reader", "Draft Reader", "StrongPass1!".toCharArray());
        game.login("full-draft-reader", "StrongPass1!".toCharArray());
        backend.storyPackImporter().orElseThrow().importFile(Path.of("assets/content/hailey-investigation-draft.json"));
        Chapter chapter = backend.chapters().findByDifficulty(Difficulty.EASY).stream()
                .filter(Chapter::adaptive).findFirst().orElseThrow();
        game.startChapter(chapter.id(), Difficulty.EASY, false);
        advanceDraftTo(game, "study-first-search-choice");
        fx(() -> { navigator.showGameplay(); return null; });
        await(() -> root().lookup("#story-choice-5") != null && !navigator.workInProgress());
        fx(() -> {
            assertTrue(root().lookupAll(".story-choice-button").stream()
                    .allMatch(node -> node instanceof Button choice && choice.isWrapText()));
            assertNull(root().lookup("#voiceButton"));
            assertFalse(root().lookup("#portraitColumn").isManaged(), "Narration must not show an empty portrait frame.");
            capture(root(), "draft-five-authored-choices");
            return null;
        });
        for (String checkpoint : List.of("study-first", "hall-second")) {
            advanceDraftTo(game, checkpoint + "-check");
            fx(() -> { navigator.showGameplay(); return null; });
            await(() -> root().lookup("#passageText") instanceof Label passage
                    && passage.getText().contains("answer keys are provisional") && !navigator.workInProgress());
            var state = game.currentGame().orElseThrow();
            Question question = chapter.questions(checkpoint, state.activeDifficulty()).getFirst();
            int selected = checkpoint.equals("study-first") ? question.correctChoiceIndex()
                    : (question.correctChoiceIndex() + 1) % question.choices().size();
            fx(() -> {
                capture(root(), "draft-" + checkpoint + "-question");
                ((RadioButton) root().lookup("#answer-" + selected)).fire();
                click("SUBMIT");
                return null;
            });
            await(() -> label("explanationText").getText().contains("Provisional answer key")
                    && !navigator.workInProgress());
            fx(() -> { click("VIEW RESULTS"); return null; });
            await(this::resultsReady);
            fx(() -> {
                assertTrue(label("chapterTitle").getText().contains("Draft keys"));
                if (checkpoint.equals("hall-second")) {
                    capture(root(), "draft-second-results");
                    assertTrue(label("scoreLabel").getText().contains("50%"), label("scoreLabel").getText());
                }
                click("CONTINUE STORY");
                return null;
            });
            await(() -> root().lookup("#gameplayRoot") != null && !navigator.workInProgress());
        }
        advanceDraftTo(game, "draft-complete");
        fx(() -> { navigator.showGameplay(); return null; });
        await(this::resultsReady);
        fx(() -> {
            assertTrue(label("nextChapterStatus").getText().contains("CURRENT STORY DRAFT ENDS HERE"));
            assertTrue(label("scoreLabel").getText().contains("50%"));
            assertTrue(label("nextChapterStatus").getText().contains("author has not supplied"));
            capture(root(), "draft-boundary-results");
            return null;
        });
    }

    @Test void hardDraftQuestionRemainsReadableInASmallerWindow() throws Exception {
        BackendContext backend = launch();
        var game = backend.accountGameService();
        game.signup("hard-layout-reader", "Layout Reader", "StrongPass1!".toCharArray());
        game.login("hard-layout-reader", "StrongPass1!".toCharArray());
        backend.storyPackImporter().orElseThrow().importFile(Path.of("assets/content/hailey-investigation-draft.json"));
        Chapter chapter = backend.chapters().findByDifficulty(Difficulty.EASY).stream()
                .filter(Chapter::adaptive).findFirst().orElseThrow();
        game.startChapter(chapter.id(), Difficulty.HARD, false);
        advanceDraftTo(game, "study-first-check");
        fx(() -> {
            stage.setWidth(800); stage.setHeight(600);
            navigator.setReadingFontScale(1.2);
            navigator.showGameplay();
            return null;
        });
        await(() -> root().getLayoutBounds().getWidth() < 900 && root().lookup("#questionPrompt") instanceof Label prompt
                && prompt.getText().contains("Empty Antidote Case") && !navigator.workInProgress());
        fx(() -> {
            assertTrue(root().lookupAll(".radio-button").stream()
                    .allMatch(node -> node instanceof RadioButton answer && answer.isWrapText()
                            && answer.getWidth() < root().getLayoutBounds().getWidth()
                            && answer.getHeight() >= answer.prefHeight(answer.getWidth()) - 1));
            assertTrue(label("questionPrompt").getHeight() >= label("questionPrompt").prefHeight(label("questionPrompt").getWidth()) - 1);
            for (Node node : root().lookupAll(".quiz-option")) {
                RadioButton answer = (RadioButton) node;
                assertTrue(answer.lookup(".text") instanceof javafx.scene.text.Text text
                        && text.getText().equals(answer.getText()), "Answer text must be fully rendered.");
            }
            capture(root(), "draft-hard-small-window-top");
            ((javafx.scene.control.ScrollPane) root().lookup("#assessmentScroll")).setVvalue(1);
            return null;
        });
        fx(() -> { capture(root(), "draft-hard-small-window-bottom"); return null; });
        fx(() -> {
            Question key = chapter.questions("study-first", Difficulty.HARD).getFirst();
            ((RadioButton) root().lookup("#answer-" + key.correctChoiceIndex())).fire();
            click("SUBMIT");
            return null;
        });
        await(() -> root().lookup("#explanationText") instanceof Label explanation
                && explanation.getText().contains("Provisional answer key") && !navigator.workInProgress());
        fx(() -> {
            capture(root(), "draft-hard-small-window-feedback");
            assertTrue(root().lookupAll(".quiz-option").stream()
                    .allMatch(node -> node.isDisabled() && node.getOpacity() == 1.0));
            return null;
        });
    }

    private static void advanceDraftTo(com.adaptivereadinggame.service.AccountGameService game, String targetNodeId) {
        for (int step = 0; step < 1000; step++) {
            var view = game.currentGameView().orElseThrow();
            StoryNode node = view.storyNode();
            if (targetNodeId.equals(node.storyNodeId())) return;
            if (node.storyChoices().isEmpty()) {
                game.advanceStory();
                continue;
            }
            String preferred = switch (node.storyNodeId()) {
                case "amelia-response" -> "answer-politely";
                case "eris-request-choice" -> "bring-tart";
                case "investigation-route-choice" -> "study-first";
                default -> node.storyChoices().getFirst().choiceId();
            };
            String requestedChoice = preferred;
            if (node.storyChoices().stream().map(StoryChoice::choiceId).noneMatch(requestedChoice::equals))
                preferred = node.storyChoices().getFirst().choiceId();
            game.chooseStory(preferred);
        }
        fail("Draft story did not reach node " + targetNodeId);
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
                && root().lookup("#scoreLabel") instanceof Label score
                && score.getText().contains("50%") && !navigator.workInProgress());
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
        String fixtureTitle = "MySQL UI fixture " + shortId();
        StoryPack.ChapterPack chapter = fixtureChapter("mysql-ui-" + shortId(), fixtureTitle);
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
        fx(() -> { dialogButtonContaining(fixtureTitle).fire(); return null; });
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
                && root().lookup("#scoreLabel") instanceof Label score
                && score.getText().contains("100%") && !navigator.workInProgress());
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
        fx(() -> {
            assertTrue(label("continueStatusLabel").getText().contains("Save progress"));
            assertReadableDisabledButton((Button) root().lookup("#continueButton"));
            return null;
        });
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
            for (int slot = 1; slot <= 3; slot++) {
                Button save = (Button) pane.lookup("#saveSlot" + slot);
                Button load = (Button) pane.lookup("#loadSlot" + slot);
                assertEquals("Save progress to slot " + slot, save.getAccessibleText());
                assertEquals("Load progress from slot " + slot, load.getAccessibleText());
                assertTrue(load.getAccessibleHelp().contains("is empty"));
                assertReadableDisabledButton(save);
                assertReadableDisabledButton(load);
            }
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
        await(() -> root().lookup("#continueButton") != null && !navigator.workInProgress());
        fx(() -> {
            Button continueButton = (Button) root().lookup("#continueButton");
            assertFalse(continueButton.isDisabled());
            assertTrue(label("continueStatusLabel").getText().contains("most recently saved progress"));
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
            Button load = (Button) pane.lookup("#loadSlot1");
            assertEquals("Load progress from slot 1", load.getAccessibleText());
            load.requestFocus();
            pane.applyCss();
            assertTrue(load.isFocused());
            assertVisibleFocusRing(load, Color.web("#6e1428"));
            capture(pane, "accessibility-save-load");
            capture(pane, "populated-save-slots");
            Label details = (Label) pane.lookup("#slotDetails1");
            assertTrue(details.getHeight() >= details.prefHeight(details.getWidth()) - 1,
                    "All populated-slot details must be visible without truncation.");
            assertTrue(details.getText().contains("Story progress saved"));
            assertFalse(details.getText().contains("chapter-introduction"));
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
            stage.toFront();
            stage.requestFocus();
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
        root().applyCss();
        root().layout();
        return root().lookupAll(".button").stream().filter(node -> node instanceof Button button
                && button.getText().equals(text) && visible(node)).map(Button.class::cast).findFirst().orElseThrow();
    }

    private static void assertVisibleFocusRing(Node node, Color expectedColor) {
        assertTrue(node instanceof Region, "A focusable control must expose a visible border region.");
        Border border = ((Region) node).getBorder();
        assertNotNull(border, "The focused control must have a CSS border.");
        assertTrue(border.getStrokes().stream().anyMatch(stroke -> expectedColor.equals(stroke.getTopStroke())
                        && stroke.getWidths().getTop() >= 2),
                "The focused control must have the expected high-contrast outline.");
    }

    private static void assertReadableDisabledButton(Button button) {
        button.applyCss();
        assertTrue(button.isDisabled(), "The action must stay disabled until it is available.");
        assertEquals(1.0, button.getOpacity(), 0.001, "Disabled actions must not be faded into low contrast.");
        Color foreground = (Color) button.getTextFill();
        Color background = (Color) button.getBackground().getFills().getFirst().getFill();
        assertTrue(contrastRatio(foreground, background) >= 4.5,
                "Disabled action text must remain readable against its background.");
    }

    private static double contrastRatio(Color first, Color second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05)
                / (Math.min(firstLuminance, secondLuminance) + 0.05);
    }

    private static double relativeLuminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen())
                + 0.0722 * linear(color.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private boolean visible(Node node) {
        for (Node current = node; current != null; current = current.getParent()) if (!current.isVisible()) return false;
        return true;
    }

    private void click(String text) { button(text).fire(); }

    private DialogPane dialogPane() {
        DialogPane pane = Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).reduce((before, after) -> after).orElseThrow();
        pane.applyCss();
        pane.layout();
        return pane;
    }

    private Button dialogButton(String text) {
        return dialogPane().lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private Button dialogButtonContaining(String text) {
        return dialogPane().lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> button.getText().contains(text)).findFirst().orElseThrow();
    }

    private boolean resultsReady() {
        return root().lookup("#resultsRoot") != null
                && root().lookup("#scoreLabel") instanceof Label score && !score.getText().isBlank()
                && !navigator.workInProgress();
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

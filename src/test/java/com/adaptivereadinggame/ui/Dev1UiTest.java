package com.adaptivereadinggame.ui;

import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.backend.UnavailableGameplay;
import com.adaptivereadinggame.controller.AppNavigator;
import com.adaptivereadinggame.model.Chapter;
import com.adaptivereadinggame.model.Difficulty;
import com.adaptivereadinggame.model.Question;
import com.adaptivereadinggame.model.GameState;
import com.adaptivereadinggame.model.SaveGame;
import com.adaptivereadinggame.model.SaveSnapshot;
import com.adaptivereadinggame.repository.memory.InMemoryAccountRepository;
import com.adaptivereadinggame.repository.memory.InMemoryGameRepository;
import com.adaptivereadinggame.repository.memory.InMemorySaveRepository;
import com.adaptivereadinggame.service.BaselineDifficultyPolicy;
import com.adaptivereadinggame.service.Pbkdf2PasswordHasher;
import com.adaptivereadinggame.service.StoryService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
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
        fx(() -> {
            stage = new Stage();
            navigator = new AppNavigator(stage, backend, "Temporary storage — test accounts close with the app.");
            navigator.start();
            capture(root(), "welcome");
            return null;
        });
        return backend;
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

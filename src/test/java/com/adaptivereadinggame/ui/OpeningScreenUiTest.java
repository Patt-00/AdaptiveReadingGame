package com.adaptivereadinggame.ui;

import com.adaptivereadinggame.audio.GameAudio;
import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.view.OpeningScreen;
import com.adaptivereadinggame.settings.PlayerPreferences;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.prefs.Preferences;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "arg.uiTests", matches = "true")
class OpeningScreenUiTest {
    private Stage stage;
    private static final class SilentOutput implements GameAudio.Output {
        final AtomicInteger clicks = new AtomicInteger();
        public void setMusic(boolean playing, double volume) {}
        public void click(double volume) { clicks.incrementAndGet(); }
        public void close() {}
    }

    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }
    @AfterEach void close() throws Exception { if (stage != null) fx(() -> { stage.hide(); return null; }); }

    @Test void openingRestoresMuteAndReducedMotionBeforeEntering() throws Exception {
        Preferences node = Preferences.userRoot().node("com/adaptivereadinggame-tests/" + UUID.randomUUID());
        try {
            var preferences = new PlayerPreferences(node);
            preferences.setVolumes(0, 0.3);
            preferences.setReducedMotion(true);
            var audio = new GameAudio(new SilentOutput());
            fx(() -> {
                stage = new Stage();
                new OpeningScreen(stage, audio, reporter -> {
                    reporter.update(4, 4, "Ready");
                    return new OpeningScreen.Loaded(BackendContext.inMemory(), "Temporary storage");
                }, preferences).start();
                return null;
            });
            await(() -> root("#loadingScreen") != null && root("#loadingScreen").isVisible());
            await(() -> root("#enterGame") != null && root("#enterGame").isVisible());
            assertTrue(fx(() -> ((javafx.scene.control.CheckBox) root("#loadingReduceMotion")).isSelected()));
            assertEquals(0, audio.musicVolume());
            assertEquals(0.3, audio.effectsVolume());
            fx(() -> { ((Button) root("#enterGame")).fire(); return null; });
            await(() -> root("#accountRoot") != null);
            assertEquals(0, audio.musicVolume());
            assertTrue(new PlayerPreferences(node).reducedMotion());
            assertEquals(0, new PlayerPreferences(node).music());
        } finally {
            if (stage != null) fx(() -> { stage.hide(); return null; });
            node.removeNode();
            Preferences.userRoot().flush();
        }
    }

    @Test void configuredFullscreenOpeningUsesTheWholeDisplay() throws Exception {
        var preferences = PlayerPreferences.temporary();
        preferences.setReducedMotion(true);
        fx(() -> {
            stage = new Stage();
            new OpeningScreen(stage, new GameAudio(new SilentOutput()), reporter -> {
                reporter.update(4, 4, "Ready");
                return new OpeningScreen.Loaded(BackendContext.inMemory(), "Temporary storage");
            }, preferences, true).start();
            return null;
        });
        await(() -> root("#enterGame") != null && root("#enterGame").isVisible());
        assertEquals("Press Esc to leave full screen.", fx(() -> stage.getFullScreenExitHint()));
    }

    @Test void openingWaitsForRealLoadingBeforeSpaceEnters() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        SilentOutput output = new SilentOutput();
        fx(() -> {
            stage = new Stage();
            stage.setWidth(760); stage.setHeight(520);
            new OpeningScreen(stage, new GameAudio(output), reporter -> {
                reporter.update(1, 4, "Preparing test content");
                if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Test loader timed out");
                reporter.update(4, 4, "Ready");
                return new OpeningScreen.Loaded(BackendContext.inMemory(), "Temporary storage");
            }).start();
            stage.toFront(); stage.requestFocus();
            return null;
        });
        await(() -> root("#headphoneHint") != null && ((Label) root("#headphoneHint")).getText().contains("headphones"));
        assertNotNull(fx(() -> root("#headphoneIcon")));
        await(() -> ((Label) root("#loadingStatus")).getText().equals("Preparing test content"));
        await(() -> {
            double value = ((ProgressBar) root("#startupProgress")).getProgress();
            return value > 0 && value < 0.25;
        });
        double earlyProgress = fx(() -> ((ProgressBar) root("#startupProgress")).getProgress());
        assertTrue(earlyProgress > 0 && earlyProgress < 0.25,
                "The startup bar should visibly travel between real progress milestones.");
        Thread.sleep(700);
        double openingOpacity = fx(() -> root("#openingText").getOpacity());
        assertTrue(openingOpacity > 0.05 && openingOpacity < 0.95,
                "The opening title should still be fading in at 700 ms.");
        fx(() -> { capture("opening-text"); return null; });
        click("#skipOpening");
        await(() -> root("#loadingScreen").isVisible());
        assertEquals(1, output.clicks.get());
        await(() -> stage.getScene().getFocusOwner() == root("#loadingScreen"));
        await(() -> Math.abs(((ProgressBar) root("#startupProgress")).getProgress() - 0.25) < 0.001);
        Thread.sleep(700);
        fx(() -> { capture("loading-progress"); return null; });
        fx(() -> { new Robot().keyPress(KeyCode.SPACE); new Robot().keyRelease(KeyCode.SPACE); return null; });
        assertTrue(fx(() -> root("#loadingScreen").isVisible()));
        assertFalse(fx(() -> ((javafx.scene.control.CheckBox) root("#loadingReduceMotion")).isSelected()),
                "Space while loading must not change the motion preference.");
        assertFalse(fx(() -> root("#enterGame").isVisible()));
        assertEquals(0.25, fx(() -> ((ProgressBar) root("#startupProgress")).getProgress()), 0.001);
        String firstCaption = fx(() -> ((Label) root("#loadingCaption")).getText());
        if (firstCaption.equals("Meet the characters"))
            await(() -> !((Label) root("#loadingCaption")).getText().equals(firstCaption));
        release.countDown();
        await(() -> root("#enterGame").isVisible());
        assertEquals(1, fx(() -> ((ProgressBar) root("#startupProgress")).getProgress()), 0.001);
        fx(() -> { capture("loading-ready"); return null; });
        fx(() -> { root("#enterGame").requestFocus(); new Robot().keyPress(KeyCode.SPACE); new Robot().keyRelease(KeyCode.SPACE); return null; });
        await(() -> root("#accountRoot") != null);
        fx(() -> {
            Node login = ((javafx.scene.Parent) root("#selectionBox")).lookupAll(".button").stream()
                    .filter(node -> node instanceof Button button && "LOG IN".equals(button.getText()) && node.isVisible())
                    .findFirst().orElseThrow();
            var bounds = login.localToScreen(login.getBoundsInLocal());
            Robot robot = new Robot();
            robot.mouseMove(bounds.getCenterX(), bounds.getCenterY());
            robot.mouseClick(MouseButton.PRIMARY);
            return null;
        });
        await(() -> output.clicks.get() == 2);
    }

    @Test void failedLoadingOffersRetryWithoutEntering() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        fx(() -> {
            stage = new Stage();
            new OpeningScreen(stage, new GameAudio(new SilentOutput()), reporter -> {
                reporter.update(1, 4, "Preparing test content");
                if (attempts.getAndIncrement() == 0)
                    throw new OpeningScreen.LoadFailure("Default story pack is missing. Restore it or set ARG_CONTENT_PACK.");
                reporter.update(4, 4, "Ready");
                return new OpeningScreen.Loaded(BackendContext.inMemory(), "Temporary storage");
            }).start();
            stage.toFront(); stage.requestFocus();
            return null;
        });
        click("#skipOpening");
        await(() -> root("#retryStartup").isVisible());
        assertTrue(fx(() -> ((Label) root("#loadingStatus")).getText()
                .contains("Default story pack is missing")));
        assertFalse(fx(() -> root("#enterGame").isVisible()));
        click("#retryStartup");
        await(() -> root("#enterGame").isVisible());
        assertEquals(2, attempts.get());
    }

    @Test void completedTaskCannotBeEnteredUntilTheLoadingAnimationFinishes() throws Exception {
        CountDownLatch finishStartup = new CountDownLatch(1);
        fx(() -> {
            stage = new Stage();
            new OpeningScreen(stage, new GameAudio(new SilentOutput()), reporter -> {
                reporter.update(1, 4, "Preparing account services");
                if (!finishStartup.await(20, TimeUnit.SECONDS))
                    throw new IllegalStateException("Test loader timed out");
                reporter.update(4, 4, "Startup work complete");
                return new OpeningScreen.Loaded(BackendContext.inMemory(), "Temporary storage");
            }).start();
            stage.toFront(); stage.requestFocus();
            return null;
        });
        await(() -> ((Label) root("#loadingStatus")).getText().equals("Preparing account services"));
        click("#skipOpening");
        finishStartup.countDown();
        await(() -> ((Label) root("#loadingStatus")).getText().equals("Finishing the loading sequence..."));
        fx(() -> {
            new Robot().keyPress(KeyCode.SPACE);
            new Robot().keyRelease(KeyCode.SPACE);
            new Robot().keyPress(KeyCode.ENTER);
            new Robot().keyRelease(KeyCode.ENTER);
            return null;
        });
        assertTrue(fx(() -> root("#loadingScreen").isVisible()));
        assertFalse(fx(() -> root("#enterGame").isVisible()),
                "The game must stay on the loading screen until its bar animation completes.");
        await(() -> root("#enterGame").isVisible());
        assertEquals(1, fx(() -> ((ProgressBar) root("#startupProgress")).getProgress()), 0.001);
        fx(() -> {
            new Robot().keyPress(KeyCode.ENTER);
            new Robot().keyRelease(KeyCode.ENTER);
            return null;
        });
        await(() -> root("#accountRoot") != null);
    }

    private Node root(String id) { return stage.getScene().getRoot().lookup(id); }
    private void capture(String name) throws Exception {
        String folder = System.getProperty("arg.ui.captureDir");
        if (folder == null || folder.isBlank()) return;
        var view = stage.getScene().getRoot();
        var shot = view.snapshot(null, null);
        var output = new java.awt.image.BufferedImage((int) shot.getWidth(), (int) shot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++)
            for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
        var path = java.nio.file.Path.of(folder).toAbsolutePath();
        java.nio.file.Files.createDirectories(path);
        javax.imageio.ImageIO.write(output, "png", path.resolve(name + ".png").toFile());
    }
    private void click(String id) throws Exception {
        fx(() -> {
            Node node = root(id);
            assertNotNull(node);
            var bounds = node.localToScreen(node.getBoundsInLocal());
            assertNotNull(bounds);
            Robot robot = new Robot();
            robot.mouseMove(bounds.getCenterX(), bounds.getCenterY());
            robot.mouseClick(MouseButton.PRIMARY);
            return null;
        });
    }
    private void await(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (fx(condition::getAsBoolean)) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for the opening screen.");
    }
    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}

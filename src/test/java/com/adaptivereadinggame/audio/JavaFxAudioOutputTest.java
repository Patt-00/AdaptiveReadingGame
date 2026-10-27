package com.adaptivereadinggame.audio;

import javafx.application.Platform;
import javafx.scene.media.MediaPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Requires a working desktop audio server and an X display. */
@EnabledIfSystemProperty(named = "arg.audioDeviceTests", matches = "true")
class JavaFxAudioOutputTest {
    @Test void bundledMusicCanPlayPauseAndResume() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        JavaFxAudioOutput output = new JavaFxAudioOutput();
        try {
            fx(() -> { output.setMusic(true, 0.02); return null; });
            await(output, MediaPlayer.Status.PLAYING);
            fx(() -> { output.click(0.02); return null; });
            boolean clickPlayed = false;
            for (int i = 0; i < 500; i++) {
                if (fx(output::clickStatus) == MediaPlayer.Status.PLAYING) { clickPlayed = true; break; }
                Thread.sleep(10);
            }
            assertTrue(clickPlayed, "The click sound must reach the playing state.");
            fx(() -> { output.setMusic(false, 0.02); return null; });
            await(output, MediaPlayer.Status.PAUSED);
            fx(() -> { output.setMusic(true, 0.02); return null; });
            await(output, MediaPlayer.Status.PLAYING);
        } finally {
            fx(() -> { output.close(); return null; });
        }
    }

    private static void await(JavaFxAudioOutput output, MediaPlayer.Status expected) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (fx(output::musicStatus) == expected) return;
            Thread.sleep(100);
        }
        fail("Audio player did not reach " + expected + "; actual state: " + fx(output::musicStatus));
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}

package com.adaptivereadinggame;

import javafx.application.Application;
import javafx.stage.Stage;
import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.audio.GameAudio;
import com.adaptivereadinggame.audio.JavaFxAudioOutput;
import com.adaptivereadinggame.repository.mysql.MySqlConfig;
import com.adaptivereadinggame.settings.PlayerPreferences;
import com.adaptivereadinggame.view.OpeningScreen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Application entry point. */
public class AdaptiveReadingGameApp extends Application {
    @Override
    public void start(Stage stage) {
        new OpeningScreen(stage, new GameAudio(new JavaFxAudioOutput()), AdaptiveReadingGameApp::loadGame,
                PlayerPreferences.system()).start();
    }

    private static OpeningScreen.Loaded loadGame(OpeningScreen.Reporter progress) throws Exception {
        progress.update(0, 4, "Checking the game settings...");
        var database = MySqlConfig.fromEnvironment();
        progress.update(1, 4, "Preparing account and game services...");
        BackendContext backend = database.map(BackendContext::mysql)
                .orElseGet(BackendContext::inMemoryWithModel);
        progress.update(2, 4, "Finding the story content...");
        String configuredContentPack = System.getenv("ARG_CONTENT_PACK");
        Path contentPack;
        try {
            contentPack = resolveContentPack(configuredContentPack, Path.of("."));
        } catch (IOException failure) {
            throw new OpeningScreen.LoadFailure(failure.getMessage(), failure);
        }
        progress.update(3, 4, "Loading the story and reading checks...");
        var importer = backend.storyPackImporter().orElseThrow(() ->
                new IllegalStateException("The selected storage adapter cannot import prepared story content."));
        try {
            int loadedChapters = importer.importFile(contentPack);
            System.out.println("Loaded " + loadedChapters + " story chapter(s) from " + contentPack + ".");
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            throw new OpeningScreen.LoadFailure("The story pack could not be loaded from " + contentPack
                    + ". Check its JSON format and the ARG_CONTENT_PACK path.", failure);
        }
        String storage = database.isPresent()
                ? "MySQL storage — accounts, assessments, and saves persist across restarts."
                : "Temporary storage — accounts and saves disappear when the app closes.";
        progress.update(4, 4, "Game ready.");
        return new OpeningScreen.Loaded(backend, storage);
    }

    static Path resolveContentPack(String configuredContentPack, Path projectRoot) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        if (configuredContentPack != null && !configuredContentPack.isBlank()) {
            Path configured = Path.of(configuredContentPack);
            Path selected = (configured.isAbsolute() ? configured : root.resolve(configured)).normalize();
            if (!Files.isRegularFile(selected))
                throw new IOException("The story pack configured by ARG_CONTENT_PACK was not found: " + selected);
            return selected;
        }
        Path defaultPack = root.resolve("assets/content/hailey-investigation-complete.json").normalize();
        if (!Files.isRegularFile(defaultPack))
            throw new IOException("The default story pack is missing: " + defaultPack
                    + ". Restore the pack or set ARG_CONTENT_PACK to a valid story pack.");
        return defaultPack;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

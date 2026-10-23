package com.adaptivereadinggame;

import javafx.application.Application;
import javafx.stage.Stage;
import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.controller.AppNavigator;
import com.adaptivereadinggame.repository.mysql.MySqlConfig;

import java.io.IOException;
import java.nio.file.Path;

/** Application entry point. */
public class AdaptiveReadingGameApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        var database = MySqlConfig.fromEnvironment();
        BackendContext backend = database.map(BackendContext::mysql)
                .orElseGet(BackendContext::inMemoryWithModel);
        String contentPack = System.getenv("ARG_CONTENT_PACK");
        if (contentPack != null && !contentPack.isBlank()) {
            var importer = backend.storyPackImporter().orElseThrow(() ->
                    new IllegalStateException("The selected storage adapter cannot import prepared story content."));
            try {
                int loadedChapters = importer.importFile(Path.of(contentPack));
                System.out.println("Loaded " + loadedChapters + " prepared story chapter(s).");
            } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
                throw new IllegalStateException("The configured story pack could not be loaded. Check ARG_CONTENT_PACK and the pack format.", failure);
            }
        }
        String storage = database.isPresent()
                ? "MySQL storage — accounts, assessments, and saves persist across restarts."
                : "Temporary storage — accounts and saves disappear when the app closes.";
        new AppNavigator(stage, backend, storage).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

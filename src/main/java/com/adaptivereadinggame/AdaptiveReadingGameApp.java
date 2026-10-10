package com.adaptivereadinggame;

import javafx.application.Application;
import javafx.stage.Stage;
import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.controller.AppNavigator;

import java.io.IOException;

/** Application entry point. */
public class AdaptiveReadingGameApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // Dev 3 can inject the MySQL context here without changing the screens.
        new AppNavigator(stage, BackendContext.inMemory(),
                "Temporary storage — accounts and saves disappear when the app closes.").start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

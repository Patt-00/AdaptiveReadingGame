package com.adaptivereadinggame;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/** Application entry point. */
public class AdaptiveReadingGameApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                AdaptiveReadingGameApp.class.getResource("/com/adaptivereadinggame/view/dashboard.fxml"));
        Scene scene = new Scene(loader.load(), 960, 640);
        scene.getStylesheets().add(AdaptiveReadingGameApp.class
                .getResource("/com/adaptivereadinggame/style/application.css").toExternalForm());

        stage.setTitle("Adaptive Reading Game");
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

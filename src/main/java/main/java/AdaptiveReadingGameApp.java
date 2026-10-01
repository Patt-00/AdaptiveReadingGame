package main.java;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Entry point used by the JavaFX runner configuration. */
public class AdaptiveReadingGameApp extends Application {
    @Override
    public void start(Stage stage) {
        ReadingProgressModel model = new ReadingProgressModel();
        DashboardView view = new DashboardView();
        Controller controller = new Controller(model, view);

        Scene scene = new Scene(controller.getView(), 820, 560);
        stage.setTitle("Adaptive Reading Game");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

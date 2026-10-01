package com.adaptivereadinggame.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Handles the main menu while the remaining game screens are being built. */
public class MenuController {
    @FXML private StackPane menuRoot;
    @FXML private Pane menuCanvas;

    @FXML
    private void initialize() {
        // Keep the reference layout centered and proportional at any window size.
        NumberBinding scale = Bindings.min(
                menuRoot.widthProperty().divide(1920),
                menuRoot.heightProperty().divide(1080));
        menuCanvas.scaleXProperty().bind(scale);
        menuCanvas.scaleYProperty().bind(scale);
    }

    @FXML
    private void newGame() {
        showPendingScreen("New Game", "The How to Play and story screens are not built yet.");
    }

    @FXML
    private void saveLoad() {
        showPendingScreen("Save / Load", "No saved games are available. Account saves are not built yet.");
    }

    @FXML
    private void settings() {
        showPendingScreen("Settings", "The settings screen is not built yet.");
    }

    @FXML
    private void quit() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Close Adaptive Reading Game?", ButtonType.YES, ButtonType.NO);
        confirmation.initOwner(menuRoot.getScene().getWindow());
        confirmation.setTitle("Quit");
        confirmation.setHeaderText("Quit the game?");
        confirmation.showAndWait().filter(ButtonType.YES::equals)
                .ifPresent(answer -> ((Stage) menuRoot.getScene().getWindow()).close());
    }

    private void showPendingScreen(String title, String message) {
        Alert notice = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        notice.initOwner(menuRoot.getScene().getWindow());
        notice.setTitle(title);
        notice.setHeaderText(title);
        notice.showAndWait();
    }
}

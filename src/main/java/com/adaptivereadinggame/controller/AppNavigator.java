package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.AdaptiveReadingGameApp;
import com.adaptivereadinggame.backend.BackendContext;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/** One application context and navigation boundary shared by all Dev 1 screens. */
public final class AppNavigator {
    private final Stage stage;
    private final BackendContext backend;
    private final String storageDescription;
    private final boolean georgiaMissing = Font.getFamilies().stream()
            .noneMatch(family -> family.equalsIgnoreCase("Georgia"));
    private final boolean timesMissing = Font.getFamilies().stream()
            .noneMatch(family -> family.equalsIgnoreCase("Times New Roman"));
    private boolean closing;
    private boolean workInProgress;

    public AppNavigator(Stage stage, BackendContext backend, String storageDescription) {
        this.stage = Objects.requireNonNull(stage);
        this.backend = Objects.requireNonNull(backend);
        this.storageDescription = Objects.requireNonNull(storageDescription);
    }

    public BackendContext backend() { return backend; }
    public Stage stage() { return stage; }
    public String storageDescription() { return storageDescription; }
    public boolean workInProgress() { return workInProgress; }
    public void setWorkInProgress(boolean busy) { workInProgress = busy; }

    public String fontNotice() {
        if (timesMissing) return "Reference fonts unavailable: using system Serif.";
        return georgiaMissing ? "Georgia unavailable: body text uses Times New Roman." : "";
    }

    public void start() throws IOException {
        stage.setTitle("Adaptive Reading Game");
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.setOnCloseRequest(event -> {
            if (!closing) {
                event.consume();
                requestQuit();
            }
        });
        showAccount();
        stage.show();
        if (!fontNotice().isEmpty()) System.err.println(fontNotice());
    }

    public void showAccount() throws IOException { show("account.fxml"); }
    public void showMenu() throws IOException { show("main-menu.fxml"); }

    private void show(String resource) throws IOException {
        FXMLLoader loader = new FXMLLoader(AdaptiveReadingGameApp.class.getResource(
                "/com/adaptivereadinggame/view/" + resource));
        loader.setControllerFactory(type -> {
            if (type == AccountController.class) return new AccountController(this);
            if (type == MenuController.class) return new MenuController(this);
            throw new IllegalArgumentException("No controller registered for " + type.getName());
        });
        Parent root = loader.load();
        applyFontFallback(root);
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, 1280, 720);
            scene.getStylesheets().add(stylesheet());
            stage.setScene(scene);
        } else stage.getScene().setRoot(root);
    }

    public void styleDialog(Dialog<?> dialog) {
        dialog.initOwner(stage);
        dialog.getDialogPane().getStylesheets().add(stylesheet());
        dialog.getDialogPane().getStyleClass().add("reference-dialog");
        applyFontFallback(dialog.getDialogPane());
    }

    public boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(title);
        styleDialog(alert);
        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }

    public void notice(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        styleDialog(alert);
        alert.showAndWait();
    }

    public void requestQuit() {
        if (workInProgress) {
            notice("Please wait", "An account or save operation is still running. Please wait before closing.");
            return;
        }
        boolean signedIn = backend.accountGameService().currentAccount().isPresent();
        boolean unsaved = signedIn && backend.accountGameService().hasUnsavedProgress();
        String message = unsaved
                ? "Unsaved progress will be lost. Close Adaptive Reading Game?"
                : "Close Adaptive Reading Game?";
        if (!confirm("Quit the game?", message)) return;
        try {
            if (signedIn) backend.accountGameService().logout(true);
        } catch (RuntimeException cleanupFailure) {
            // The facade signs out in finally. Never expose storage or credential details.
            System.err.println("Session cleanup could not complete; the account has been signed out.");
        } finally {
            closing = true;
            stage.close();
        }
    }

    private void applyFontFallback(Parent root) {
        if (georgiaMissing) root.getStyleClass().add("georgia-fallback");
        if (timesMissing) root.getStyleClass().add("serif-fallback");
    }

    private String stylesheet() {
        return AdaptiveReadingGameApp.class.getResource(
                "/com/adaptivereadinggame/style/application.css").toExternalForm();
    }
}

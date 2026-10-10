package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.model.Account;
import com.adaptivereadinggame.service.AuthException;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Reference authentication layout; account rules and credentials stay in Java services. */
public final class AccountController {
    private final AppNavigator navigator;
    private boolean signupMode;
    @FXML private StackPane accountRoot;
    @FXML private Pane accountCanvas;
    @FXML private VBox selectionBox;
    @FXML private VBox formBox;
    @FXML private TextField usernameField;
    @FXML private TextField displayNameField;
    @FXML private Label displayNameLabel;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;
    @FXML private Label storageLabel;
    @FXML private Label fontLabel;
    @FXML private Label formTitle;
    @FXML private Button submitButton;

    public AccountController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        var scale = Bindings.min(accountRoot.widthProperty().divide(1920),
                accountRoot.heightProperty().divide(1080));
        accountCanvas.scaleXProperty().bind(scale);
        accountCanvas.scaleYProperty().bind(scale);
        storageLabel.setText(navigator.storageDescription());
        fontLabel.setText(navigator.fontNotice());
        showSelection();
    }

    @FXML private void chooseLogin() { showForm(false); }
    @FXML private void chooseSignup() { showForm(true); }

    @FXML private void showSelection() {
        if (navigator.workInProgress()) return;
        passwordField.clear();
        usernameField.clear();
        displayNameField.clear();
        selectionBox.setVisible(true);
        formBox.setVisible(false);
        status("", false);
    }

    private void showForm(boolean signup) {
        signupMode = signup;
        selectionBox.setVisible(false);
        formBox.setVisible(true);
        displayNameField.setVisible(signup);
        displayNameField.setManaged(signup);
        displayNameLabel.setVisible(signup);
        displayNameLabel.setManaged(signup);
        formTitle.setText(signup ? "CREATE AN ACCOUNT" : "LOG IN");
        submitButton.setText(signup ? "SIGN UP" : "LOG IN");
        status("", false);
        usernameField.requestFocus();
    }

    @FXML private void submit() {
        if (navigator.workInProgress()) return;
        String username = usernameField.getText();
        String displayName = displayNameField.getText();
        char[] password = passwordField.getText().toCharArray();
        if (username == null || username.isBlank() || password.length == 0
                || (signupMode && (displayName == null || displayName.isBlank()))) {
            Arrays.fill(password, '\0');
            passwordField.clear();
            status(signupMode ? "Enter a username, display name, and password."
                    : "Enter your username and password.", true);
            return;
        }
        boolean signup = signupMode;
        passwordField.clear();
        formBox.setDisable(true);
        navigator.setWorkInProgress(true);
        status(signup ? "Creating your account…" : "Checking your account…", false);
        Task<Account> task = new Task<>() {
            @Override protected Account call() {
                try {
                    return signup ? navigator.backend().accountGameService().signup(username, displayName, password)
                            : navigator.backend().accountGameService().login(username, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        };
        task.setOnSucceeded(event -> {
            finishTask();
            if (signup) {
                showForm(false);
                usernameField.setText(task.getValue().username());
                status("Account created successfully! Please log in.", false);
                passwordField.requestFocus();
            } else {
                try { navigator.showMenu(); }
                catch (IOException failure) {
                    navigator.backend().accountGameService().logout(true);
                    status("The menu could not open. Please try again.", true);
                }
            }
        });
        task.setOnFailed(event -> {
            finishTask();
            // Known validation/authentication messages are safe for the user; no stack traces or secrets.
            String message = task.getException() instanceof AuthException
                    ? task.getException().getMessage() : "The account operation failed. Please try again.";
            status(message == null ? "The account operation failed. Please try again." : message, true);
            passwordField.requestFocus();
        });
        Thread worker = new Thread(task, "account-operation");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishTask() {
        navigator.setWorkInProgress(false);
        formBox.setDisable(false);
        passwordField.clear();
    }

    private void status(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("auth-error", "auth-success");
        statusLabel.getStyleClass().add(error ? "auth-error" : "auth-success");
    }
}

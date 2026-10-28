package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.content.CharacterAssetResolver;
import com.adaptivereadinggame.model.PlayerAppearance;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** The account's cosmetic servant appearance does not affect story or scoring. */
public final class AppearanceController {
    private final AppNavigator navigator;
    @FXML private StackPane appearanceRoot;
    @FXML private ToggleGroup appearanceGroup;
    @FXML private RadioButton choiceA;
    @FXML private RadioButton choiceB;
    @FXML private ImageView portraitA;
    @FXML private ImageView portraitB;
    @FXML private Button confirmButton;
    @FXML private Button backButton;

    public AppearanceController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        choiceA.setUserData(PlayerAppearance.A);
        choiceB.setUserData(PlayerAppearance.B);
        navigator.playerAppearance().ifPresent(previous -> {
            if (previous == PlayerAppearance.A) choiceA.setSelected(true);
            else choiceB.setSelected(true);
            backButton.setText("BACK TO MENU");
        });
        confirmButton.disableProperty().bind(appearanceGroup.selectedToggleProperty().isNull());
        try {
            Path root = Path.of(System.getenv().getOrDefault("ARG_ASSET_DIR", "assets"));
            load(portraitA, PlayerAppearance.A, root);
            load(portraitB, PlayerAppearance.B, root);
        } catch (RuntimeException unavailable) {
            // Selection remains possible if optional artwork is unavailable.
        }
    }

    private static void load(ImageView view, PlayerAppearance appearance, Path root) {
        new CharacterAssetResolver().findCharacterImage(appearance.assetKey(), root).ifPresent(path -> {
            Image image = new Image(path.toUri().toString(), 500, 700, true, true, false);
            if (!image.isError()) view.setImage(image);
        });
    }

    @FXML private void confirm() {
        if (navigator.workInProgress() || appearanceGroup.getSelectedToggle() == null) return;
        PlayerAppearance choice = (PlayerAppearance) appearanceGroup.getSelectedToggle().getUserData();
        appearanceRoot.setDisable(true);
        navigator.runAsync("Save player appearance", () -> navigator.backend().playerAppearanceService().save(choice), saved -> {
            navigator.setPlayerAppearance(saved);
            try { navigator.showMenu(); }
            catch (IOException failure) {
                appearanceRoot.setDisable(false);
                navigator.notice("Menu unavailable", "Your appearance was saved. Please restart and sign in again.");
            }
        }, failure -> {
            appearanceRoot.setDisable(false);
            navigator.notice("Appearance unavailable", "Your choice could not be saved. Check the database setup and try again.");
        });
    }

    @FXML private void logout() {
        if (navigator.workInProgress()) return;
        if (navigator.playerAppearance().isPresent()) {
            try { navigator.showMenu(); }
            catch (IOException failure) { navigator.notice("Menu unavailable", "Please restart the game."); }
            return;
        }
        appearanceRoot.setDisable(true);
        navigator.runAsync("Sign out", () -> { navigator.backend().accountGameService().logout(true); return null; }, ignored -> {
            try { navigator.showAccount(); }
            catch (IOException failure) { navigator.notice("Welcome unavailable", "Please restart the game."); }
        }, failure -> {
            appearanceRoot.setDisable(false);
            navigator.notice("Sign out unavailable", "Please try again.");
        });
    }
}

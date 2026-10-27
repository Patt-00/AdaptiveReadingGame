package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.content.CharacterAssetResolver;
import com.adaptivereadinggame.content.StoryAssetResolver;
import com.adaptivereadinggame.view.StoryArtworkPane;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Introductory title screen with optional group-provided artwork. */
public final class SplashController {
    private final AppNavigator navigator;
    @FXML private StackPane splashRoot;
    @FXML private StoryArtworkPane splashArtwork;
    @FXML private Pane portraitLayer;
    @FXML private ImageView heroPortrait;
    @FXML private VBox titleCard;

    public SplashController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(portraitLayer.widthProperty());
        clip.heightProperty().bind(portraitLayer.heightProperty());
        portraitLayer.setClip(clip);
        try {
            Path assets = Path.of(System.getenv().getOrDefault("ARG_ASSET_DIR", "assets"));
            new StoryAssetResolver().findSceneImage("solarium-hall", assets)
                    .ifPresent(path -> {
                        Image image = new Image(path.toUri().toString(), 1280, 720, true, true, false);
                        if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0)
                            splashArtwork.setSceneImage(image);
                    });
            new CharacterAssetResolver().findCharacterImage("Hailey_Neutral", assets)
                    .ifPresent(path -> {
                        Image image = new Image(path.toUri().toString(), 900, 1400, true, true, false);
                        if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0)
                            heroPortrait.setImage(image);
                    });
        } catch (RuntimeException invalidAssets) {
            // The title and controls remain available when artwork is unavailable.
        }
        splashRoot.widthProperty().addListener((observable, oldWidth, width) -> updateLayout());
        splashRoot.heightProperty().addListener((observable, oldHeight, height) -> updateLayout());
        portraitLayer.widthProperty().addListener((observable, oldWidth, width) -> positionPortrait());
        portraitLayer.heightProperty().addListener((observable, oldHeight, height) -> positionPortrait());
        heroPortrait.layoutBoundsProperty().addListener((observable, oldBounds, bounds) -> positionPortrait());
    }

    private void updateLayout() {
        double width = splashRoot.getWidth();
        double height = splashRoot.getHeight();
        if (width <= 0 || height <= 0) return;
        titleCard.setMaxWidth(Math.min(620, width * 0.65));
        heroPortrait.setFitWidth(width * 0.48);
        heroPortrait.setFitHeight(height * 0.90);
        positionPortrait();
    }

    private void positionPortrait() {
        double width = portraitLayer.getWidth();
        double height = portraitLayer.getHeight();
        heroPortrait.relocate(Math.max(0, width - heroPortrait.getLayoutBounds().getWidth() - width * 0.025),
                Math.max(0, height - heroPortrait.getLayoutBounds().getHeight()));
    }

    @FXML private void begin() {
        try { navigator.showAccount(); }
        catch (IOException failure) { navigator.notice("Welcome unavailable", "The welcome screen could not open. Please restart the game."); }
    }

    @FXML private void audioSettings() { navigator.showAudioSettings(); }
}

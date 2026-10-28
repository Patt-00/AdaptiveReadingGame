package com.adaptivereadinggame.controller;

import com.adaptivereadinggame.content.CharacterAssetResolver;
import com.adaptivereadinggame.content.StoryAssetResolver;
import com.adaptivereadinggame.view.StoryArtworkPane;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

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
    @FXML private Label titleLineOne;
    @FXML private Label titleLineTwo;
    @FXML private CheckBox reduceMotion;
    private ParallelTransition intro;

    public SplashController(AppNavigator navigator) { this.navigator = Objects.requireNonNull(navigator); }

    @FXML private void initialize() {
        Rectangle screenClip = new Rectangle();
        screenClip.widthProperty().bind(splashRoot.widthProperty());
        screenClip.heightProperty().bind(splashRoot.heightProperty());
        splashRoot.setClip(screenClip);
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
        reduceMotion.setSelected(navigator.reducedMotion());
        if (!navigator.reducedMotion()) {
            splashArtwork.setOpacity(0.45);
            heroPortrait.setOpacity(0);
            titleLineOne.setOpacity(0.25);
            titleLineTwo.setOpacity(0.25);
        }
        Platform.runLater(() -> {
            if (splashRoot.getScene() != null && splashRoot.getScene().getRoot() == splashRoot
                    && !navigator.reducedMotion()) playIntro();
        });
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

    private void playIntro() {
        heroPortrait.setOpacity(0);
        heroPortrait.setTranslateX(18);
        titleLineOne.setOpacity(0.25);
        titleLineTwo.setOpacity(0.25);
        FadeTransition backgroundFade = new FadeTransition(Duration.millis(1500), splashArtwork);
        backgroundFade.setFromValue(0.45);
        backgroundFade.setToValue(1);
        ScaleTransition backgroundZoom = new ScaleTransition(Duration.millis(1800), splashArtwork);
        backgroundZoom.setFromX(1.035);
        backgroundZoom.setFromY(1.035);
        backgroundZoom.setToX(1);
        backgroundZoom.setToY(1);
        FadeTransition portraitFade = new FadeTransition(Duration.millis(1400), heroPortrait);
        portraitFade.setFromValue(0);
        portraitFade.setToValue(1);
        portraitFade.setDelay(Duration.millis(250));
        TranslateTransition portraitSlide = new TranslateTransition(Duration.millis(1400), heroPortrait);
        portraitSlide.setFromX(18);
        portraitSlide.setToX(0);
        portraitSlide.setDelay(Duration.millis(250));
        FadeTransition firstLine = new FadeTransition(Duration.millis(850), titleLineOne);
        firstLine.setFromValue(0.25);
        firstLine.setToValue(1);
        firstLine.setDelay(Duration.millis(300));
        FadeTransition secondLine = new FadeTransition(Duration.millis(850), titleLineTwo);
        secondLine.setFromValue(0.25);
        secondLine.setToValue(1);
        secondLine.setDelay(Duration.millis(600));
        intro = new ParallelTransition(backgroundFade, backgroundZoom, portraitFade, portraitSlide, firstLine, secondLine);
        intro.play();
    }

    private void finishIntro() {
        if (intro != null) intro.stop();
        splashArtwork.setOpacity(1);
        splashArtwork.setScaleX(1);
        splashArtwork.setScaleY(1);
        heroPortrait.setOpacity(1);
        heroPortrait.setTranslateX(0);
        titleLineOne.setOpacity(1);
        titleLineTwo.setOpacity(1);
    }

    @FXML private void changeMotion() {
        navigator.setReducedMotion(reduceMotion.isSelected());
        if (reduceMotion.isSelected()) finishIntro();
    }

    @FXML private void begin() {
        finishIntro();
        try { navigator.showAccount(); }
        catch (IOException failure) { navigator.notice("Welcome unavailable", "The welcome screen could not open. Please restart the game."); }
    }

    @FXML private void audioSettings() { navigator.showAudioSettings(); }
}

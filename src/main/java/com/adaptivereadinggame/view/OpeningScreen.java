package com.adaptivereadinggame.view;

import com.adaptivereadinggame.audio.GameAudio;
import com.adaptivereadinggame.backend.BackendContext;
import com.adaptivereadinggame.content.CharacterAssetResolver;
import com.adaptivereadinggame.content.StoryAssetResolver;
import com.adaptivereadinggame.controller.AppNavigator;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A skippable text opening followed by actual startup progress and optional artwork. */
public final class OpeningScreen {
    public record Loaded(BackendContext backend, String storageDescription) {}
    public static final class LoadFailure extends Exception {
        public LoadFailure(String message, Throwable cause) { super(message, cause); }
        public LoadFailure(String message) { super(message); }
    }
    @FunctionalInterface public interface Reporter { void update(int completed, int total, String message); }
    @FunctionalInterface public interface Loader { Loaded load(Reporter reporter) throws Exception; }
    private record Slide(Image background, Image portrait, String caption) {}

    private final Stage stage;
    private final GameAudio audio;
    private final Loader loader;
    private final StackPane root = new StackPane();
    private final VBox opening = new VBox(18);
    private final StackPane loading = new StackPane();
    private final StoryArtworkPane artwork = new StoryArtworkPane();
    private final Pane portraitLayer = new Pane();
    private final ImageView portrait = new ImageView();
    private final Label caption = new Label();
    private final Label status = new Label("Preparing the game...");
    private final ProgressBar progress = new ProgressBar(0);
    private final Button enter = new Button("PRESS SPACE TO ENTER");
    private final Button retry = new Button("TRY AGAIN");
    private final CheckBox reduceMotion = new CheckBox("Reduce motion");
    private final List<Slide> slides = new ArrayList<>();
    private final EventHandler<ActionEvent> soundFilter;
    private final EventHandler<KeyEvent> keyFilter = this::handleKey;
    private Task<Loaded> task;
    private SequentialTransition openingAnimation;
    private Timeline slideshow;
    private Transition progressAnimation;
    private FadeTransition slideFade;
    private Loaded loaded;
    private Scene scene;
    private int slideIndex;
    private boolean showingLoading;
    private boolean readyToEnter;
    private boolean entering;
    private boolean closed;
    private double rememberedMusic = 0.22;

    public OpeningScreen(Stage stage, GameAudio audio, Loader loader) {
        this.stage = Objects.requireNonNull(stage);
        this.audio = Objects.requireNonNull(audio);
        this.loader = Objects.requireNonNull(loader);
        this.soundFilter = event -> { if (event.getTarget() instanceof ButtonBase) this.audio.click(); };
    }

    public void start() {
        stage.setTitle("Adaptive Reading Game");
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        root.setId("openingRoot");
        root.getStyleClass().add("opening-root");
        if (Font.getFamilies().stream().noneMatch(family -> family.equalsIgnoreCase("Georgia")))
            root.getStyleClass().add("georgia-fallback");
        if (Font.getFamilies().stream().noneMatch(family -> family.equalsIgnoreCase("Times New Roman")))
            root.getStyleClass().add("serif-fallback");
        buildOpening();
        buildLoading();
        root.getChildren().addAll(loading, opening);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);
        scene = new Scene(root, 1280, 720);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(
                "/com/adaptivereadinggame/style/application.css")).toExternalForm());
        scene.addEventFilter(ActionEvent.ACTION, soundFilter);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        stage.setScene(scene);
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, event -> close());
        stage.show();
        if (Boolean.parseBoolean(System.getenv("ARG_FULL_SCREEN"))) {
            stage.setFullScreenExitHint("Press Esc to leave full screen.");
            stage.setFullScreen(true);
        }
        audio.setMenuScene(true);
        startLoading();
        playOpening();
    }

    private void buildOpening() {
        opening.setId("openingText");
        opening.setAlignment(Pos.CENTER);
        opening.getStyleClass().add("opening-text-screen");
        Label title = new Label("ADAPTIVE READING GAME");
        title.setWrapText(true);
        title.getStyleClass().add("opening-title");
        SVGPath headphones = new SVGPath();
        headphones.setId("headphoneIcon");
        headphones.setContent("M 4 14 V 11 C 4 6 7 3 12 3 C 17 3 20 6 20 11 V 14 M 4 13 H 7 V 19 H 4 Z M 17 13 H 20 V 19 H 17 Z");
        headphones.setScaleX(1.7);
        headphones.setScaleY(1.7);
        headphones.getStyleClass().add("opening-headphones");
        Label hint = new Label("For the best experience, use headphones.");
        hint.setId("headphoneHint");
        hint.setWrapText(true);
        hint.getStyleClass().add("opening-hint");
        Button skip = new Button("SKIP OPENING");
        skip.setId("skipOpening");
        skip.getStyleClass().add("opening-secondary");
        skip.setOnAction(event -> showLoading());
        reduceMotion.setId("openingReduceMotion");
        reduceMotion.getStyleClass().add("opening-motion");
        reduceMotion.setOnAction(event -> { if (reduceMotion.isSelected()) showLoading(); });
        Button mute = new Button("MUTE MUSIC");
        mute.setId("openingMute");
        mute.getStyleClass().add("opening-secondary");
        mute.setOnAction(event -> {
            if (audio.musicVolume() > 0) {
                rememberedMusic = audio.musicVolume();
                audio.setVolumes(0, audio.effectsVolume());
                mute.setText("UNMUTE MUSIC");
            } else {
                audio.setVolumes(rememberedMusic, audio.effectsVolume());
                mute.setText("MUTE MUSIC");
            }
        });
        HBox actions = new HBox(12, skip, mute);
        actions.setAlignment(Pos.CENTER);
        opening.getChildren().addAll(title, headphones, hint, actions, reduceMotion);
    }

    private void buildLoading() {
        loading.setId("loadingScreen");
        loading.setFocusTraversable(true);
        loading.setVisible(false);
        loading.setManaged(false);
        loading.getStyleClass().add("opening-loading");
        portraitLayer.setMouseTransparent(true);
        portrait.setManaged(false);
        portrait.setId("loadingPortrait");
        portrait.setPreserveRatio(true);
        portrait.setSmooth(true);
        portraitLayer.getChildren().add(portrait);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(portraitLayer.widthProperty());
        clip.heightProperty().bind(portraitLayer.heightProperty());
        portraitLayer.setClip(clip);
        portraitLayer.widthProperty().addListener((o, oldValue, value) -> layoutPortrait());
        portraitLayer.heightProperty().addListener((o, oldValue, value) -> layoutPortrait());
        Region shade = new Region();
        shade.setMouseTransparent(true);
        shade.getStyleClass().add("opening-loading-shade");
        caption.setId("loadingCaption");
        caption.setText("A story of clues and choices");
        caption.setWrapText(true);
        caption.getStyleClass().add("opening-caption");
        status.setId("loadingStatus");
        status.setWrapText(true);
        status.getStyleClass().add("opening-status");
        progress.setId("startupProgress");
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setAccessibleText("Game startup progress");
        enter.setId("enterGame");
        enter.getStyleClass().add("splash-begin");
        enter.setVisible(false);
        enter.setManaged(false);
        enter.setOnAction(event -> enterGame());
        retry.setId("retryStartup");
        retry.getStyleClass().add("opening-secondary");
        retry.setVisible(false);
        retry.setManaged(false);
        retry.setOnAction(event -> startLoading());
        CheckBox loadingMotion = new CheckBox("Reduce motion");
        loadingMotion.setId("loadingReduceMotion");
        loadingMotion.getStyleClass().add("opening-motion");
        loadingMotion.selectedProperty().bindBidirectional(reduceMotion.selectedProperty());
        loadingMotion.setOnAction(event -> {
            updateSlideshow();
            applyReducedMotion();
        });
        VBox bottom = new VBox(10, caption, status, progress, enter, retry, loadingMotion);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setMaxWidth(730);
        bottom.setMinHeight(Region.USE_PREF_SIZE);
        bottom.setMaxHeight(Region.USE_PREF_SIZE);
        bottom.getStyleClass().add("opening-loading-card");
        StackPane.setAlignment(bottom, Pos.BOTTOM_LEFT);
        StackPane.setMargin(bottom, new Insets(25, 30, 32, 30));
        loading.getChildren().addAll(artwork, portraitLayer, shade, bottom);
    }

    private void playOpening() {
        opening.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(1400), opening);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(900), opening);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        openingAnimation = new SequentialTransition(fadeIn, new PauseTransition(Duration.millis(2600)), fadeOut);
        openingAnimation.setOnFinished(event -> showLoading());
        openingAnimation.play();
    }

    private void showLoading() {
        if (showingLoading || closed) return;
        showingLoading = true;
        if (openingAnimation != null) openingAnimation.stop();
        opening.setVisible(false);
        opening.setManaged(false);
        loading.setVisible(true);
        loading.setManaged(true);
        loading.requestFocus();
        loadSlides();
        if (!slides.isEmpty()) showSlide(0);
        updateSlideshow();
        updateReady();
    }

    private void updateSlideshow() {
        if (reduceMotion.isSelected()) {
            if (slideshow != null) slideshow.stop();
            if (slideFade != null) slideFade.stop();
            portrait.setOpacity(1);
        } else if (slides.size() > 1) {
            if (slideshow == null) {
                slideshow = new Timeline(new KeyFrame(Duration.seconds(5), event -> showSlide((slideIndex + 1) % slides.size())));
                slideshow.setCycleCount(Timeline.INDEFINITE);
            }
            slideshow.play();
        }
    }

    private void loadSlides() {
        if (!slides.isEmpty()) return;
        try {
            Path assets = Path.of(System.getenv().getOrDefault("ARG_ASSET_DIR", "assets"));
            addSlide(assets, "solarium-hall", "Hailey_Neutral", "Meet the characters");
            addSlide(assets, "study-room", "Amelia_Neutral", "Explore the clues");
            addSlide(assets, "solarium-hall-evening", "Davor_Neutral", "Read carefully and choose your path");
        } catch (RuntimeException unavailable) {
            // Loading and navigation work even if optional images are absent.
        }
    }

    private void addSlide(Path assets, String sceneKey, String portraitKey, String text) {
        Image backgroundImage = new StoryAssetResolver().findSceneImage(sceneKey, assets)
                .map(path -> new Image(path.toUri().toString(), 1280, 720, true, true, true)).orElse(null);
        Image character = new CharacterAssetResolver().findCharacterImage(portraitKey, assets)
                .map(path -> new Image(path.toUri().toString(), 800, 1200, true, true, true)).orElse(null);
        if (backgroundImage != null && backgroundImage.isError()) backgroundImage = null;
        if (character != null && character.isError()) character = null;
        if (backgroundImage != null || character != null) slides.add(new Slide(backgroundImage, character, text));
    }

    private void showSlide(int index) {
        if (slides.isEmpty()) return;
        slideIndex = index;
        Slide slide = slides.get(index);
        artwork.setSceneImage(slide.background());
        portrait.setImage(slide.portrait());
        caption.setText(slide.caption());
        layoutPortrait();
        if (slideFade != null) slideFade.stop();
        if (!reduceMotion.isSelected()) {
            slideFade = new FadeTransition(Duration.millis(550), portrait);
            slideFade.setFromValue(0.35);
            slideFade.setToValue(1);
            slideFade.play();
        } else portrait.setOpacity(1);
    }

    private void layoutPortrait() {
        double width = portraitLayer.getWidth(), height = portraitLayer.getHeight();
        if (width <= 0 || height <= 0) return;
        portrait.setFitWidth(width * 0.45);
        portrait.setFitHeight(height * 0.85);
        portrait.relocate(Math.max(0, width - portrait.getLayoutBounds().getWidth() - width * 0.03),
                Math.max(0, height - portrait.getLayoutBounds().getHeight()));
    }

    private void startLoading() {
        if (task != null && task.isRunning()) return;
        loaded = null;
        readyToEnter = false;
        enter.setVisible(false); enter.setManaged(false);
        retry.setVisible(false); retry.setManaged(false);
        task = new Task<>() {
            @Override protected Loaded call() throws Exception {
                return loader.load((completed, total, message) -> {
                    updateMessage(message);
                    updateProgress(completed, total);
                });
            }
        };
        if (progressAnimation != null) progressAnimation.stop();
        progress.setProgress(0);
        task.progressProperty().addListener((observable, previous, next) -> {
            double target = next.doubleValue();
            if (target < 0) return;
            if (reduceMotion.isSelected()) {
                progress.setProgress(target);
                return;
            }
            if (progressAnimation != null) progressAnimation.stop();
            double start = Math.max(0, progress.getProgress());
            Transition animation = new Transition() {
                { setCycleDuration(Duration.millis(1200)); }
                @Override protected void interpolate(double fraction) {
                    progress.setProgress(start + (target - start) * fraction);
                }
            };
            progressAnimation = animation;
            animation.setOnFinished(event -> {
                if (progressAnimation == animation && loaded != null) updateReady();
            });
            animation.play();
        });
        status.textProperty().unbind();
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> { loaded = task.getValue(); updateReady(); });
        task.setOnFailed(event -> {
            status.textProperty().unbind();
            Throwable failure = task.getException();
            status.setText(failure instanceof LoadFailure loadFailure
                    ? loadFailure.getMessage()
                    : "The game could not finish loading. Check the database and story content setup, then retry.");
            retry.setVisible(true); retry.setManaged(true);
            retry.requestFocus();
            System.err.println("Game startup failed: " + task.getException().getClass().getSimpleName());
        });
        Thread thread = new Thread(task, "game-startup");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateReady() {
        if (!showingLoading || loaded == null || closed) return;
        if (reduceMotion.isSelected()) {
            if (progressAnimation != null) progressAnimation.stop();
            progress.setProgress(1);
            showReadyPrompt();
            return;
        }
        if (progressAnimation != null && progressAnimation.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            status.textProperty().unbind();
            status.setText("Finishing the loading sequence...");
            return;
        }
        progress.setProgress(1);
        showReadyPrompt();
    }

    private void showReadyPrompt() {
        if (closed || loaded == null) return;
        status.textProperty().unbind();
        status.setText("Ready. Press Space or select Enter to continue.");
        readyToEnter = true;
        enter.setVisible(true);
        enter.setManaged(true);
        enter.requestFocus();
    }

    private void handleKey(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE && showingLoading && readyToEnter
                && (!(scene.getFocusOwner() instanceof ButtonBase) || scene.getFocusOwner() == enter)) {
            enterGame();
            event.consume();
        }
    }

    private void enterGame() {
        if (entering || closed || !showingLoading || loaded == null || !readyToEnter) return;
        entering = true;
        readyToEnter = false;
        if (slideshow != null) slideshow.stop();
        if (slideFade != null) slideFade.stop();
        if (progressAnimation != null) progressAnimation.stop();
        scene.removeEventFilter(ActionEvent.ACTION, soundFilter);
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        try {
            AppNavigator navigator = new AppNavigator(stage, loaded.backend(), loaded.storageDescription(), audio);
            navigator.setReducedMotion(reduceMotion.isSelected());
            navigator.startAtAccount();
        } catch (IOException failure) {
            entering = false;
            scene.addEventFilter(ActionEvent.ACTION, soundFilter);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
            status.setText("The welcome screen could not open. Please try again.");
        }
    }

    private void applyReducedMotion() {
        if (!reduceMotion.isSelected()) return;
        if (progressAnimation != null) progressAnimation.stop();
        if (loaded != null) {
            updateReady();
            return;
        }
        if (task != null && task.getProgress() >= 0) progress.setProgress(task.getProgress());
    }

    private void close() {
        closed = true;
        if (task != null) task.cancel();
        if (openingAnimation != null) openingAnimation.stop();
        if (slideshow != null) slideshow.stop();
        if (slideFade != null) slideFade.stop();
        audio.close();
    }
}

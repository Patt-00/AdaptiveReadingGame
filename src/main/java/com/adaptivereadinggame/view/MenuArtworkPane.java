package com.adaptivereadinggame.view;

import com.adaptivereadinggame.content.CharacterAssetResolver;
import com.adaptivereadinggame.content.StoryAssetResolver;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.nio.file.Path;

/** Decorative title art for wide authentication and menu screens. */
public final class MenuArtworkPane extends Pane {
    private final StoryArtworkPane background = new StoryArtworkPane();
    private final Region tint = new Region();
    private final ImageView portrait = new ImageView();
    private final VBox heading = new VBox(5);

    public MenuArtworkPane() {
        setMinSize(0, 0);
        setPrefWidth(0);
        setMouseTransparent(true);
        getStyleClass().add("menu-artwork");
        tint.getStyleClass().add("menu-artwork-tint");
        portrait.setPreserveRatio(true);
        portrait.setSmooth(true);
        Label kicker = new Label("A STORY-DRIVEN ADVENTURE");
        kicker.getStyleClass().add("menu-artwork-kicker");
        Label title = new Label("ADAPTIVE\nREADING GAME");
        title.getStyleClass().add("menu-artwork-title");
        Label subtitle = new Label("Read. Explore. Reflect.");
        subtitle.getStyleClass().add("menu-artwork-subtitle");
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getChildren().addAll(kicker, title, subtitle);
        getChildren().addAll(background, tint, portrait, heading);
        getChildren().forEach(node -> node.setManaged(false));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        loadArtwork();
    }

    private void loadArtwork() {
        try {
            Path assets = Path.of(System.getenv().getOrDefault("ARG_ASSET_DIR", "assets"));
            new StoryAssetResolver().findSceneImage("solarium-hall", assets).ifPresent(path -> {
                Image image = new Image(path.toUri().toString(), 900, 900, true, true, false);
                if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0)
                    background.setSceneImage(image);
            });
            new CharacterAssetResolver().findCharacterImage("Hailey_Neutral", assets).ifPresent(path -> {
                Image image = new Image(path.toUri().toString(), 900, 1400, true, true, false);
                if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) portrait.setImage(image);
            });
        } catch (RuntimeException unavailableArtwork) {
            // The gradient and account controls remain available without artwork.
        }
    }

    @Override protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) return;
        background.resizeRelocate(0, 0, width, height);
        tint.resizeRelocate(0, 0, width, height);
        portrait.setFitWidth(width * 0.90);
        portrait.setFitHeight(height * 0.76);
        portrait.relocate((width - portrait.getLayoutBounds().getWidth()) / 2,
                Math.max(height * 0.20, height - portrait.getLayoutBounds().getHeight()));
        heading.autosize();
        heading.relocate(28, 28);
    }

    public void fitToWindowWidth(double width) {
        boolean wide = width >= 1000;
        setVisible(wide);
        setManaged(wide);
        if (wide) setPrefWidth(Math.min(500, width * 0.36));
    }
}

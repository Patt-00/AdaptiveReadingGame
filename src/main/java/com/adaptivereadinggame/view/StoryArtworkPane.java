package com.adaptivereadinggame.view;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/** Decorative art fills a viewport without contributing to its content size. */
public final class StoryArtworkPane extends Pane {
    private final ImageView scene = new ImageView();
    private final ImageView illustration = new ImageView();
    private final Region dimmer = new Region();

    public StoryArtworkPane() {
        setMinSize(0, 0);
        setPrefSize(0, 0);
        setMouseTransparent(true);
        scene.setId("sceneImage");
        scene.setAccessibleText("Story scene background");
        illustration.setId("cgImage");
        illustration.setAccessibleText("Story illustration");
        illustration.setPreserveRatio(true);
        dimmer.setId("sceneDimmer");
        dimmer.getStyleClass().add("scene-dimmer");
        getChildren().addAll(scene, dimmer, illustration);
        getChildren().forEach(node -> node.setManaged(false));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        clear();
    }

    public void clear() {
        setSceneImage(null);
        setIllustration(null);
    }

    public void setSceneImage(Image image) {
        scene.setImage(image);
        scene.setVisible(image != null);
        dimmer.setVisible(image != null);
        requestLayout();
    }

    public void setIllustration(Image image) {
        illustration.setImage(image);
        illustration.setVisible(image != null);
        requestLayout();
    }

    public Image illustrationImage() { return illustration.getImage(); }

    @Override protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) return;
        Image image = scene.getImage();
        if (image != null) {
            double scale = Math.max(width / image.getWidth(), height / image.getHeight());
            double cropWidth = width / scale;
            double cropHeight = height / scale;
            scene.setViewport(new Rectangle2D((image.getWidth() - cropWidth) / 2,
                    (image.getHeight() - cropHeight) / 2, cropWidth, cropHeight));
            scene.setFitWidth(width);
            scene.setFitHeight(height);
            scene.relocate(0, 0);
        }
        dimmer.resizeRelocate(0, 0, width, height);
        illustration.setFitWidth(width * 0.92);
        illustration.setFitHeight(height * 0.92);
        illustration.relocate((width - illustration.getLayoutBounds().getWidth()) / 2,
                (height - illustration.getLayoutBounds().getHeight()) / 2);
    }
}

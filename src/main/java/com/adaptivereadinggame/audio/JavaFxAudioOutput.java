package com.adaptivereadinggame.audio;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

/** Optional media playback. Failures leave the rest of the game available. */
public final class JavaFxAudioOutput implements GameAudio.Output {
    private static final String ROOT = "/com/adaptivereadinggame/audio/";
    private MediaPlayer player;
    private MediaPlayer click;
    private boolean musicFailed;
    private boolean clickFailed;
    private boolean desiredMusic;
    private double musicVolume;

    @Override public void setMusic(boolean playing, double volume) {
        desiredMusic = playing;
        musicVolume = volume;
        if (!playing && player == null) return;
        try {
            if (player == null && !musicFailed) {
                var resource = JavaFxAudioOutput.class.getResource(ROOT + "comfortable-mystery.wav");
                if (resource == null) { musicFailed = true; return; }
                Media media = new Media(resource.toExternalForm());
                player = new MediaPlayer(media);
                player.setCycleCount(MediaPlayer.INDEFINITE);
                player.setOnError(() -> failMusic(player == null ? null : player.getError()));
                media.setOnError(() -> failMusic(media.getError()));
                player.setOnReady(this::applyMusic);
            }
            applyMusic();
        } catch (RuntimeException | LinkageError failure) { failMusic(failure); }
    }

    private void applyMusic() {
        if (player == null || musicFailed) return;
        player.setVolume(musicVolume);
        if (desiredMusic) player.play();
        else player.pause();
    }

    private void failMusic(Throwable failure) {
        if (!musicFailed) System.err.println("Background music is unavailable; the game will continue without it. "
                + (failure == null ? "" : failure.getClass().getSimpleName() + ": " + failure.getMessage()));
        musicFailed = true;
        if (player != null) { player.dispose(); player = null; }
    }

    @Override public void click(double volume) {
        if (clickFailed) return;
        try {
            if (click == null) {
                var resource = JavaFxAudioOutput.class.getResource(ROOT + "click.wav");
                if (resource == null) { clickFailed = true; return; }
                Media media = new Media(resource.toExternalForm());
                click = new MediaPlayer(media);
                click.setOnError(this::failClick);
                media.setOnError(this::failClick);
            }
            click.stop();
            click.seek(Duration.ZERO);
            click.setVolume(volume);
            click.play();
        } catch (RuntimeException | LinkageError failure) {
            failClick();
        }
    }

    private void failClick() {
        if (!clickFailed) System.err.println("Click sound is unavailable; the game will continue without it.");
        clickFailed = true;
        if (click != null) { click.dispose(); click = null; }
    }

    MediaPlayer.Status musicStatus() { return player == null ? null : player.getStatus(); }
    MediaPlayer.Status clickStatus() { return click == null ? null : click.getStatus(); }

    @Override public void close() {
        if (player != null) { player.dispose(); player = null; }
        if (click != null) { click.dispose(); click = null; }
    }
}

package com.adaptivereadinggame.audio;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;

/** Optional media playback. Failures leave the rest of the game available. */
public final class JavaFxAudioOutput implements GameAudio.Output {
    private static final String ROOT = "/com/adaptivereadinggame/audio/";
    private MediaPlayer player;
    private final Deque<MediaPlayer> clicks = new ArrayDeque<>();
    private MediaPlayer latestClick;
    private boolean musicFailed;
    private boolean clickFailed;
    private boolean desiredMusic;
    private double musicVolume;
    private GameAudio.Track activeTrack = GameAudio.Track.SILENT;

    @Override public void setMusic(boolean playing, double volume) {
        setTrack(playing ? GameAudio.Track.MENU : GameAudio.Track.SILENT, volume);
    }

    @Override public void setTrack(GameAudio.Track requested, double volume) {
        GameAudio.Track track = requested;
        if (track == GameAudio.Track.TENSE && !Files.isRegularFile(Path.of("assets/local-music/tense.wav")))
            track = GameAudio.Track.INVESTIGATION;
        desiredMusic = track != GameAudio.Track.SILENT;
        musicVolume = volume;
        if (!desiredMusic && player == null) return;
        if (desiredMusic && player == null && activeTrack == track && musicFailed) return;
        try {
            if (desiredMusic && (player == null || activeTrack != track)) {
                if (player != null) { player.dispose(); player = null; }
                activeTrack = track;
                musicFailed = false;
                URL resource = track == GameAudio.Track.MENU
                        ? JavaFxAudioOutput.class.getResource(ROOT + "comfortable-mystery.wav")
                        : localTrack(track);
                if (resource == null) { musicFailed = true; return; }
                Media media = new Media(resource.toExternalForm());
                MediaPlayer created = new MediaPlayer(media);
                player = created;
                created.setCycleCount(MediaPlayer.INDEFINITE);
                created.setOnError(() -> { if (player == created) failMusic(created.getError()); });
                media.setOnError(() -> { if (player == created) failMusic(media.getError()); });
                created.setOnReady(() -> { if (player == created) applyMusic(); });
            }
            applyMusic();
        } catch (RuntimeException | LinkageError failure) { failMusic(failure); }
    }

    private static URL localTrack(GameAudio.Track track) {
        try {
            Path file = Path.of("assets", "local-music", track == GameAudio.Track.TENSE ? "tense.wav" : "investigation.wav");
            return Files.isRegularFile(file) ? file.toUri().toURL() : null;
        } catch (java.net.MalformedURLException | RuntimeException unavailable) { return null; }
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
            var resource = JavaFxAudioOutput.class.getResource(ROOT + "click.wav");
            if (resource == null) { clickFailed = true; return; }
            while (clicks.size() >= 4) clicks.removeFirst().dispose();
            Media media = new Media(resource.toExternalForm());
            MediaPlayer sound = new MediaPlayer(media);
            clicks.addLast(sound);
            latestClick = sound;
            sound.setVolume(volume);
            sound.setOnReady(() -> { if (clicks.contains(sound)) sound.play(); });
            sound.setOnEndOfMedia(() -> disposeClick(sound));
            sound.setOnError(() -> failClick(sound));
            media.setOnError(() -> failClick(sound));
        } catch (RuntimeException | LinkageError failure) {
            failClick(null);
        }
    }

    private void disposeClick(MediaPlayer sound) {
        if (clicks.remove(sound)) sound.dispose();
    }

    private void failClick(MediaPlayer sound) {
        if (sound != null && !clicks.contains(sound)) return;
        if (!clickFailed) System.err.println("Click sound is unavailable; the game will continue without it.");
        clickFailed = true;
        if (sound != null) disposeClick(sound);
    }

    MediaPlayer.Status musicStatus() { return player == null ? null : player.getStatus(); }
    MediaPlayer.Status clickStatus() { return latestClick == null ? null : latestClick.getStatus(); }

    @Override public void close() {
        if (player != null) { player.dispose(); player = null; }
        while (!clicks.isEmpty()) clicks.removeFirst().dispose();
        latestClick = null;
    }
}

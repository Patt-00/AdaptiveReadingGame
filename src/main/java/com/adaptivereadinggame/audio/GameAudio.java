package com.adaptivereadinggame.audio;

import java.util.Objects;

/** Session audio preferences and playback policy. */
public final class GameAudio implements AutoCloseable {
    public enum Track { SILENT, MENU, INVESTIGATION, TENSE }
    public interface Output extends AutoCloseable {
        void setMusic(boolean playing, double volume);
        default void setTrack(Track track, double volume) { setMusic(track != Track.SILENT, volume); }
        void click(double volume);
        @Override void close();
    }

    private final Output output;
    private Track track = Track.SILENT;
    private double musicVolume = 0.22;
    private double effectsVolume = 0.55;
    private boolean closed;

    public GameAudio(Output output) { this.output = Objects.requireNonNull(output); }
    public double musicVolume() { return musicVolume; }
    public double effectsVolume() { return effectsVolume; }

    public void setVolumes(double music, double effects) {
        validate(music);
        validate(effects);
        musicVolume = music;
        effectsVolume = effects;
        updateMusic();
    }

    public void setMenuScene(boolean menuScene) {
        setTrack(menuScene ? Track.MENU : Track.SILENT);
    }

    public void setTrack(Track track) {
        this.track = Objects.requireNonNull(track);
        updateMusic();
    }

    public void click() {
        if (!closed && effectsVolume > 0) output.click(effectsVolume);
    }

    private void updateMusic() {
        if (!closed) output.setTrack(musicVolume > 0 ? track : Track.SILENT, musicVolume);
    }

    private static void validate(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Audio volume must be between 0 and 1.");
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        output.close();
    }
}

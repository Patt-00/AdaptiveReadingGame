package com.adaptivereadinggame.settings;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Device-local presentation settings. Failure to access storage leaves the game usable. */
public final class PlayerPreferences {
    public static final double DEFAULT_TEXT_SCALE = 1.0;
    public static final double DEFAULT_MUSIC = 0.22;
    public static final double DEFAULT_EFFECTS = 0.55;
    private final Preferences storage;
    private double textScale = DEFAULT_TEXT_SCALE;
    private boolean artwork = true;
    private boolean reducedMotion;
    private double music = DEFAULT_MUSIC;
    private double effects = DEFAULT_EFFECTS;

    public static PlayerPreferences system() {
        try { return new PlayerPreferences(Preferences.userRoot().node("com/adaptivereadinggame/player")); }
        catch (SecurityException unavailable) { return new PlayerPreferences(null); }
    }

    public static PlayerPreferences temporary() { return new PlayerPreferences(null); }

    public PlayerPreferences(Preferences storage) {
        this.storage = storage;
        if (storage == null) return;
        try {
            textScale = valid(storage.getDouble("textScale", DEFAULT_TEXT_SCALE), 0.8, 1.6, DEFAULT_TEXT_SCALE);
            artwork = storage.getBoolean("artwork", true);
            reducedMotion = storage.getBoolean("reducedMotion", false);
            music = valid(storage.getDouble("music", DEFAULT_MUSIC), 0, 1, DEFAULT_MUSIC);
            effects = valid(storage.getDouble("effects", DEFAULT_EFFECTS), 0, 1, DEFAULT_EFFECTS);
        } catch (SecurityException | IllegalStateException unavailable) {
            // Defaults remain available when the preference store cannot be read.
        }
    }

    public double textScale() { return textScale; }
    public boolean artwork() { return artwork; }
    public boolean reducedMotion() { return reducedMotion; }
    public double music() { return music; }
    public double effects() { return effects; }

    public void setTextScale(double value) {
        requireRange(value, 0.8, 1.6);
        textScale = value;
        write(() -> storage.putDouble("textScale", value));
    }
    public void setArtwork(boolean value) { artwork = value; write(() -> storage.putBoolean("artwork", value)); }
    public void setReducedMotion(boolean value) { reducedMotion = value; write(() -> storage.putBoolean("reducedMotion", value)); }
    public void setVolumes(double music, double effects) {
        requireRange(music, 0, 1);
        requireRange(effects, 0, 1);
        this.music = music;
        this.effects = effects;
        write(() -> { storage.putDouble("music", music); storage.putDouble("effects", effects); });
    }

    private void write(Runnable action) {
        if (storage == null) return;
        try { action.run(); storage.flush(); }
        catch (SecurityException | IllegalStateException | BackingStoreException unavailable) {
            System.err.println("Player settings could not be saved; they remain active for this session.");
        }
    }
    private static double valid(double value, double min, double max, double fallback) {
        return Double.isFinite(value) && value >= min && value <= max ? value : fallback;
    }
    private static void requireRange(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("Preference is outside its supported range.");
    }
}

package com.adaptivereadinggame.settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class PlayerPreferencesTest {
    private final Preferences node = Preferences.userRoot().node("com/adaptivereadinggame-tests/" + UUID.randomUUID());

    @AfterEach void cleanup() throws Exception { node.removeNode(); Preferences.userRoot().flush(); }

    @Test void settingsSurviveCreatingAnotherPreferencesInstance() {
        var first = new PlayerPreferences(node);
        first.setTextScale(1.4);
        first.setArtwork(false);
        first.setReducedMotion(true);
        first.setVolumes(0, 0.7);
        var restored = new PlayerPreferences(node);
        assertEquals(1.4, restored.textScale());
        assertFalse(restored.artwork());
        assertTrue(restored.reducedMotion());
        assertEquals(0, restored.music());
        assertEquals(0.7, restored.effects());
    }

    @Test void invalidStoredAndInputValuesAreRejected() {
        node.put("textScale", "NaN");
        node.put("music", "-1");
        node.put("effects", "2");
        var preferences = new PlayerPreferences(node);
        assertEquals(PlayerPreferences.DEFAULT_TEXT_SCALE, preferences.textScale());
        assertEquals(PlayerPreferences.DEFAULT_MUSIC, preferences.music());
        assertEquals(PlayerPreferences.DEFAULT_EFFECTS, preferences.effects());
        assertThrows(IllegalArgumentException.class, () -> preferences.setTextScale(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> preferences.setVolumes(0.5, Double.POSITIVE_INFINITY));
        assertEquals(PlayerPreferences.DEFAULT_MUSIC, preferences.music());
    }

    @Test void unavailableStorageStillAllowsSessionSettings() throws Exception {
        var removed = node.node("removed");
        removed.removeNode();
        var preferences = new PlayerPreferences(removed);
        preferences.setReducedMotion(true);
        preferences.setVolumes(0, 0.4);
        assertTrue(preferences.reducedMotion());
        assertEquals(0, preferences.music());
        assertEquals(0.4, preferences.effects());
    }
}

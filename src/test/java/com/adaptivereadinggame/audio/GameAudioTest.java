package com.adaptivereadinggame.audio;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.*;

class GameAudioTest {
    private static final class RecordingOutput implements GameAudio.Output {
        boolean playing;
        double music;
        double effect;
        int clicks;
        int closed;
        GameAudio.Track track;
        public void setMusic(boolean playing, double volume) { this.playing = playing; music = volume; }
        public void setTrack(GameAudio.Track track, double volume) { this.track = track; setMusic(track != GameAudio.Track.SILENT, volume); }
        public void click(double volume) { effect = volume; clicks++; }
        public void close() { closed++; }
    }

    @Test void musicFollowsMenuAndVolumeAndEffectsCanBeMutedIndependently() {
        var output = new RecordingOutput();
        var audio = new GameAudio(output);
        audio.setMenuScene(true);
        assertTrue(output.playing);
        assertEquals(GameAudio.Track.MENU, output.track);
        assertEquals(0.22, output.music);
        audio.setVolumes(0, 0.5);
        assertFalse(output.playing);
        audio.click();
        assertEquals(0.5, output.effect);
        audio.setVolumes(0.4, 0);
        assertTrue(output.playing);
        audio.click();
        assertEquals(1, output.clicks);
        audio.setMenuScene(false);
        assertFalse(output.playing);
        audio.setTrack(GameAudio.Track.INVESTIGATION);
        assertEquals(GameAudio.Track.INVESTIGATION, output.track);
        audio.setTrack(GameAudio.Track.TENSE);
        assertEquals(GameAudio.Track.TENSE, output.track);
        audio.setMenuScene(true);
        assertTrue(output.playing);
        audio.close();
        audio.close();
        assertEquals(1, output.closed);
    }

    @Test void invalidVolumesDoNotChangeEitherPreference() {
        var audio = new GameAudio(new RecordingOutput());
        assertThrows(IllegalArgumentException.class, () -> audio.setVolumes(0.5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> audio.setVolumes(-0.1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> audio.setVolumes(1.1, 0.5));
        assertEquals(0.22, audio.musicVolume());
        assertEquals(0.55, audio.effectsVolume());
    }

    @Test void clickAssetHasUsefulLevelWithoutClipping() throws Exception {
        var resource = GameAudioTest.class.getResource("/com/adaptivereadinggame/audio/click.wav");
        assertNotNull(resource);
        try (var input = AudioSystem.getAudioInputStream(resource)) {
            var format = input.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
            assertEquals(16, format.getSampleSizeInBits());
            assertFalse(format.isBigEndian());
            byte[] data = input.readAllBytes();
            assertTrue(data.length > 0 && data.length % 2 == 0);
            double squareSum = 0;
            int peak = 0;
            for (int i = 0; i < data.length; i += 2) {
                short sample = (short) ((data[i] & 0xff) | (data[i + 1] << 8));
                int magnitude = Math.abs((int) sample);
                peak = Math.max(peak, magnitude);
                squareSum += (double) sample * sample;
            }
            double rms = Math.sqrt(squareSum / (data.length / 2));
            assertTrue(rms >= 3500, "The click should remain clearly stronger than the original quiet asset.");
            assertTrue(peak < Short.MAX_VALUE, "The click must not clip.");
        }
    }
}

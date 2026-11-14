package de.exit.sound2artnet;

import de.exit.sound2artnet.audio.BeatDetector;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying that beat detection tokens are reliably consumed across
 * global and per-mode callers without race-condition losses or timing swallows.
 */
class BeatDetectionTimingTest {

    @Test
    @DisplayName("Verify consumeBeat(LEVEL_DETECT) is not swallowed when consumeBeat() was called first")
    void testLevelBeatConsumeDoesNotGetSwallowedByGlobalConsume() {
        BeatDetector detector = new BeatDetector(1024, 44100.0f);
        detector.setDetectionMode(BeatDetector.DetectionMode.LEVEL_DETECT);

        // 1. Kick-Sample erzeugen, um einen Beat auszulösen
        float[] kick = new float[1024];
        for (int i = 0; i < 1024; i++) {
            kick[i] = (float) (0.95 * Math.sin(2.0 * Math.PI * 60.0 * (i / 44100.0)));
        }

        long now = System.currentTimeMillis();
        detector.detect(kick, 0.70, 0.95, now);

        // 2. Simuliere nächsten Audio-Hop 10ms später ohne Beat (Transient abgeklungen)
        float[] quiet = new float[1024];
        detector.detect(quiet, 0.05, 0.05, now + 10);

        // 3. Engine Tick ruft nacheinander consumeBeat() und consumeBeat(LEVEL_DETECT) auf
        boolean globalBeat = detector.consumeBeat();
        boolean levelBeat = detector.consumeBeat(BeatDetector.DetectionMode.LEVEL_DETECT);

        assertTrue(globalBeat, "Globaler Beat muss vom Latch konsumiert werden");
        assertTrue(levelBeat, "Level-Beat darf NICHT verschluckt werden, auch wenn die Momentan-Variable bereits false ist");
    }

    @Test
    @DisplayName("Verify consumeBeat(BEAT_DETECT) is not swallowed when consumeBeat() was called first")
    void testFftBeatConsumeDoesNotGetSwallowedByGlobalConsume() {
        BeatDetector detector = new BeatDetector(1024, 44100.0f);
        detector.setDetectionMode(BeatDetector.DetectionMode.BEAT_DETECT);

        float[] kick = new float[1024];
        for (int i = 0; i < 1024; i++) {
            kick[i] = (float) (0.95 * Math.sin(2.0 * Math.PI * 65.0 * (i / 44100.0)));
        }

        long now = System.currentTimeMillis();
        detector.detect(kick, 0.70, 0.95, now);

        float[] quiet = new float[1024];
        detector.detect(quiet, 0.05, 0.05, now + 10);

        boolean globalBeat = detector.consumeBeat();
        boolean fftBeat = detector.consumeBeat(BeatDetector.DetectionMode.BEAT_DETECT);

        assertTrue(globalBeat, "Globaler Beat muss vom Latch konsumiert werden");
        assertTrue(fftBeat, "FFT-Beat darf NICHT verschluckt werden, wenn global consumeBeat() zuvor lief");
    }

    @Test
    @DisplayName("Verify default fixture (detectionMode == null) reliably flashes on global beat")
    void testDefaultFixtureFlashesOnGlobalBeat() {
        Sound2LightEngine engine = new Sound2LightEngine(null, null);

        FixtureProfile spot = FixtureLibrary.createGeneric11chSpot();
        FixturePatch spotPatch = new FixturePatch("Cameo ZQ Spot", 1, spot);
        // detectionMode is null (Default / Global)
        assertNull(spotPatch.getDetectionMode());
        spotPatch.setDimmerMode(Sound2LightEngine.DimmerMode.BEAT_PULSE);

        engine.setPatchedFixtures(List.of(spotPatch));
        engine.setDimmerMode(Sound2LightEngine.DimmerMode.BEAT_PULSE);

        // Globalen Beat simulieren
        engine.setSimulatedBpmAndBeat(128.0, true);
        engine.tick();

        byte[] frame = engine.getCurrentDmxFrame();
        int dimmerIdx = spotPatch.getStartAddress() - 1 + spot.findChannelOffset(de.exit.sound2artnet.fixture.ChannelFunction.DIMMER);
        int dimmerVal = frame[dimmerIdx] & 0xFF;

        assertEquals(255, dimmerVal, "Default Fixture muss beim globalen Beat-Flash voll auf 255 aufleuchten");
    }
}

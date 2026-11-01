package de.exit.sound2artnet;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.exit.sound2artnet.audio.BeatDetector;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IndividualFixtureDetectionModeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testFixturePatchSerializationAndCopy() throws Exception {
        FixtureProfile spot = FixtureLibrary.createGeneric9chSpot();
        FixturePatch patch = new FixturePatch("Custom Fixture", 1, spot);
        patch.setDetectionMode(BeatDetector.DetectionMode.BEAT_DETECT);
        patch.setDimmerMode(Sound2LightEngine.DimmerMode.BEAT_PULSE);

        // JSON Serialisierung testen
        String json = mapper.writeValueAsString(patch);
        assertTrue(json.contains("\"detectionMode\":\"BEAT_DETECT\""));
        assertTrue(json.contains("\"dimmerMode\":\"BEAT_PULSE\""));

        // Deserialisierung testen
        FixturePatch deserialized = mapper.readValue(json, FixturePatch.class);
        assertEquals(BeatDetector.DetectionMode.BEAT_DETECT, deserialized.getDetectionMode());
        assertEquals(Sound2LightEngine.DimmerMode.BEAT_PULSE, deserialized.getDimmerMode());

        // Backwards-Compatibility testen: JSON ohne die neuen Felder
        String legacyJson = "{\"id\":\"123\",\"name\":\"Legacy Fixture\",\"universe\":0,\"startAddress\":1,\"profile\":null}";
        FixturePatch legacyPatch = mapper.readValue(legacyJson, FixturePatch.class);
        assertNull(legacyPatch.getDetectionMode(), "Legacy Patch muss null (Standard/Global) als DetectionMode haben");
        assertNull(legacyPatch.getDimmerMode(), "Legacy Patch muss null (Standard/Global) als DimmerMode haben");

        // Copy testen
        FixturePatch copy = patch.copy();
        assertEquals(patch.getDetectionMode(), copy.getDetectionMode());
        assertEquals(patch.getDimmerMode(), copy.getDimmerMode());
    }

    @Test
    public void testBeatDetectorDualDetectionAndConsume() {
        BeatDetector detector = new BeatDetector(1024, 44100.0f);
        detector.setDetectionMode(BeatDetector.DetectionMode.BEAT_DETECT);

        // Simulierte Samples mit reinem Bass-Kick erzeugen
        float[] kickSamples = new float[1024];
        for (int i = 0; i < 1024; i++) {
            double t = i / 44100.0;
            kickSamples[i] = (float) (0.9 * Math.sin(2.0 * Math.PI * 65.0 * t));
        }

        detector.detect(kickSamples, 0.65, 0.90, System.currentTimeMillis());

        // Sowohl global als auch modusspezifisch konsumierbar
        boolean globalBeat = detector.isBeat();
        boolean fftBeat = detector.isBeat(BeatDetector.DetectionMode.BEAT_DETECT);
        assertEquals(globalBeat, fftBeat);

        // Unabhängiges Consume
        detector.consumeBeat();
        assertFalse(detector.isBeat(BeatDetector.DetectionMode.MANUAL));
    }

    @Test
    public void testLaserBeatDetectAndMovingHeadLevelDetectOutput() throws Exception {
        Sound2LightEngine engine = new Sound2LightEngine(null, null);

        // Fixture 1: Laser auf DMX 1 mit BEAT_DETECT & BEAT_PULSE
        FixtureProfile laser = FixtureLibrary.createMinecraftTheatricalLaser();
        FixturePatch laserPatch = new FixturePatch("MC Laser", 1, laser);
        laserPatch.setDetectionMode(BeatDetector.DetectionMode.BEAT_DETECT);
        laserPatch.setDimmerMode(Sound2LightEngine.DimmerMode.BEAT_PULSE);

        // Fixture 2: Moving Head auf DMX 25 mit LEVEL_DETECT & AUDIO_LEVEL
        FixtureProfile spot = FixtureLibrary.createGeneric9chSpot();
        FixturePatch mhPatch = new FixturePatch("Moving Head", 25, spot);
        mhPatch.setDetectionMode(BeatDetector.DetectionMode.LEVEL_DETECT);
        mhPatch.setDimmerMode(Sound2LightEngine.DimmerMode.AUDIO_LEVEL);

        // Fixture 3: Spot auf DMX 50 mit Default/Global & ALWAYS_ON
        FixtureProfile wash = FixtureLibrary.createGeneric9chWash();
        FixturePatch washPatch = new FixturePatch("Wash Par", 50, wash);
        washPatch.setDimmerMode(Sound2LightEngine.DimmerMode.ALWAYS_ON);

        engine.setPatchedFixtures(List.of(laserPatch, mhPatch, washPatch));

        // Szenario A: Reiner Beat-Detect Kick (z. B. FFT Kick), kein Audio Level (RMS = 0.0)
        engine.setSimulatedRms(0.0);
        engine.setSimulatedBeat(BeatDetector.DetectionMode.BEAT_DETECT, true);
        engine.setSimulatedBeat(BeatDetector.DetectionMode.LEVEL_DETECT, false);

        engine.tick();
        byte[] frameA = engine.getCurrentDmxFrame();

        // Laser Dimmer ist an DMX 1 (Index 0)
        int laserDimmerIdx = laserPatch.getStartAddress() - 1 + laser.findChannelOffset(de.exit.sound2artnet.fixture.ChannelFunction.DIMMER);
        int laserDimmerA = frameA[laserDimmerIdx] & 0xFF;
        assertEquals(255, laserDimmerA, "Laser auf BEAT_DETECT & BEAT_PULSE muss beim Kick 255 sein");

        // Moving Head Dimmer via Profil-Offset ermitteln
        int mhDimmerIdx = mhPatch.getStartAddress() - 1 + spot.findChannelOffset(de.exit.sound2artnet.fixture.ChannelFunction.DIMMER);
        int mhDimmerA = frameA[mhDimmerIdx] & 0xFF;
        assertEquals(0, mhDimmerA, "Moving Head auf AUDIO_LEVEL muss bei RMS=0.0 dunkel (0) sein");

        // Wash Dimmer via Profil-Offset ermitteln
        int washDimmerIdx = washPatch.getStartAddress() - 1 + wash.findChannelOffset(de.exit.sound2artnet.fixture.ChannelFunction.DIMMER);
        int washDimmerA = frameA[washDimmerIdx] & 0xFF;
        assertEquals(255, washDimmerA, "Wash Par auf ALWAYS_ON muss 255 sein");

        // Szenario B: Kein Beat-Detect Kick, aber Musikpegel steigt (RMS = 0.28)
        engine.setSimulatedRms(0.28);
        engine.setSimulatedBeat(BeatDetector.DetectionMode.BEAT_DETECT, false);
        engine.setSimulatedBeat(BeatDetector.DetectionMode.LEVEL_DETECT, false);

        // Zeit vergehen lassen, damit der vorherige Beat-Flash abklingt
        Thread.sleep(450);
        engine.tick();
        byte[] frameB = engine.getCurrentDmxFrame();

        int laserDimmerB = frameB[laserDimmerIdx] & 0xFF;
        assertEquals(0, laserDimmerB, "Laser ohne Beat muss nach Abklingen 0 sein");

        int mhDimmerB = frameB[mhDimmerIdx] & 0xFF;
        assertTrue(mhDimmerB > 150, "Moving Head muss dem RMS-Audiolevel folgen: " + mhDimmerB);

        int washDimmerB = frameB[washDimmerIdx] & 0xFF;
        assertEquals(255, washDimmerB, "Wash Par auf ALWAYS_ON bleibt bei 255");

        // Szenario C: Moving Head auf BEAT_PULSE & LEVEL_DETECT schalten
        mhPatch.setDimmerMode(Sound2LightEngine.DimmerMode.BEAT_PULSE);
        // Level-Detect Beat auslösen, aber KEIN Beat-Detect Kick
        engine.setSimulatedBeat(BeatDetector.DetectionMode.LEVEL_DETECT, true);
        engine.setSimulatedBeat(BeatDetector.DetectionMode.BEAT_DETECT, false);

        engine.tick();
        byte[] frameC = engine.getCurrentDmxFrame();

        int laserDimmerC = frameC[laserDimmerIdx] & 0xFF;
        assertEquals(0, laserDimmerC, "Laser auf BEAT_DETECT darf beim LEVEL_DETECT Beat nicht feuern");

        int mhDimmerC = frameC[mhDimmerIdx] & 0xFF;
        assertEquals(255, mhDimmerC, "Moving Head auf LEVEL_DETECT & BEAT_PULSE muss beim Level-Beat voll feuern (255)");
    }
}

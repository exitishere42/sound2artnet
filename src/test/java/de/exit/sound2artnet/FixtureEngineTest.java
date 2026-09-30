package de.exit.sound2artnet;

import de.exit.sound2artnet.engine.MovementPattern;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FixtureEngineTest {

    @Test
    public void testPanTiltComputationAndLimits() {
        FixtureProfile spot = FixtureLibrary.createGeneric9chSpot();
        FixturePatch patch = new FixturePatch("Test MH", 1, spot);

        // Standard 0.0 -> 0, 0.5 -> 128, 1.0 -> 255
        assertEquals(0, patch.computePanDmx(0.0));
        assertEquals(128, patch.computePanDmx(0.5));
        assertEquals(255, patch.computePanDmx(1.0));

        // Invertierung testen
        patch.setInvertPan(true);
        assertEquals(255, patch.computePanDmx(0.0));
        assertEquals(0, patch.computePanDmx(1.0));

        // Limit-Begrenzung testen (z. B. Pan nur zwischen 50 und 200)
        patch.setInvertPan(false);
        patch.setPanMin(50);
        patch.setPanMax(200);
        assertEquals(50, patch.computePanDmx(0.0));
        assertEquals(125, patch.computePanDmx(0.5));
        assertEquals(200, patch.computePanDmx(1.0));
    }

    @Test
    public void testSound2LightEngineFrameGeneration() {
        Sound2LightEngine engine = new Sound2LightEngine(null, null);

        FixtureProfile wash = FixtureLibrary.createGeneric9chWash();
        // Wash an DMX-Adresse 10 patchen (DMX 10 bis 18)
        FixturePatch patch = new FixturePatch("Wash 1", 10, wash);
        engine.setPatchedFixtures(List.of(patch));
        engine.setMovementPattern(MovementPattern.CIRCLE);
        engine.setDimmerMode(Sound2LightEngine.DimmerMode.ALWAYS_ON);

        // Frame berechnen
        engine.tick();

        byte[] frame = engine.getCurrentDmxFrame();
        assertNotNull(frame);
        assertEquals(512, frame.length);

        // Ungepatchte Kanäle vor Adresse 10 (Indizes 0 bis 8) müssen 0 sein
        for (int i = 0; i < 9; i++) {
            assertEquals(0, frame[i] & 0xFF, "Kanal " + (i + 1) + " muss 0 sein");
        }

        // Gepatchte Kanäle ab DMX 10 (Index 9..17):
        // 9: Pan (0..255)
        // 10: Tilt (0..255)
        // 11: Speed (Default 0)
        // 12: Dimmer (Always on = 255)
        // 13: Strobe (Default 255 = open)
        // 14: Red
        // 15: Green
        // 16: Blue
        // 17: White
        int dimmerVal = frame[9 + 3] & 0xFF;
        assertEquals(255, dimmerVal, "Dimmer an DMX 13 muss 255 sein (Always ON)");

        int strobeVal = frame[9 + 4] & 0xFF;
        assertEquals(0, strobeVal, "Strobe an DMX 14 muss 0 sein");
    }

    @Test
    public void testIndependentMovementAndLightLocking() {
        Sound2LightEngine engine = new Sound2LightEngine(null, null);
        FixtureProfile wash = FixtureLibrary.createGeneric9chWash();
        FixturePatch patch = new FixturePatch("Wash 1", 1, wash);
        engine.setPatchedFixtures(List.of(patch));
        engine.setDimmerMode(Sound2LightEngine.DimmerMode.ALWAYS_ON);

        // 1. Bewegung aus, nur Licht an
        engine.setMovementEnabled(false);
        engine.setLightEnabled(true);
        engine.tick();
        byte[] frameLightOnly = engine.getCurrentDmxFrame();
        assertEquals(128, frameLightOnly[0] & 0xFF, "Pan muss auf Default (128) gesperrt bleiben");
        assertEquals(128, frameLightOnly[1] & 0xFF, "Tilt muss auf Default (128) gesperrt bleiben");
        assertEquals(255, frameLightOnly[3] & 0xFF, "Dimmer muss aktiv (255) sein");

        // 2. Licht aus, nur Bewegung an
        engine.setMovementEnabled(true);
        engine.setLightEnabled(false);
        engine.tick();
        byte[] frameMoveOnly = engine.getCurrentDmxFrame();
        assertEquals(0, frameMoveOnly[3] & 0xFF, "Dimmer muss bei deaktiviertem Licht 0 sein");
        assertEquals(0, frameMoveOnly[5] & 0xFF, "Rot muss bei deaktiviertem Licht 0 sein");
        assertEquals(0, frameMoveOnly[6] & 0xFF, "Grün muss bei deaktiviertem Licht 0 sein");
        assertEquals(0, frameMoveOnly[7] & 0xFF, "Blau muss bei deaktiviertem Licht 0 sein");
    }

    @Test
    public void testBpmSpeedTiersAndStrobeToggle() {
        Sound2LightEngine engine = new Sound2LightEngine(null, null);
        FixtureProfile wash = FixtureLibrary.createGeneric9chWash();
        FixturePatch patch = new FixturePatch("Wash 1", 1, wash);
        engine.setPatchedFixtures(List.of(patch));
        engine.setMovementPattern(MovementPattern.AUTO_BPM);
        engine.setDimmerMode(Sound2LightEngine.DimmerMode.ALWAYS_ON);
        engine.setStrobeEnabled(true);

        // 1. Bei 90 BPM -> Stufe SLOW ("Langsam"), kein Strobo trotz aktivem Strobo-Toggle
        engine.setSimulatedBpmAndBeat(90.0, true);
        engine.tick();
        assertEquals(Sound2LightEngine.SpeedTier.SLOW, engine.getCurrentSpeedTier(), "90 BPM muss als Stufe SLOW (Langsam) eingestuft werden");
        byte[] frame90 = engine.getCurrentDmxFrame();
        assertEquals(0, frame90[4] & 0xFF, "Bei 90 BPM (Langsam) darf Strobo nicht auslösen");

        // 2. Bei 120 BPM -> Stufe FAST ("Schnell"), Strobo zündet im schnellen Effekt auf den Beat (DMX 215)
        engine.setSimulatedBpmAndBeat(120.0, true);
        engine.tick();
        assertEquals(Sound2LightEngine.SpeedTier.FAST, engine.getCurrentSpeedTier(), "120 BPM muss als Stufe FAST (Schnell) eingestuft werden");
        byte[] frame120 = engine.getCurrentDmxFrame();
        assertEquals(215, frame120[4] & 0xFF, "Bei 120 BPM (Schnell) und aktiviertem Strobo-Toggle muss Strobo (215) zünden");

        // 3. Bei 156 BPM -> Stufe RAVE ("Extrem"), intensives Strobo (DMX 255)
        engine.setSimulatedBpmAndBeat(156.0, true);
        engine.tick();
        assertEquals(Sound2LightEngine.SpeedTier.RAVE, engine.getCurrentSpeedTier(), "156 BPM muss als Stufe RAVE (Extrem) eingestuft werden");
        byte[] frame156 = engine.getCurrentDmxFrame();
        assertEquals(255, frame156[4] & 0xFF, "Bei 156 BPM (Extrem) muss schnelles Rave-Strobo (255) zünden");

        // 4. Strobo-Toggle deaktivieren -> selbst bei 156 BPM und Beat muss Strobo strikt 0 sein
        engine.setStrobeEnabled(false);
        engine.setSimulatedBpmAndBeat(156.0, true);
        engine.tick();
        byte[] frameStrobeOff = engine.getCurrentDmxFrame();
        assertEquals(0, frameStrobeOff[4] & 0xFF, "Bei deaktiviertem Strobo-Toggle muss der Strobe-Kanal immer 0 bleiben");
    }

    @Test
    public void testSmoothEffectTransitionsAt170Bpm() {
        de.exit.sound2artnet.engine.MovementGenerator gen = new de.exit.sound2artnet.engine.MovementGenerator();
        FixtureProfile spot = FixtureLibrary.createGeneric9chSpot();
        FixturePatch patch = new FixturePatch("MH 170 BPM", 1, spot);
        patch.setPhaseOffset(Math.PI);

        double dt = 0.025; // 40 FPS
        double bpm = 170.0;
        double beatIntervalSec = 60.0 / bpm; // ~0.3529s
        double timeSinceLastBeat = 0.0;

        double[] prevPos = gen.computePosition(patch, MovementPattern.AUTO_BPM, 0.7);
        boolean transitionObserved = false;

        // Simuliere 25 Sekunden bei 170 BPM (mehrere Effektwechsel in AUTO_BPM)
        for (int frame = 0; frame < 1000; frame++) {
            timeSinceLastBeat += dt;
            boolean isBeat = false;
            if (timeSinceLastBeat >= beatIntervalSec) {
                timeSinceLastBeat -= beatIntervalSec;
                isBeat = true;
            }
            gen.update(isBeat, 0.7, bpm, Sound2LightEngine.SpeedTier.RAVE, dt);
            double[] pos = gen.computePosition(patch, MovementPattern.AUTO_BPM, 0.7);

            if (gen.getPatternTransitionProgress() < 1.0) {
                transitionObserved = true;
            }

            double dPan = Math.abs(pos[0] - prevPos[0]);
            double dTilt = Math.abs(pos[1] - prevPos[1]);
            assertTrue(dPan < 0.14, "Kein harter Pan-Sprung bei 170 BPM erlaubt (dPan=" + dPan + " bei Frame " + frame + ")");
            assertTrue(dTilt < 0.14, "Kein harter Tilt-Sprung bei 170 BPM erlaubt (dTilt=" + dTilt + " bei Frame " + frame + ")");
            prevPos = pos;
        }
        assertTrue(transitionObserved, "Während 25s auf 170 BPM muss ein weicher Effekt-Übergang stattgefunden haben");
    }
}

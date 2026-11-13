package de.exit.sound2artnet;

import de.exit.sound2artnet.config.AppConfig;
import de.exit.sound2artnet.config.ArtNetPreset;
import de.exit.sound2artnet.engine.ColorEngine;
import de.exit.sound2artnet.engine.MovementGenerator;
import de.exit.sound2artnet.engine.MovementPattern;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IdleAnimationTest {

    @Test
    public void testMovementGeneratorIdleAnimationToggle() {
        MovementGenerator mg = new MovementGenerator();
        assertTrue(mg.isIdleAnimationEnabled(), "Idle-Animation muss standardmaessig aktiv sein");

        FixtureProfile spot = FixtureLibrary.createGeneric9chSpot();
        FixturePatch patch = new FixturePatch("MH 1", 1, spot);

        // 1. Wenn Idle-Animation aktiv ist und Tier = IDLE:
        mg.setIdleAnimationEnabled(true);
        mg.update(false, 0.0, 0.0, Sound2LightEngine.SpeedTier.IDLE, 0.025);
        double[] pos1 = mg.computePosition(patch, MovementPattern.WAVE, 0.0);
        assertNotNull(pos1);

        // Nach weiteren Updates soll sich die Position bei aktiver Idle-Animation leicht bewegen
        for (int i = 0; i < 20; i++) {
            mg.update(false, 0.0, 0.0, Sound2LightEngine.SpeedTier.IDLE, 0.025);
        }
        double[] pos2 = mg.computePosition(patch, MovementPattern.WAVE, 0.0);
        // Phase hat sich weiterbewegt
        assertTrue(Math.abs(pos2[0] - pos1[0]) > 0.0001 || Math.abs(pos2[1] - pos1[1]) > 0.0001,
                "Position muss sich bei aktiver Idle-Animation veraendern");

        // 2. Wenn Idle-Animation deaktiviert ist und Tier = IDLE:
        mg.setIdleAnimationEnabled(false);
        assertFalse(mg.isIdleAnimationEnabled());

        // Mehrere Ticks durchlaufen, damit der Follower-Filter sanft auf die Mitte (0.5, 0.5) faehrt
        for (int i = 0; i < 50; i++) {
            mg.update(false, 0.0, 0.0, Sound2LightEngine.SpeedTier.IDLE, 0.025);
            mg.computePosition(patch, MovementPattern.WAVE, 0.0);
        }

        double[] centerPos = mg.computePosition(patch, MovementPattern.WAVE, 0.0);
        assertEquals(0.5, centerPos[0], 0.02, "Pan muss bei deaktivierter Idle-Animation auf 0.5 (Mitte) sein");
        assertEquals(0.5, centerPos[1], 0.02, "Tilt muss bei deaktivierter Idle-Animation auf 0.5 (Mitte) sein");
    }

    @Test
    public void testSound2LightEngineIdleAnimationIntegration() {
        Sound2LightEngine engine = new Sound2LightEngine(null, null);
        assertTrue(engine.isIdleAnimationEnabled(), "Engine Idle-Animation muss default true sein");

        engine.setIdleAnimationEnabled(false);
        assertFalse(engine.isIdleAnimationEnabled());

        engine.setIdleAnimationEnabled(true);
        assertTrue(engine.isIdleAnimationEnabled());
    }

    @Test
    public void testAppConfigAndPresetPersistence() {
        AppConfig config = new AppConfig();
        assertTrue(config.isIdleAnimationEnabled(), "Config Idle-Animation default true");

        config.setIdleAnimationEnabled(false);
        assertFalse(config.isIdleAnimationEnabled());

        ArtNetPreset preset = new ArtNetPreset();
        assertTrue(preset.isIdleAnimationEnabled(), "Preset Idle-Animation default true");

        preset.setIdleAnimationEnabled(false);
        assertFalse(preset.isIdleAnimationEnabled());
    }
}

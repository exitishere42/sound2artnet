package de.exit.sound2artnet;

import de.exit.sound2artnet.config.AppConfig;
import de.exit.sound2artnet.config.ConfigManager;
import de.exit.sound2artnet.engine.ColorEngine;
import de.exit.sound2artnet.engine.MovementPattern;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {

    private final File configFile = new File("config.json");

    @AfterEach
    public void cleanup() {
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    public void testSaveAndLoadConfig() {
        AppConfig config = new AppConfig();
        config.setTargetIp("192.168.1.100");
        config.setUniverse(3);
        config.setFps(35);
        config.setAudioDevice("CABLE Output (VB-Audio Virtual Cable)");
        config.setGain(1.75);
        config.setAgc(false);
        config.setMovementPattern(MovementPattern.FIGURE_8);
        config.setMovementSpeed(1.8);
        config.setMovementSize(0.65);
        config.setDimmerMode(Sound2LightEngine.DimmerMode.BEAT_PULSE);
        config.setColorPalette(ColorEngine.Palette.CYBERPUNK);

        FixturePatch patch = new FixturePatch("Custom Spot", 25, FixtureLibrary.createGeneric9chSpot());
        patch.setInvertTilt(true);
        config.setFixtures(List.of(patch));

        ConfigManager.saveConfig(config);
        assertTrue(configFile.exists(), "config.json Datei muss erstellt werden");

        AppConfig loaded = ConfigManager.loadConfig();
        assertNotNull(loaded);
        assertEquals("192.168.1.100", loaded.getTargetIp());
        assertEquals(3, loaded.getUniverse());
        assertEquals(35, loaded.getFps());
        assertEquals("CABLE Output (VB-Audio Virtual Cable)", loaded.getAudioDevice());
        assertEquals(1.75, loaded.getGain(), 0.001);
        assertFalse(loaded.isAgc());
        assertEquals(MovementPattern.FIGURE_8, loaded.getMovementPattern());
        assertEquals(1.8, loaded.getMovementSpeed(), 0.001);
        assertEquals(0.65, loaded.getMovementSize(), 0.001);
        assertEquals(Sound2LightEngine.DimmerMode.BEAT_PULSE, loaded.getDimmerMode());
        assertEquals(ColorEngine.Palette.CYBERPUNK, loaded.getColorPalette());
        assertEquals(1, loaded.getFixtures().size());
        assertEquals("Custom Spot", loaded.getFixtures().get(0).getName());
        assertEquals(25, loaded.getFixtures().get(0).getStartAddress());
        assertTrue(loaded.getFixtures().get(0).isInvertTilt());
    }
}

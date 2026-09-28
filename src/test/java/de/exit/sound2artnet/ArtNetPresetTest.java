package de.exit.sound2artnet;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.exit.sound2artnet.artnet.ArtNetSender;
import de.exit.sound2artnet.config.AppConfig;
import de.exit.sound2artnet.config.ArtNetPreset;
import de.exit.sound2artnet.config.ConfigManager;
import de.exit.sound2artnet.engine.ColorEngine;
import de.exit.sound2artnet.engine.MovementPattern;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ArtNetPresetTest {

    private final File configFile = new File("config.json");
    private byte[] backupBytes = null;

    @BeforeEach
    public void backup() throws IOException {
        if (configFile.exists()) {
            backupBytes = Files.readAllBytes(configFile.toPath());
        } else {
            backupBytes = null;
        }
    }

    @AfterEach
    public void cleanup() throws IOException {
        if (backupBytes != null) {
            Files.write(configFile.toPath(), backupBytes);
        } else if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    public void testPresetCreationAndFixtureIsolation() {
        FixturePatch fp = new FixturePatch("Moving Head 1", 1, FixtureLibrary.createGeneric11chSpot());
        fp.setInvertPan(true);
        fp.setPanMin(10);
        fp.setPanMax(240);

        List<FixturePatch> list = new ArrayList<>();
        list.add(fp);

        ArtNetPreset preset = new ArtNetPreset(
                "p-1",
                "Club Stage A",
                "Preset for Main Hall",
                "192.168.1.255",
                0,
                40,
                list,
                MovementPattern.CIRCLE,
                1.2,
                0.8,
                Sound2LightEngine.DimmerMode.AUDIO_LEVEL,
                ColorEngine.Palette.CLUB_NEON,
                true,
                true,
                true,
                null
        );

        assertEquals("p-1", preset.getId());
        assertEquals("Club Stage A", preset.getName());
        assertEquals("192.168.1.255", preset.getTargetIp());
        assertEquals(0, preset.getUniverse());
        assertEquals(40, preset.getFps());
        assertEquals(1, preset.getFixtures().size());

        // Test copyFixtures isolation
        List<FixturePatch> copies = preset.copyFixtures();
        assertEquals(1, copies.size());
        assertEquals("Moving Head 1", copies.get(0).getName());
        assertTrue(copies.get(0).isInvertPan());

        // Modifying copy must not affect original preset fixtures
        copies.get(0).setName("Modified Name");
        copies.get(0).setInvertPan(false);
        assertEquals("Moving Head 1", preset.getFixtures().get(0).getName());
        assertTrue(preset.getFixtures().get(0).isInvertPan());
    }

    @Test
    public void testAppConfigDefaultPresetGeneration() {
        AppConfig config = new AppConfig();
        config.setTargetIp("192.168.200.232");
        config.setUniverse(2);
        config.setFps(44);

        assertTrue(config.getPresets().isEmpty());
        config.ensureDefaultPreset();

        assertFalse(config.getPresets().isEmpty());
        assertEquals(1, config.getPresets().size());
        ArtNetPreset defaultPreset = config.getPresets().get(0);
        assertEquals("Standard", defaultPreset.getName());
        assertEquals("192.168.200.232", defaultPreset.getTargetIp());
        assertEquals(2, defaultPreset.getUniverse());
        assertEquals(44, defaultPreset.getFps());
        assertEquals(defaultPreset.getId(), config.getActivePresetId());
    }

    @Test
    public void testPresetPersistenceViaConfigManager() throws IOException {
        AppConfig config = new AppConfig();
        config.setTargetIp("192.168.1.255");
        config.setUniverse(0);

        ArtNetPreset p1 = new ArtNetPreset(
                "p-hall-1",
                "Halle 1",
                "Venue 1 setup",
                "192.168.1.255",
                0,
                40,
                List.of(new FixturePatch("Spot 1", 1, FixtureLibrary.createGeneric9chSpot())),
                MovementPattern.CIRCLE,
                1.0,
                0.7,
                Sound2LightEngine.DimmerMode.AUDIO_LEVEL,
                ColorEngine.Palette.CLUB_NEON,
                true,
                true,
                false,
                null
        );

        ArtNetPreset p2 = new ArtNetPreset(
                "p-hall-2",
                "Halle 2",
                "Venue 2 setup",
                "192.168.200.232",
                2,
                45,
                List.of(new FixturePatch("Beam 1", 1, FixtureLibrary.createGeneric11chSpot())),
                MovementPattern.PAN_SWEEP,
                1.5,
                0.9,
                Sound2LightEngine.DimmerMode.BEAT_PULSE,
                ColorEngine.Palette.FIRE_AND_ICE,
                true,
                true,
                true,
                null
        );

        config.getPresets().add(p1);
        config.getPresets().add(p2);
        config.setActivePresetId(p2.getId());

        ConfigManager.saveConfig(config);

        AppConfig loaded = ConfigManager.loadConfig();
        assertNotNull(loaded.getPresets());
        assertEquals(2, loaded.getPresets().size());
        assertEquals("p-hall-2", loaded.getActivePresetId());

        ArtNetPreset loadedP2 = loaded.getPresets().stream()
                .filter(p -> p.getId().equals("p-hall-2"))
                .findFirst()
                .orElse(null);
        assertNotNull(loadedP2);
        assertEquals("Halle 2", loadedP2.getName());
        assertEquals("192.168.200.232", loadedP2.getTargetIp());
        assertEquals(2, loadedP2.getUniverse());
        assertEquals(45, loadedP2.getFps());
        assertEquals(1, loadedP2.getFixtures().size());
        assertEquals("Beam 1", loadedP2.getFixtures().get(0).getName());
    }

    @Test
    public void testArtNetSenderLiveTargetUpdate() throws Exception {
        ArtNetSender sender = new ArtNetSender();
        sender.start("127.0.0.1", 0);
        assertTrue(sender.isRunning());
        assertEquals("127.0.0.1", sender.getTargetAddress().getHostAddress());
        assertEquals(0, sender.getUniverse());

        sender.updateTarget("192.168.200.232", 2);
        assertEquals("192.168.200.232", sender.getTargetAddress().getHostAddress());
        assertEquals(2, sender.getUniverse());

        sender.stop();
        assertFalse(sender.isRunning());
    }
}

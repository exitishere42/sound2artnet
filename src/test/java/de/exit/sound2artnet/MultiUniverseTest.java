package de.exit.sound2artnet;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.exit.sound2artnet.artnet.ArtNetSender;
import de.exit.sound2artnet.audio.AudioCaptureService;
import de.exit.sound2artnet.config.ArtNetPreset;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.ui.MainWindow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MultiUniverseTest {

    @Test
    public void testParseUniverses() {
        assertEquals(List.of(0), MainWindow.parseUniverses(null));
        assertEquals(List.of(0), MainWindow.parseUniverses(""));
        assertEquals(List.of(0), MainWindow.parseUniverses("   "));
        assertEquals(List.of(0), MainWindow.parseUniverses("0"));
        assertEquals(List.of(0, 1), MainWindow.parseUniverses("0, 1"));
        assertEquals(List.of(0, 1, 2, 3), MainWindow.parseUniverses("0-3"));
        assertEquals(List.of(0, 2, 3, 4, 7), MainWindow.parseUniverses("0, 2-4, 7"));
        assertEquals(List.of(1, 5), MainWindow.parseUniverses("5; 1"));
        // Out of bounds filtering (only 0..15)
        assertEquals(List.of(0, 15), MainWindow.parseUniverses("0, 15, 20, -5"));
    }

    @Test
    public void testFixturePatchUniversePropertyAndCopy() {
        FixturePatch patch = new FixturePatch("Robe MegaPointe Uni 1", 1, FixtureLibrary.createGeneric11chSpot());
        assertEquals(0, patch.getUniverse());

        patch.setUniverse(1);
        assertEquals(1, patch.getUniverse());

        FixturePatch copy = patch.copy();
        assertEquals(1, copy.getUniverse());
        assertEquals("Robe MegaPointe Uni 1", copy.getName());
        assertEquals(1, copy.getStartAddress());

        copy.setUniverse(2);
        assertEquals(1, patch.getUniverse());
        assertEquals(2, copy.getUniverse());
    }

    @Test
    public void testArtNetPresetMultiUniverseSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ArtNetPreset preset = new ArtNetPreset();
        preset.setName("MultiUni Setup");
        preset.setTargetUniverses(List.of(0, 1, 2));

        assertEquals(List.of(0, 1, 2), preset.getTargetUniverses());
        assertEquals("0, 1, 2", preset.formatUniversesText());
        assertEquals(0, preset.getUniverse());

        String json = mapper.writeValueAsString(preset);
        assertTrue(json.contains("\"targetUniverses\":[0,1,2]"));

        ArtNetPreset deserialized = mapper.readValue(json, ArtNetPreset.class);
        assertEquals("MultiUni Setup", deserialized.getName());
        assertEquals(List.of(0, 1, 2), deserialized.getTargetUniverses());
        assertEquals("0, 1, 2", deserialized.formatUniversesText());
    }

    @Test
    public void testEngineMultiUniverseFrameIsolation() {
        AudioCaptureService audioService = new AudioCaptureService();
        ArtNetSender artNetSender = new ArtNetSender();
        Sound2LightEngine engine = new Sound2LightEngine(audioService, artNetSender);

        // Fixture 1 in Universe 0: Ch 1..11
        FixturePatch f1 = new FixturePatch("MH Uni 0", 1, FixtureLibrary.createGeneric11chSpot());
        f1.setUniverse(0);

        // Fixture 2 in Universe 1: Ch 1..11
        FixturePatch f2 = new FixturePatch("MH Uni 1", 1, FixtureLibrary.createGeneric11chSpot());
        f2.setUniverse(1);

        engine.setPatchedFixtures(List.of(f1, f2));
        engine.setTargetUniverses(List.of(0, 1));
        engine.setDimmerMode(Sound2LightEngine.DimmerMode.ALWAYS_ON);
        engine.setAlwaysOnIntensity(1.0);

        assertEquals(2, engine.getActiveUniverses().size());
        assertTrue(engine.getActiveUniverses().contains(0));
        assertTrue(engine.getActiveUniverses().contains(1));

        engine.tick();

        byte[] frameUni0 = engine.getCurrentDmxFrame(0);
        byte[] frameUni1 = engine.getCurrentDmxFrame(1);

        assertNotNull(frameUni0);
        assertNotNull(frameUni1);
        assertEquals(512, frameUni0.length);
        assertEquals(512, frameUni1.length);

        // Dimmer channel in 11ch spot is channel 6 (index 5)
        int dimmerUni0 = frameUni0[5] & 0xFF;
        int dimmerUni1 = frameUni1[5] & 0xFF;

        assertEquals(255, dimmerUni0, "Universe 0 dimmer should be 255");
        assertEquals(255, dimmerUni1, "Universe 1 dimmer should be 255");

        // Universe 2 has no fixtures and is not configured
        byte[] frameUni2 = engine.getCurrentDmxFrame(2);
        assertNotNull(frameUni2);
        assertEquals(0, frameUni2[5] & 0xFF);
    }
}

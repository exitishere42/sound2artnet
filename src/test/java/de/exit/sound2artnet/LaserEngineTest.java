package de.exit.sound2artnet;

import de.exit.sound2artnet.artnet.ArtNetSender;
import de.exit.sound2artnet.audio.AudioCaptureService;
import de.exit.sound2artnet.engine.ColorEngine;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;
import de.exit.sound2artnet.fixture.qlc.QlcFixtureDefinition;
import de.exit.sound2artnet.fixture.qlc.QlcFixtureParser;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LaserEngineTest {

    @Test
    public void testMinecraftTheatricalLaserQlcParsing() throws Exception {
        File qxfFile = new File("C:\\Users\\timo\\Documents\\QLC+ Saves\\qxf\\Minecraft-Theatrical-Laser.qxf");
        if (!qxfFile.exists()) {
            System.out.println("QXF file not found at " + qxfFile.getAbsolutePath() + ", skipping local file test.");
            return;
        }

        QlcFixtureDefinition def = QlcFixtureParser.parse(qxfFile);
        assertNotNull(def);
        assertEquals("Minecraft Theatrical", def.getManufacturer());
        assertEquals("Laser", def.getModel());

        FixtureProfile profile = def.toFixtureProfile("19 Channel");
        assertNotNull(profile);
        assertEquals(19, profile.getChannelCount());

        assertEquals(ChannelFunction.DIMMER, profile.getChannels().get(0).getFunction());
        assertEquals(ChannelFunction.RED, profile.getChannels().get(1).getFunction());
        assertEquals(ChannelFunction.GREEN, profile.getChannels().get(2).getFunction());
        assertEquals(ChannelFunction.BLUE, profile.getChannels().get(3).getFunction());

        // Beam 2
        assertEquals(ChannelFunction.RED, profile.getChannels().get(4).getFunction());
        assertEquals(ChannelFunction.GREEN, profile.getChannels().get(5).getFunction());
        assertEquals(ChannelFunction.BLUE, profile.getChannels().get(6).getFunction());

        // Beam 3
        assertEquals(ChannelFunction.RED, profile.getChannels().get(7).getFunction());
        assertEquals(ChannelFunction.GREEN, profile.getChannels().get(8).getFunction());
        assertEquals(ChannelFunction.BLUE, profile.getChannels().get(9).getFunction());

        // Laser Effekte
        assertEquals(ChannelFunction.LASER_PATTERN, profile.getChannels().get(10).getFunction());
        assertEquals(ChannelFunction.LASER_SIZE, profile.getChannels().get(11).getFunction());
        assertEquals(ChannelFunction.LASER_AMPLITUDE, profile.getChannels().get(12).getFunction());
        assertEquals(ChannelFunction.LASER_SPEED, profile.getChannels().get(13).getFunction());
        assertEquals(ChannelFunction.LASER_ROTATION, profile.getChannels().get(14).getFunction());
        assertEquals(ChannelFunction.PAN, profile.getChannels().get(15).getFunction());
        assertEquals(ChannelFunction.TILT, profile.getChannels().get(16).getFunction());
        assertEquals(ChannelFunction.FOCUS, profile.getChannels().get(17).getFunction());
        assertEquals(ChannelFunction.LASER_PERSISTENCE, profile.getChannels().get(18).getFunction());
    }

    @Test
    public void testFixtureLibraryBuiltinLaser() {
        FixtureProfile laserProf = FixtureLibrary.createMinecraftTheatricalLaser();
        assertNotNull(laserProf);
        assertEquals(19, laserProf.getChannelCount());
        assertEquals(ChannelFunction.DIMMER, laserProf.getChannels().get(0).getFunction());
        assertEquals(ChannelFunction.LASER_PATTERN, laserProf.getChannels().get(10).getFunction());
        assertEquals(ChannelFunction.LASER_SIZE, laserProf.getChannels().get(11).getFunction());
        assertEquals(ChannelFunction.LASER_PERSISTENCE, laserProf.getChannels().get(18).getFunction());

        FixtureProfile mirrorProf = FixtureLibrary.createMinecraftTheatricalLaserMirror();
        assertNotNull(mirrorProf);
        assertEquals(5, mirrorProf.getChannelCount());
    }

    @Test
    public void testColorEngineMultiHeadColorSpread() {
        ColorEngine colorEngine = new ColorEngine();
        colorEngine.setPalette(ColorEngine.Palette.CLUB_NEON);

        Color c0 = colorEngine.getColorForHead(0);
        Color c1 = colorEngine.getColorForHead(1);
        Color c2 = colorEngine.getColorForHead(2);

        assertNotNull(c0);
        assertNotNull(c1);
        assertNotNull(c2);
        // Unterschiedliche Farben für verschiedene Laser-Köpfe
        assertNotEquals(c0, c1);
        assertNotEquals(c1, c2);
    }

    @Test
    public void testSound2LightEngineLaserOutput() {
        AudioCaptureService audio = new AudioCaptureService();
        ArtNetSender sender = new ArtNetSender();
        Sound2LightEngine engine = new Sound2LightEngine(audio, sender);

        FixtureProfile laserProf = FixtureLibrary.createMinecraftTheatricalLaser();
        FixturePatch patch = new FixturePatch("MC Laser", 1, laserProf);
        engine.setPatchedFixtures(List.of(patch));
        engine.setSimulatedBpmAndBeat(128.0, false);
        engine.setSimulatedRms(0.5);

        engine.tick();
        byte[] frame = engine.getCurrentDmxFrame();

        int dimmer = frame[0] & 0xFF;
        int r1 = frame[1] & 0xFF;
        int g1 = frame[2] & 0xFF;
        int b1 = frame[3] & 0xFF;
        int pattern = frame[10] & 0xFF;
        int size = frame[11] & 0xFF;
        int amp = frame[12] & 0xFF;
        int speed = frame[13] & 0xFF;
        int focus = frame[17] & 0xFF;
        int persistence = frame[18] & 0xFF;

        assertEquals(255, dimmer);
        // Beams müssen aktiv sein
        assertTrue(r1 > 0 || g1 > 0 || b1 > 0, "Beam 1 muss Farbe haben");
        // Laser-Effekte dürfen NIEMALS 0 sein!
        assertTrue(pattern > 0, "Pattern muss aktiv sein: " + pattern);
        assertTrue(size >= 140, "Size muss aufgefächert sein: " + size);
        assertTrue(amp >= 100, "Amplitude muss aktiv sein: " + amp);
        assertTrue(speed > 0, "Speed muss aktiv sein: " + speed);
        assertTrue(focus >= 180, "Focus muss scharf sein: " + focus);
        assertTrue(persistence >= 200, "Persistence muss hoch sein: " + persistence);

        // Blackout Test
        engine.setBlackout(true);
        engine.tick();
        byte[] boFrame = engine.getCurrentDmxFrame();
        assertEquals(0, boFrame[0] & 0xFF, "Dimmer bei Blackout 0");
        assertEquals(0, boFrame[1] & 0xFF, "Red bei Blackout 0");
        assertEquals(0, boFrame[10] & 0xFF, "Pattern bei Blackout 0");
        assertEquals(0, boFrame[11] & 0xFF, "Size bei Blackout 0");
    }
}

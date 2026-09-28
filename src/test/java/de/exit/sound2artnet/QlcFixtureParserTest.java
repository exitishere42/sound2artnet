package de.exit.sound2artnet;

import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.fixture.FixtureProfile;
import de.exit.sound2artnet.fixture.qlc.QlcFixtureDefinition;
import de.exit.sound2artnet.fixture.qlc.QlcFixtureParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class QlcFixtureParserTest {

    private static final String SAMPLE_QXF = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE FixtureDefinition>
        <FixtureDefinition xmlns="http://www.qlcplus.org/FixtureDefinition">
         <Creator>
          <Name>Q Light Controller Plus</Name>
          <Version>5.2.0</Version>
         </Creator>
         <Manufacturer>UKing</Manufacturer>
         <Model>ZQ-02042</Model>
         <Type>Moving Head</Type>
         <Channel Name="Pan" Preset="PositionPan"/>
         <Channel Name="Pan fine" Preset="PositionPanFine"/>
         <Channel Name="Tilt" Preset="PositionTilt"/>
         <Channel Name="Tilt fine" Preset="PositionTiltFine"/>
         <Channel Name="P/T speed" Preset="SpeedPanTiltFastSlow"/>
         <Channel Name="Dimming" Preset="IntensityMasterDimmer"/>
         <Channel Name="Strobe" Preset="ShutterStrobeSlowFast"/>
         <Channel Name="Red" Preset="IntensityRed"/>
         <Channel Name="Green" Preset="IntensityGreen"/>
         <Channel Name="Blue" Preset="IntensityBlue"/>
         <Channel Name="White" Preset="IntensityWhite"/>
         <Channel Name="Mode">
          <Group Byte="0">Effect</Group>
         </Channel>
         <Channel Name="Reset">
          <Group Byte="0">Maintenance</Group>
         </Channel>
         <Mode Name="13 Channel">
          <Channel Number="0">Pan</Channel>
          <Channel Number="1">Pan fine</Channel>
          <Channel Number="2">Tilt</Channel>
          <Channel Number="3">Tilt fine</Channel>
          <Channel Number="4">P/T speed</Channel>
          <Channel Number="5">Dimming</Channel>
          <Channel Number="6">Strobe</Channel>
          <Channel Number="7">Red</Channel>
          <Channel Number="8">Green</Channel>
          <Channel Number="9">Blue</Channel>
          <Channel Number="10">White</Channel>
          <Channel Number="11">Mode</Channel>
          <Channel Number="12">Reset</Channel>
         </Mode>
         <Physical>
          <Focus Type="Fixed" PanMax="540" TiltMax="270"/>
         </Physical>
        </FixtureDefinition>
        """;

    @Test
    public void testParseSampleQxf() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(SAMPLE_QXF.getBytes(StandardCharsets.UTF_8));
        QlcFixtureDefinition def = QlcFixtureParser.parse(in);

        assertNotNull(def);
        assertEquals("UKing", def.getManufacturer());
        assertEquals("ZQ-02042", def.getModel());
        assertEquals("Moving Head", def.getType());
        assertEquals(540, def.getPanMax());
        assertEquals(270, def.getTiltMax());

        assertEquals(1, def.getModes().size());
        QlcFixtureDefinition.QlcMode mode = def.getModes().get(0);
        assertEquals("13 Channel", mode.getName());
        assertEquals(13, mode.getChannelCount());

        FixtureProfile profile = def.toFixtureProfile("13 Channel");
        assertNotNull(profile);
        assertEquals(13, profile.getChannelCount());

        // Kanäle und Funktionen prüfen
        assertEquals(ChannelFunction.PAN, profile.getChannels().get(0).getFunction());
        assertEquals(128, profile.getChannels().get(0).getDefaultValue(), "Pan sollte standardmäßig 128 (Mitte) sein");
        assertEquals(ChannelFunction.PAN_FINE, profile.getChannels().get(1).getFunction());
        assertEquals(ChannelFunction.TILT, profile.getChannels().get(2).getFunction());
        assertEquals(128, profile.getChannels().get(2).getDefaultValue(), "Tilt sollte standardmäßig 128 (gerade runter) sein");
        assertEquals(ChannelFunction.TILT_FINE, profile.getChannels().get(3).getFunction());
        assertEquals(ChannelFunction.PAN_TILT_SPEED, profile.getChannels().get(4).getFunction());
        assertEquals(ChannelFunction.DIMMER, profile.getChannels().get(5).getFunction());
        assertEquals(ChannelFunction.STROBE, profile.getChannels().get(6).getFunction());
        assertEquals(0, profile.getChannels().get(6).getDefaultValue(), "Strobe sollte standardmäßig 0 sein");
        assertEquals(ChannelFunction.RED, profile.getChannels().get(7).getFunction());
        assertEquals(ChannelFunction.GREEN, profile.getChannels().get(8).getFunction());
        assertEquals(ChannelFunction.BLUE, profile.getChannels().get(9).getFunction());
        assertEquals(ChannelFunction.WHITE, profile.getChannels().get(10).getFunction());
        assertEquals(ChannelFunction.CONSTANT, profile.getChannels().get(11).getFunction());
        assertEquals(ChannelFunction.CONSTANT, profile.getChannels().get(12).getFunction());
    }

    @Test
    public void testParseRealFileIfAvailable() throws Exception {
        File realFile = new File("C:\\Users\\timo\\Documents\\QLC+ Saves\\qxf\\uking-zq-02042.qxf");
        if (realFile.exists()) {
            QlcFixtureDefinition def = QlcFixtureParser.parse(realFile);
            assertNotNull(def);
            assertEquals("UKing", def.getManufacturer());
            assertEquals("ZQ-02042", def.getModel());
            assertEquals(13, def.toFixtureProfile(null).getChannelCount());
        }
    }

    @Test
    public void testParseRobeTetra2Mode1() throws Exception {
        File robeFile = new File("C:\\Users\\timo\\Downloads\\ROBE-QLC5-Fixture-Files-TRP@MrDino-ariron\\ROBE-Tetra2@MrDino.qxf");
        if (robeFile.exists()) {
            QlcFixtureDefinition def = QlcFixtureParser.parse(robeFile);
            assertNotNull(def);
            assertEquals("ROBE", def.getManufacturer());
            assertEquals("Tetra2", def.getModel());
            assertEquals(0, def.getPanMax(), "Tetra2 hat kein Pan");

            assertEquals(6, def.getModes().size());
            assertEquals("Mode 1 - Wash (34 ch)", def.getModes().get(0).getName());
            assertEquals(34, def.getModes().get(0).getChannelCount(), "Mode 1 muss exakt 34 Kanäle haben");

            FixtureProfile prof = def.toFixtureProfile("Mode 1 - Wash (34 ch)");
            assertEquals(34, prof.getChannelCount());

            // Ch 0 (1): Tilt (128)
            assertEquals(ChannelFunction.TILT, prof.getChannels().get(0).getFunction());
            assertEquals(128, prof.getChannels().get(0).getDefaultValue());

            // Ch 1 (2): Tilt Fine (0)
            assertEquals(ChannelFunction.TILT_FINE, prof.getChannels().get(1).getFunction());

            // Ch 7, 8, 9 (8, 9, 10): Background RGB
            assertEquals(ChannelFunction.RED, prof.getChannels().get(7).getFunction());
            assertEquals(ChannelFunction.GREEN, prof.getChannels().get(8).getFunction());
            assertEquals(ChannelFunction.BLUE, prof.getChannels().get(9).getFunction());

            // Ch 10 (11): Background White (Def 0!)
            assertEquals(ChannelFunction.WHITE, prof.getChannels().get(10).getFunction());
            assertEquals(0, prof.getChannels().get(10).getDefaultValue(), "White default muss 0 sein, um RGB nicht auszuwaschen");

            // Ch 12 (13): Background Shutter (Def 32)
            assertEquals(ChannelFunction.STROBE, prof.getChannels().get(12).getFunction());
            assertEquals(32, prof.getChannels().get(12).getDefaultValue(), "Shutter muss 32 (Open) sein");

            // Ch 13 (14): Background Dimmer (Def 255)
            assertEquals(ChannelFunction.CONSTANT, prof.getChannels().get(13).getFunction());
            assertEquals(255, prof.getChannels().get(13).getDefaultValue());

            // Ch 14 (15): Colour Mix control (Def 45)
            assertEquals(ChannelFunction.CONSTANT, prof.getChannels().get(14).getFunction());
            assertEquals(45, prof.getChannels().get(14).getDefaultValue());

            // Ch 15 (16): Flower 1 rotation (Def 0, NOT RED!)
            assertNotEquals(ChannelFunction.RED, prof.getChannels().get(15).getFunction(), "Flower 1 rotation darf nicht Rot sein");
            assertEquals(ChannelFunction.CONSTANT, prof.getChannels().get(15).getFunction());

            // Ch 29 (30): Master Zoom (Def 128, NOT DIMMER!)
            assertNotEquals(ChannelFunction.DIMMER, prof.getChannels().get(29).getFunction(), "Zoom darf nicht Dimmer sein");
            assertEquals(ChannelFunction.FOCUS, prof.getChannels().get(29).getFunction());
            assertEquals(128, prof.getChannels().get(29).getDefaultValue());

            // Ch 32 (33): Master Shutter (Def 32)
            assertEquals(ChannelFunction.STROBE, prof.getChannels().get(32).getFunction());
            assertEquals(32, prof.getChannels().get(32).getDefaultValue());

            // Ch 33 (34): Master Dimmer
            assertEquals(ChannelFunction.DIMMER, prof.getChannels().get(33).getFunction());
        }
    }
}

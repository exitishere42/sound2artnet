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
        assertEquals(ChannelFunction.PAN_FINE, profile.getChannels().get(1).getFunction());
        assertEquals(ChannelFunction.TILT, profile.getChannels().get(2).getFunction());
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
}

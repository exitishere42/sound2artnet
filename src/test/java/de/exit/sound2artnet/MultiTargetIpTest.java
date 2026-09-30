package de.exit.sound2artnet;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.exit.sound2artnet.artnet.ArtNetSender;
import de.exit.sound2artnet.config.AppConfig;
import de.exit.sound2artnet.config.ArtNetPreset;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MultiTargetIpTest {

    @Test
    public void testParseTargetIps() {
        assertEquals(List.of("127.0.0.1"), AppConfig.parseTargetIps(null));
        assertEquals(List.of("127.0.0.1"), AppConfig.parseTargetIps(""));
        assertEquals(List.of("127.0.0.1"), AppConfig.parseTargetIps("   "));
        assertEquals(List.of("192.168.1.100"), AppConfig.parseTargetIps("192.168.1.100"));
        assertEquals(List.of("127.0.0.1", "192.168.1.50"), AppConfig.parseTargetIps("127.0.0.1, 192.168.1.50"));
        assertEquals(List.of("10.0.0.1", "10.0.0.2", "10.0.0.3"), AppConfig.parseTargetIps("10.0.0.1; 10.0.0.2  10.0.0.3"));
        assertEquals(List.of("127.0.0.1"), AppConfig.parseTargetIps("127.0.0.1, 127.0.0.1"));
    }

    @Test
    public void testArtNetSenderMultiTargetTransmission() throws Exception {
        ArtNetSender sender = new ArtNetSender();
        try {
            sender.start("127.0.0.1, 127.0.0.2", 0);
            assertTrue(sender.isRunning());
            assertEquals(2, sender.getTargetAddresses().size());

            assertEquals(0, sender.getTotalPacketsSent());
            sender.sendDmx(0, new byte[512]);
            // Bei 2 Ziel-IPs müssen 2 UDP-Pakete gesendet worden sein
            assertEquals(2, sender.getTotalPacketsSent());

            // Live-Aktualisierung auf 1 Ziel-IP
            sender.updateTarget("127.0.0.1", 0);
            assertEquals(1, sender.getTargetAddresses().size());

            sender.sendDmx(0, new byte[512]);
            // Jetzt genau 1 weiteres Paket -> Summe 3
            assertEquals(3, sender.getTotalPacketsSent());
        } finally {
            sender.stop();
        }
    }

    @Test
    public void testPresetMultiTargetIpSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ArtNetPreset preset = new ArtNetPreset();
        preset.setName("Dual-IP Venue");
        preset.setTargetIp("192.168.1.20, 192.168.1.21");

        assertEquals(List.of("192.168.1.20", "192.168.1.21"), preset.getTargetIps());

        String json = mapper.writeValueAsString(preset);
        ArtNetPreset read = mapper.readValue(json, ArtNetPreset.class);

        assertEquals("Dual-IP Venue", read.getName());
        assertEquals("192.168.1.20, 192.168.1.21", read.getTargetIp());
        assertEquals(List.of("192.168.1.20", "192.168.1.21"), read.getTargetIps());
    }
}

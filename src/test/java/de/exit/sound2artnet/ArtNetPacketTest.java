package de.exit.sound2artnet;

import de.exit.sound2artnet.artnet.ArtNetPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ArtNetPacketTest {

    @Test
    public void testBuildAndParseDmxPacket() {
        byte[] dmxData = new byte[512];
        dmxData[0] = (byte) 255; // Ch 1
        dmxData[1] = (byte) 128; // Ch 2
        dmxData[511] = (byte) 64; // Ch 512

        int sequence = 42;
        int physical = 0;
        int net = 0;
        int subnet = 0;
        int universe = 2;

        byte[] packetBytes = ArtNetPacket.buildDmxPacket(sequence, physical, net, subnet, universe, dmxData);
        assertNotNull(packetBytes);
        assertEquals(530, packetBytes.length); // 18 Bytes Header + 512 Bytes Data

        // Header prüfen
        assertEquals('A', packetBytes[0]);
        assertEquals('r', packetBytes[1]);
        assertEquals('t', packetBytes[2]);
        assertEquals('-', packetBytes[3]);
        assertEquals('N', packetBytes[4]);
        assertEquals('e', packetBytes[5]);
        assertEquals('t', packetBytes[6]);
        assertEquals(0, packetBytes[7]);

        // OpCode 0x5000 (Little Endian: 0x00, 0x50)
        assertEquals(0x00, packetBytes[8]);
        assertEquals(0x50, packetBytes[9]);

        // Protocol Version 14 (Big Endian: 0x00, 0x0E)
        assertEquals(0x00, packetBytes[10]);
        assertEquals(14, packetBytes[11]);

        // Sequence
        assertEquals(42, packetBytes[12] & 0xFF);

        // SubUni (Bits 0-3 = Universe 2)
        assertEquals(2, packetBytes[14] & 0x0F);

        // Paket parsen und verifizieren
        ArtNetPacket parsed = ArtNetPacket.parse(packetBytes, packetBytes.length);
        assertNotNull(parsed);
        assertEquals(ArtNetPacket.OP_DMX, parsed.opCode());
        assertEquals(42, parsed.sequence());
        assertEquals(2, parsed.universe());
        assertEquals(512, parsed.dmxData().length);
        assertEquals((byte) 255, parsed.dmxData()[0]);
        assertEquals((byte) 128, parsed.dmxData()[1]);
        assertEquals((byte) 64, parsed.dmxData()[511]);
    }
}

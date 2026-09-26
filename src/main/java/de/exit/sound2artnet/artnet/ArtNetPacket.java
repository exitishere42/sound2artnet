package de.exit.sound2artnet.artnet;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Art-Net 4 OpDmx Paket-Definition und Builder nach Artistic Licence Standard.
 */
public record ArtNetPacket(
    int opCode,
    int sequence,
    int physical,
    int net,
    int subnet,
    int universe,
    int portAddress,
    byte[] dmxData
) {
    public static final byte[] ARTNET_HEADER = "Art-Net\0".getBytes(StandardCharsets.US_ASCII);
    public static final int OP_POLL = 0x2000;
    public static final int OP_POLL_REPLY = 0x2100;
    public static final int OP_DMX = 0x5000;
    public static final int PROTOCOL_VERSION = 14;

    /**
     * Erstellt ein vollständiges Art-Net 4 OpDmx UDP-Paket (Header + DMX512 Daten).
     *
     * @param sequence   Laufende Sequenznummer (1-255, oder 0 falls deaktiviert)
     * @param physical   Physischer Port-Index (0-3)
     * @param net        Art-Net Net (0-127, Bits 8-14)
     * @param subnet     Art-Net Subnet (0-15, Bits 4-7)
     * @param universe   Art-Net Universe (0-15, Bits 0-3)
     * @param dmxData    DMX512 Kanalwerte (Array mit bis zu 512 Bytes)
     * @return 530-Byte Rohdaten für das UDP-Datagramm
     */
    public static byte[] buildDmxPacket(int sequence, int physical, int net, int subnet, int universe, byte[] dmxData) {
        int length = (dmxData != null) ? Math.min(dmxData.length, 512) : 512;
        // DMX Datenlänge muss immer eine gerade Zahl sein (min. 2, max. 512)
        if (length % 2 != 0) {
            length++;
        }
        int packetSize = 18 + length;
        byte[] packet = new byte[packetSize];

        // 0-7: "Art-Net\0"
        System.arraycopy(ARTNET_HEADER, 0, packet, 0, 8);

        // 8-9: OpCode (Little Endian: 0x5000 -> 0x00, 0x50)
        packet[8] = 0x00;
        packet[9] = 0x50;

        // 10-11: Protocol Version 14 (Big Endian: 0x00, 0x0E)
        packet[10] = 0x00;
        packet[11] = (byte) PROTOCOL_VERSION;

        // 12: Sequence (0 = no sequence checking)
        packet[12] = (byte) (sequence & 0xFF);

        // 13: Physical Port
        packet[13] = (byte) (physical & 0xFF);

        // 14: SubUni (Bits 0-3: Universe, Bits 4-7: Subnet)
        packet[14] = (byte) (((subnet & 0x0F) << 4) | (universe & 0x0F));

        // 15: Net (Bits 0-6)
        packet[15] = (byte) (net & 0x7F);

        // 16-17: Length of DMX data (Big Endian: MSB, LSB)
        packet[16] = (byte) ((length >> 8) & 0xFF);
        packet[17] = (byte) (length & 0xFF);

        // 18+: DMX512 Kanaldaten
        if (dmxData != null) {
            System.arraycopy(dmxData, 0, packet, 18, Math.min(dmxData.length, length));
        }

        return packet;
    }

    /**
     * Parst ein empfangenes Art-Net UDP-Paket (für Tests und Verifikation).
     */
    public static ArtNetPacket parse(byte[] buffer, int length) {
        if (length < 18) {
            return null;
        }

        for (int i = 0; i < 8; i++) {
            if (buffer[i] != ARTNET_HEADER[i]) {
                return null;
            }
        }

        int opCode = (buffer[8] & 0xFF) | ((buffer[9] & 0xFF) << 8);
        if (opCode != OP_DMX) {
            return null;
        }

        int sequence = buffer[12] & 0xFF;
        int physical = buffer[13] & 0xFF;

        int subUni = buffer[14] & 0xFF;
        int universe = subUni & 0x0F;
        int subnet = (subUni >> 4) & 0x0F;
        int net = buffer[15] & 0x7F;

        int portAddress = (net << 8) | (subnet << 4) | universe;
        int dmxLength = ((buffer[16] & 0xFF) << 8) | (buffer[17] & 0xFF);
        int availableData = length - 18;
        int actualLength = Math.min(dmxLength, Math.min(availableData, 512));

        if (actualLength <= 0) {
            return null;
        }

        byte[] dmxData = Arrays.copyOfRange(buffer, 18, 18 + actualLength);
        return new ArtNetPacket(OP_DMX, sequence, physical, net, subnet, universe, portAddress, dmxData);
    }
}

package de.exit.sound2artnet.artnet;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sendet DMX512-Frames als Art-Net 4 OpDmx UDP-Pakete an das konfigurierte Ziel.
 */
public class ArtNetSender {
    private static final Logger LOGGER = Logger.getLogger(ArtNetSender.class.getName());
    public static final int DEFAULT_PORT = 6454;

    private DatagramSocket socket;
    private InetAddress targetAddress;
    private int targetPort = DEFAULT_PORT;
    private int universe = 0;
    private int subnet = 0;
    private int net = 0;
    private int sequenceCounter = 1;
    private final int[] sequenceCounters = new int[16];

    private final AtomicLong totalPacketsSent = new AtomicLong(0);
    private long lastMetricTime = System.currentTimeMillis();
    private long packetsInCurrentWindow = 0;
    private volatile double currentPps = 0.0;
    private volatile boolean isRunning = false;

    public ArtNetSender() {
        try {
            this.targetAddress = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException ignored) {}
    }

    public synchronized void start(String ip, int universe) throws IOException {
        stop();
        this.universe = Math.max(0, Math.min(15, universe));
        this.targetAddress = InetAddress.getByName(ip != null && !ip.isBlank() ? ip.trim() : "127.0.0.1");

        this.socket = new DatagramSocket();
        this.socket.setBroadcast(true);
        this.isRunning = true;
        this.sequenceCounter = 1;
        java.util.Arrays.fill(this.sequenceCounters, 0);
        this.totalPacketsSent.set(0);
        this.packetsInCurrentWindow = 0;
        this.lastMetricTime = System.currentTimeMillis();
        LOGGER.info("ArtNetSender gestartet -> Ziel: " + targetAddress.getHostAddress() + ":" + targetPort + " | Universum: " + this.universe);
    }

    public synchronized void stop() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }
        currentPps = 0.0;
        LOGGER.info("ArtNetSender gestoppt.");
    }

    public synchronized boolean isRunning() {
        return isRunning && socket != null && !socket.isClosed();
    }

    /**
     * Sendet einen 512-Byte DMX-Frame für ein beliebiges Universum (0-15).
     */
    public synchronized void sendDmx(int universe, byte[] dmxData) {
        if (!isRunning || socket == null || socket.isClosed()) {
            return;
        }

        int u = Math.max(0, Math.min(15, universe));
        int seq = sequenceCounters[u] = (sequenceCounters[u] % 255) + 1;
        byte[] packetBytes = ArtNetPacket.buildDmxPacket(seq, 0, net, subnet, u, dmxData);

        try {
            DatagramPacket datagram = new DatagramPacket(packetBytes, packetBytes.length, targetAddress, targetPort);
            socket.send(datagram);
            totalPacketsSent.incrementAndGet();
            packetsInCurrentWindow++;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Fehler beim Senden des Art-Net Pakets für Universum " + u + ": " + e.getMessage(), e);
        }

        updateMetrics();
    }

    /**
     * Sendet einen 512-Byte DMX-Frame an das Standard-Universum.
     */
    public synchronized void sendDmx(byte[] dmxData) {
        sendDmx(this.universe, dmxData);
    }

    private void updateMetrics() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastMetricTime;
        if (elapsed >= 1000) {
            currentPps = (packetsInCurrentWindow * 1000.0) / elapsed;
            packetsInCurrentWindow = 0;
            lastMetricTime = now;
        }
    }

    public double getPacketsPerSecond() {
        long now = System.currentTimeMillis();
        if (now - lastMetricTime > 2000) {
            currentPps = 0.0;
        }
        return currentPps;
    }

    public long getTotalPacketsSent() {
        return totalPacketsSent.get();
    }

    public InetAddress getTargetAddress() {
        return targetAddress;
    }

    public int getUniverse() {
        return universe;
    }

    public void setUniverse(int universe) {
        this.universe = Math.max(0, Math.min(15, universe));
    }

    /**
     * Aktualisiert Zieladresse und Universum zur Laufzeit (z. B. beim Umschalten von Presets).
     */
    public synchronized void updateTarget(String ip, int universe) throws UnknownHostException {
        this.universe = Math.max(0, Math.min(15, universe));
        if (ip != null && !ip.isBlank()) {
            this.targetAddress = InetAddress.getByName(ip.trim());
        }
        LOGGER.info("ArtNetSender Ziel aktualisiert -> " + (targetAddress != null ? targetAddress.getHostAddress() : "null") + " | Universum: " + this.universe);
    }
}

package de.exit.sound2artnet.artnet;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Sendet DMX512-Frames als Art-Net 4 OpDmx UDP-Pakete an das oder die konfigurierten Ziele.
 */
public class ArtNetSender {
    private static final Logger LOGGER = Logger.getLogger(ArtNetSender.class.getName());
    public static final int DEFAULT_PORT = 6454;

    private DatagramSocket socket;
    private InetAddress targetAddress;
    private final List<InetAddress> targetAddresses = new CopyOnWriteArrayList<>();
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
            this.targetAddresses.add(this.targetAddress);
        } catch (UnknownHostException ignored) {}
    }

    public synchronized void start(String ip, int universe) throws IOException {
        stop();
        this.universe = Math.max(0, Math.min(15, universe));
        setTargetAddresses(ip);

        this.socket = new DatagramSocket();
        this.socket.setBroadcast(true);
        this.isRunning = true;
        this.sequenceCounter = 1;
        java.util.Arrays.fill(this.sequenceCounters, 0);
        this.totalPacketsSent.set(0);
        this.packetsInCurrentWindow = 0;
        this.lastMetricTime = System.currentTimeMillis();
        LOGGER.info("ArtNetSender gestartet -> Ziele: " + formatTargetAddresses() + ":" + targetPort + " | Universum: " + this.universe);
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
     * Sendet einen 512-Byte DMX-Frame für ein beliebiges Universum (0-15) an alle Ziel-IPs.
     */
    public synchronized void sendDmx(int universe, byte[] dmxData) {
        if (!isRunning || socket == null || socket.isClosed()) {
            return;
        }

        int u = Math.max(0, Math.min(15, universe));
        int seq = sequenceCounters[u] = (sequenceCounters[u] % 255) + 1;
        byte[] packetBytes = ArtNetPacket.buildDmxPacket(seq, 0, net, subnet, u, dmxData);

        List<InetAddress> targets = this.targetAddresses;
        if (targets == null || targets.isEmpty()) {
            if (this.targetAddress != null) {
                targets = List.of(this.targetAddress);
            } else {
                return;
            }
        }

        for (InetAddress addr : targets) {
            try {
                DatagramPacket datagram = new DatagramPacket(packetBytes, packetBytes.length, addr, targetPort);
                socket.send(datagram);
                totalPacketsSent.incrementAndGet();
                packetsInCurrentWindow++;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Fehler beim Senden des Art-Net Pakets an " + addr.getHostAddress() + " für Universum " + u + ": " + e.getMessage(), e);
            }
        }

        updateMetrics();
    }

    /**
     * Sendet einen 512-Byte DMX-Frame an das Standard-Universum an alle Ziel-IPs.
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
        return (targetAddresses != null && !targetAddresses.isEmpty()) ? targetAddresses.get(0) : targetAddress;
    }

    public List<InetAddress> getTargetAddresses() {
        return Collections.unmodifiableList(targetAddresses);
    }

    public String formatTargetAddresses() {
        if (targetAddresses == null || targetAddresses.isEmpty()) {
            return targetAddress != null ? targetAddress.getHostAddress() : "127.0.0.1";
        }
        return targetAddresses.stream().map(InetAddress::getHostAddress).collect(Collectors.joining(", "));
    }

    public int getUniverse() {
        return universe;
    }

    public void setUniverse(int universe) {
        this.universe = Math.max(0, Math.min(15, universe));
    }

    /**
     * Löst eine durch Komma, Semikolon oder Leerzeichen getrennte Liste von Ziel-IPs auf.
     */
    public synchronized void setTargetAddresses(String ipString) throws UnknownHostException {
        if (ipString == null || ipString.isBlank()) {
            this.targetAddresses.clear();
            this.targetAddresses.add(InetAddress.getByName("127.0.0.1"));
            this.targetAddress = this.targetAddresses.get(0);
            return;
        }

        List<InetAddress> resolved = new ArrayList<>();
        String[] tokens = ipString.split("[,;\\s]+");
        UnknownHostException lastException = null;

        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                InetAddress addr = InetAddress.getByName(trimmed);
                if (!resolved.contains(addr)) {
                    resolved.add(addr);
                }
            } catch (UnknownHostException e) {
                LOGGER.log(Level.FINE, "Konnte Ziel-IP nicht auflösen: {0}", trimmed);
                lastException = e;
            }
        }

        if (resolved.isEmpty()) {
            if (lastException != null && this.targetAddresses.isEmpty()) {
                throw lastException;
            }
            if (this.targetAddresses.isEmpty()) {
                resolved.add(InetAddress.getByName("127.0.0.1"));
            } else {
                return;
            }
        }

        this.targetAddresses.clear();
        this.targetAddresses.addAll(resolved);
        this.targetAddress = this.targetAddresses.get(0);
    }

    /**
     * Aktualisiert Zieladresse(n) und Universum zur Laufzeit (z. B. beim Umschalten von Presets).
     */
    public synchronized void updateTarget(String ip, int universe) throws UnknownHostException {
        this.universe = Math.max(0, Math.min(15, universe));
        setTargetAddresses(ip);
        LOGGER.info("ArtNetSender Ziel aktualisiert -> " + formatTargetAddresses() + " | Universum: " + this.universe);
    }
}

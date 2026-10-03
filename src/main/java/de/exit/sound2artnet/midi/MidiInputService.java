package de.exit.sound2artnet.midi;

import de.exit.sound2artnet.util.I18n;

import javax.sound.midi.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verwaltet MIDI-Eingangsgeräte (USB-MIDI-Keyboards, Pad-Controller, DJ-Controller)
 * plattformübergreifend unter Windows (WinMM / javax.sound.midi) und Linux
 * (ALSA Sequencer / PipeWire via aseqdump, ALSA RawMIDI via amidi sowie javax.sound.midi).
 */
public class MidiInputService {
    private static final Logger LOGGER = Logger.getLogger(MidiInputService.class.getName());
    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    private static final Pattern ASEQDUMP_LIST_PATTERN =
            Pattern.compile("^\\s*(\\d+:\\d+)\\s+(.+?)\\s{2,}(.+)$");
    private static final Pattern AMIDI_LIST_PATTERN =
            Pattern.compile("^\\s*I[O ]\\s+(hw:\\S+)\\s+(.+)$");
    private static final Pattern ASEQDUMP_NOTE_ON_PATTERN =
            Pattern.compile("Note on\\s+(\\d+),\\s*note\\s+(\\d+),\\s*velocity\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASEQDUMP_NOTE_OFF_PATTERN =
            Pattern.compile("Note off\\s+(\\d+),\\s*note\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASEQDUMP_CC_PATTERN =
            Pattern.compile("Control change\\s+(\\d+),\\s*controller\\s+(\\d+),\\s*value\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    public record MidiEventInfo(
            String type,       // "NOTE" oder "CC"
            int channel,       // 0..15
            int data1,         // Note (0..127) oder CC-Nummer (0..127)
            int data2,         // Velocity / Wert (0..127)
            boolean triggered  // Ob dieser Event den Beat ausgelöst hat
    ) {
        public String formatDisplay() {
            if ("NOTE".equals(type)) {
                return String.format("Note %d (%s)  |  Vel %d  |  Ch %d",
                        data1, formatNoteName(data1), data2, channel + 1);
            } else {
                return String.format("CC %d  |  Val %d  |  Ch %d",
                        data1, data2, channel + 1);
            }
        }
    }

    private MidiDevice activeDevice;
    private Transmitter activeTransmitter;
    private Process activeLinuxProcess;
    private Thread activeLinuxReaderThread;
    private volatile boolean linuxCaptureRunning = false;
    private String activeDeviceName = "";

    // Filter / Binding für Beat (-1 = Alle Kanäle / Alle Tasten)
    private volatile String boundType = "ANY"; // "ANY", "NOTE", "CC"
    private volatile int boundChannel = -1;    // -1 oder 0..15
    private volatile int boundData1 = -1;      // -1 oder 0..127

    // Filter / Binding für Blackout (-1 / "NONE" = Nicht zugewiesen)
    private volatile String blackoutBoundType = "NONE"; // "NONE", "NOTE", "CC"
    private volatile int blackoutBoundChannel = -1;     // -1 oder 0..15
    private volatile int blackoutBoundData1 = -1;       // -1 oder 0..127

    // Filter / Binding für Strobo (-1 / "NONE" = Nicht zugewiesen)
    private volatile String strobeBoundType = "NONE"; // "NONE", "NOTE", "CC"
    private volatile int strobeBoundChannel = -1;     // -1 oder 0..15
    private volatile int strobeBoundData1 = -1;       // -1 oder 0..127

    // Filter / Binding für Master Dimmer Knob / Slider (-1 / "NONE" = Nicht zugewiesen, normalerweise "CC")
    private volatile String masterDimmerBoundType = "NONE"; // "NONE", "CC", "NOTE"
    private volatile int masterDimmerBoundChannel = -1;     // -1 oder 0..15
    private volatile int masterDimmerBoundData1 = -1;       // -1 oder 0..127 (CC Number)

    private volatile boolean learning = false;
    private volatile boolean learningBlackout = false;
    private volatile boolean learningStrobe = false;
    private volatile boolean learningMasterDimmer = false;
    private final boolean[] ccHighState = new boolean[128];
    private final boolean[] heldNotes = new boolean[16 * 128];
    private final boolean[] heldCcs = new boolean[16 * 128];
    private volatile boolean beatHeld = false;
    private volatile boolean blackoutHeld = false;
    private volatile boolean strobeHeld = false;

    private Runnable onBeatTrigger;
    private Consumer<Boolean> onBeatHoldChange;
    private Runnable onBlackoutTrigger;
    private Consumer<Boolean> onBlackoutHoldChange;
    private Runnable onStrobeTrigger;
    private Consumer<Boolean> onStrobeHoldChange;
    private Consumer<Double> onMasterDimmerChange;
    private Consumer<MidiEventInfo> onMidiEvent;
    private Runnable onLearnComplete;
    private Runnable onBlackoutLearnComplete;
    private Runnable onStrobeLearnComplete;
    private Runnable onMasterDimmerLearnComplete;

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    /**
     * Listet alle verfügbaren MIDI-Eingangsgeräte unter Windows und Linux auf.
     */
    public static List<MidiDeviceInfo> listInputDevices() {
        List<MidiDeviceInfo> result = new ArrayList<>();

        // 1. Linux: ALSA Sequencer / PipeWire Ports über `aseqdump -l` & RawMIDI über `amidi -l`
        if (isLinux()) {
            scanLinuxAlsaDevices(result);
        }

        // 2. Plattformübergreifend: Java Sound MIDI (Windows WinMM & Linux ALSA RawMIDI)
        try {
            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
            for (MidiDevice.Info info : infos) {
                try {
                    MidiDevice dev = MidiSystem.getMidiDevice(info);
                    if (dev.getMaxTransmitters() != 0 && !(dev instanceof Sequencer) && !(dev instanceof Synthesizer)) {
                        String rawName = info.getName() != null ? info.getName().trim() : "MIDI Input";
                        String desc = info.getDescription() != null ? info.getDescription().trim() : "";
                        String vendor = info.getVendor() != null ? info.getVendor().trim() : "";
                        if (rawName.equalsIgnoreCase("Real Time Sequencer") || rawName.equalsIgnoreCase("Gervill")) {
                            continue;
                        }

                        String cleanName = cleanJavaMidiName(rawName, desc);
                        String hwPort = extractHwPort(rawName, desc);

                        // Unter Linux prüfen, ob dieses Gerät bereits über ALSA Sequencer / amidi erkannt wurde
                        boolean merged = false;
                        if (isLinux()) {
                            for (int i = 0; i < result.size(); i++) {
                                MidiDeviceInfo existing = result.get(i);
                                if (matchesLinuxDevice(existing, cleanName, rawName, desc, hwPort)) {
                                    result.set(i, new MidiDeviceInfo(
                                            existing.name(),
                                            existing.vendor().isEmpty() ? vendor : existing.vendor(),
                                            existing.description().isEmpty() ? desc : existing.description(),
                                            info,
                                            existing.alsaSeqPort(),
                                            existing.alsaRawPort() != null ? existing.alsaRawPort() : hwPort
                                    ));
                                    merged = true;
                                    break;
                                }
                            }
                        }

                        if (!merged) {
                            result.add(new MidiDeviceInfo(cleanName, vendor, desc, info, null, hwPort));
                        }
                    }
                } catch (MidiUnavailableException ignored) {
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Fehler beim Scannen der Java-MIDI-Geräte: " + t.getMessage(), t);
        }
        return result;
    }

    private static void scanLinuxAlsaDevices(List<MidiDeviceInfo> result) {
        // A. ALSA Sequencer (`aseqdump -l`) - funktioniert auf Debian/GNOME/PipeWire ohne EBUSY-Blockade
        try {
            ProcessBuilder pb = new ProcessBuilder("aseqdump", "-l");
            pb.environment().put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = ASEQDUMP_LIST_PATTERN.matcher(line);
                    if (m.matches()) {
                        String port = m.group(1).trim();
                        String clientName = m.group(2).trim();
                        String portName = m.group(3).trim();
                        if (isIgnoredLinuxMidiPort(port, clientName, portName)) {
                            continue;
                        }
                        String displayName = buildCleanLinuxMidiName(clientName, portName);
                        result.add(new MidiDeviceInfo(displayName, clientName, portName, null, port, null));
                    }
                }
            }
            proc.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // aseqdump evtl. nicht installiert -> Weiter mit amidi / javax.sound.midi
        }

        // B. ALSA RawMIDI (`amidi -l`)
        try {
            ProcessBuilder pb = new ProcessBuilder("amidi", "-l");
            pb.environment().put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = AMIDI_LIST_PATTERN.matcher(line);
                    if (m.matches()) {
                        String hwPort = m.group(1).trim();
                        String name = m.group(2).trim();
                        boolean attached = false;
                        for (int i = 0; i < result.size(); i++) {
                            MidiDeviceInfo existing = result.get(i);
                            if (existing.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))
                                    || name.toLowerCase(Locale.ROOT).contains(existing.vendor().toLowerCase(Locale.ROOT))) {
                                result.set(i, new MidiDeviceInfo(
                                        existing.name(),
                                        existing.vendor(),
                                        existing.description(),
                                        existing.midiInfo(),
                                        existing.alsaSeqPort(),
                                        hwPort
                                ));
                                attached = true;
                                break;
                            }
                        }
                        if (!attached) {
                            result.add(new MidiDeviceInfo(name, "", hwPort, null, null, hwPort));
                        }
                    }
                }
            }
            proc.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static boolean isIgnoredLinuxMidiPort(String port, String clientName, String portName) {
        if (port.startsWith("0:")) {
            return true; // System Timer / System Announce
        }
        String lowerClient = clientName.toLowerCase(Locale.ROOT);
        String lowerPort = portName.toLowerCase(Locale.ROOT);
        if (lowerClient.equals("system") || lowerClient.contains("midi through")) {
            return true;
        }
        if (lowerClient.contains("pipewire-system") || lowerClient.contains("pipewire-rt-event")) {
            return true;
        }
        return lowerClient.contains("aseqdump") || lowerPort.contains("aseqdump");
    }

    private static String buildCleanLinuxMidiName(String clientName, String portName) {
        if (portName.isEmpty() || portName.equalsIgnoreCase(clientName)) {
            return clientName;
        }
        if (portName.toLowerCase(Locale.ROOT).startsWith(clientName.toLowerCase(Locale.ROOT))) {
            return portName;
        }
        return clientName + " - " + portName;
    }

    private static String cleanJavaMidiName(String rawName, String description) {
        // Unter Linux liefert OpenJDK oft "MPK2 [hw:2,0,0]" mit description "MPK2, USB Audio, AKAI MPK mini"
        if (rawName.contains("[hw:")) {
            String stripped = rawName.replaceAll("\\s*\\[hw:[^\\]]+\\]", "").trim();
            if (description != null && !description.isBlank()) {
                String[] parts = description.split(",");
                if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                    return parts[2].trim();
                }
            }
            if (!stripped.isEmpty()) {
                return stripped;
            }
        }
        return rawName;
    }

    private static String extractHwPort(String rawName, String description) {
        Matcher m = Pattern.compile("(hw:\\d+,\\d+(?:,\\d+)?)").matcher(rawName + " " + description);
        return m.find() ? m.group(1) : null;
    }

    private static boolean matchesLinuxDevice(MidiDeviceInfo existing, String cleanName, String rawName, String desc, String hwPort) {
        if (hwPort != null && hwPort.equalsIgnoreCase(existing.alsaRawPort())) {
            return true;
        }
        String exLower = existing.name().toLowerCase(Locale.ROOT);
        String cleanLower = cleanName.toLowerCase(Locale.ROOT);
        if (exLower.equals(cleanLower) || exLower.contains(cleanLower) || cleanLower.contains(exLower)) {
            return true;
        }
        String strippedRaw = rawName.replaceAll("\\s*\\[hw:[^\\]]+\\]", "").trim().toLowerCase(Locale.ROOT);
        return !strippedRaw.isEmpty() && (exLower.contains(strippedRaw) || desc.toLowerCase(Locale.ROOT).contains(exLower));
    }

    public synchronized boolean openDevice(MidiDeviceInfo deviceInfo) {
        closeDevice();
        if (deviceInfo == null) {
            return false;
        }

        // 1. Unter Linux: Bevorzuge ALSA Sequencer (`aseqdump -p <port>`), da PipeWire / snd_seq_midi
        //    RawMIDI-Geräte unter Debian/GNOME oft exklusiv belegt und aseqdump konfliktfrei läuft.
        if (isLinux() && deviceInfo.alsaSeqPort() != null && !deviceInfo.alsaSeqPort().isBlank()) {
            if (openLinuxAseqdump(deviceInfo.alsaSeqPort(), deviceInfo.name())) {
                return true;
            }
        }

        // 2. Standard Java Sound MIDI (Primär unter Windows sowie unter Linux, falls verfügbar)
        if (deviceInfo.midiInfo() != null) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(deviceInfo.midiInfo());
                if (!dev.isOpen()) {
                    dev.open();
                }
                Transmitter transmitter = dev.getTransmitter();
                transmitter.setReceiver(new MidiInputReceiver());

                this.activeDevice = dev;
                this.activeTransmitter = transmitter;
                this.activeDeviceName = deviceInfo.name();
                LOGGER.info("MIDI-Eingangsgerät (Java MIDI) geöffnet: " + activeDeviceName);
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Java-MIDI-Gerät konnte nicht geöffnet werden (" + deviceInfo.name() + "): " + e.getMessage());
                closeDevice();
            }
        }

        // 3. Linux Fallback: ALSA RawMIDI über `amidi -p <hwPort> -d`
        if (isLinux() && deviceInfo.alsaRawPort() != null && !deviceInfo.alsaRawPort().isBlank()) {
            if (openLinuxAmidi(deviceInfo.alsaRawPort(), deviceInfo.name())) {
                return true;
            }
        }

        return false;
    }

    private boolean openLinuxAseqdump(String seqPort, String displayName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("aseqdump", "-p", seqPort);
            pb.environment().put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            // Kurz prüfen, ob der Prozess sofort mit einem Fehler beendet wurde
            if (!proc.isAlive() && proc.exitValue() != 0) {
                return false;
            }

            this.activeLinuxProcess = proc;
            this.linuxCaptureRunning = true;
            this.activeDeviceName = displayName;

            this.activeLinuxReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (linuxCaptureRunning && (line = reader.readLine()) != null) {
                        parseAseqdumpLine(line);
                    }
                } catch (Exception e) {
                    if (linuxCaptureRunning) {
                        LOGGER.log(Level.FINE, "Linux aseqdump Stream beendet: " + e.getMessage());
                    }
                }
            }, "Linux-MIDI-Aseqdump-" + seqPort);
            this.activeLinuxReaderThread.setDaemon(true);
            this.activeLinuxReaderThread.start();

            LOGGER.info("MIDI-Eingangsgerät (Linux ALSA Sequencer " + seqPort + ") geöffnet: " + displayName);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte aseqdump für Port " + seqPort + " nicht starten: " + e.getMessage());
            closeDevice();
            return false;
        }
    }

    private boolean openLinuxAmidi(String rawPort, String displayName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("amidi", "-p", rawPort, "-d");
            pb.environment().put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            if (!proc.isAlive() && proc.exitValue() != 0) {
                return false;
            }

            this.activeLinuxProcess = proc;
            this.linuxCaptureRunning = true;
            this.activeDeviceName = displayName;

            this.activeLinuxReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (linuxCaptureRunning && (line = reader.readLine()) != null) {
                        parseAmidiHexLine(line);
                    }
                } catch (Exception e) {
                    if (linuxCaptureRunning) {
                        LOGGER.log(Level.FINE, "Linux amidi Stream beendet: " + e.getMessage());
                    }
                }
            }, "Linux-MIDI-Amidi-" + rawPort);
            this.activeLinuxReaderThread.setDaemon(true);
            this.activeLinuxReaderThread.start();

            LOGGER.info("MIDI-Eingangsgerät (Linux ALSA RawMIDI " + rawPort + ") geöffnet: " + displayName);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte amidi für Port " + rawPort + " nicht starten: " + e.getMessage());
            closeDevice();
            return false;
        }
    }

    /**
     * Parst eine Ausgabezeile von `aseqdump -p <port>` unter Linux.
     */
    public void parseAseqdumpLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        Matcher mNoteOn = ASEQDUMP_NOTE_ON_PATTERN.matcher(line);
        if (mNoteOn.find()) {
            int ch = clampMidiChannel(Integer.parseInt(mNoteOn.group(1)));
            int note = clampMidiData(Integer.parseInt(mNoteOn.group(2)));
            int vel = clampMidiData(Integer.parseInt(mNoteOn.group(3)));
            handleShortMessage(ShortMessage.NOTE_ON, ch, note, vel);
            return;
        }
        Matcher mNoteOff = ASEQDUMP_NOTE_OFF_PATTERN.matcher(line);
        if (mNoteOff.find()) {
            int ch = clampMidiChannel(Integer.parseInt(mNoteOff.group(1)));
            int note = clampMidiData(Integer.parseInt(mNoteOff.group(2)));
            handleShortMessage(ShortMessage.NOTE_OFF, ch, note, 0);
            return;
        }
        Matcher mCc = ASEQDUMP_CC_PATTERN.matcher(line);
        if (mCc.find()) {
            int ch = clampMidiChannel(Integer.parseInt(mCc.group(1)));
            int cc = clampMidiData(Integer.parseInt(mCc.group(2)));
            int val = clampMidiData(Integer.parseInt(mCc.group(3)));
            handleShortMessage(ShortMessage.CONTROL_CHANGE, ch, cc, val);
        }
    }

    /**
     * Parst eine Hex-Zeile von `amidi -p <hw> -d` unter Linux (z. B. "90 3C 64").
     */
    public void parseAmidiHexLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length >= 3) {
            try {
                int status = Integer.parseInt(tokens[0], 16) & 0xFF;
                int data1 = clampMidiData(Integer.parseInt(tokens[1], 16));
                int data2 = clampMidiData(Integer.parseInt(tokens[2], 16));
                int command = status & 0xF0;
                int channel = status & 0x0F;
                if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF || command == ShortMessage.CONTROL_CHANGE) {
                    handleShortMessage(command, channel, data1, data2);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static int clampMidiChannel(int ch) {
        return Math.max(0, Math.min(15, ch));
    }

    private static int clampMidiData(int d) {
        return Math.max(0, Math.min(127, d));
    }

    public synchronized void closeDevice() {
        linuxCaptureRunning = false;
        if (activeLinuxProcess != null) {
            try {
                activeLinuxProcess.destroy();
                if (!activeLinuxProcess.waitFor(200, TimeUnit.MILLISECONDS)) {
                    activeLinuxProcess.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
            activeLinuxProcess = null;
        }
        if (activeLinuxReaderThread != null) {
            activeLinuxReaderThread.interrupt();
            activeLinuxReaderThread = null;
        }
        if (activeTransmitter != null) {
            try {
                activeTransmitter.close();
            } catch (Exception ignored) {
            }
            activeTransmitter = null;
        }
        if (activeDevice != null) {
            try {
                if (activeDevice.isOpen()) {
                    activeDevice.close();
                }
            } catch (Exception ignored) {
            }
            activeDevice = null;
        }
        activeDeviceName = "";
        resetHeldState();
    }

    public synchronized boolean isOpen() {
        if (activeDevice != null && activeDevice.isOpen()) {
            return true;
        }
        return linuxCaptureRunning && activeLinuxProcess != null && activeLinuxProcess.isAlive();
    }

    public String getActiveDeviceName() {
        return activeDeviceName;
    }

    public void setBinding(String type, int channel, int data1) {
        this.boundType = (type != null && !type.isBlank()) ? type : "ANY";
        this.boundChannel = channel;
        this.boundData1 = data1;
        updateBeatHeldState();
    }

    public void clearBinding() {
        this.boundType = "ANY";
        this.boundChannel = -1;
        this.boundData1 = -1;
        this.learning = false;
        resetHeldState();
    }

    public String getBoundType() {
        return boundType;
    }

    public int getBoundChannel() {
        return boundChannel;
    }

    public int getBoundData1() {
        return boundData1;
    }

    public void setBlackoutBinding(String type, int channel, int data1) {
        this.blackoutBoundType = (type != null && !type.isBlank()) ? type : "NONE";
        this.blackoutBoundChannel = channel;
        this.blackoutBoundData1 = data1;
        updateBeatHeldState();
    }

    public void clearBlackoutBinding() {
        this.blackoutBoundType = "NONE";
        this.blackoutBoundChannel = -1;
        this.blackoutBoundData1 = -1;
        this.learningBlackout = false;
        updateBeatHeldState();
    }

    public String getBlackoutBoundType() {
        return blackoutBoundType;
    }

    public int getBlackoutBoundChannel() {
        return blackoutBoundChannel;
    }

    public int getBlackoutBoundData1() {
        return blackoutBoundData1;
    }

    public void setStrobeBinding(String type, int channel, int data1) {
        this.strobeBoundType = (type != null && !type.isBlank()) ? type : "NONE";
        this.strobeBoundChannel = channel;
        this.strobeBoundData1 = data1;
        updateBeatHeldState();
    }

    public void clearStrobeBinding() {
        this.strobeBoundType = "NONE";
        this.strobeBoundChannel = -1;
        this.strobeBoundData1 = -1;
        this.learningStrobe = false;
        updateBeatHeldState();
    }

    public String getStrobeBoundType() {
        return strobeBoundType;
    }

    public int getStrobeBoundChannel() {
        return strobeBoundChannel;
    }

    public int getStrobeBoundData1() {
        return strobeBoundData1;
    }

    public void setMasterDimmerBinding(String type, int channel, int data1) {
        this.masterDimmerBoundType = (type != null && !type.isBlank()) ? type : "NONE";
        this.masterDimmerBoundChannel = channel;
        this.masterDimmerBoundData1 = data1;
    }

    public void clearMasterDimmerBinding() {
        this.masterDimmerBoundType = "NONE";
        this.masterDimmerBoundChannel = -1;
        this.masterDimmerBoundData1 = -1;
        this.learningMasterDimmer = false;
    }

    public String getMasterDimmerBoundType() {
        return masterDimmerBoundType;
    }

    public int getMasterDimmerBoundChannel() {
        return masterDimmerBoundChannel;
    }

    public int getMasterDimmerBoundData1() {
        return masterDimmerBoundData1;
    }

    public boolean isLearning() {
        return learning;
    }

    public void setLearning(boolean learning) {
        this.learning = learning;
        if (learning) {
            this.learningBlackout = false;
            this.learningStrobe = false;
            this.learningMasterDimmer = false;
        }
    }

    public boolean isLearningBlackout() {
        return learningBlackout;
    }

    public void setLearningBlackout(boolean learningBlackout) {
        this.learningBlackout = learningBlackout;
        if (learningBlackout) {
            this.learning = false;
            this.learningStrobe = false;
            this.learningMasterDimmer = false;
        }
    }

    public boolean isLearningStrobe() {
        return learningStrobe;
    }

    public void setLearningStrobe(boolean learningStrobe) {
        this.learningStrobe = learningStrobe;
        if (learningStrobe) {
            this.learning = false;
            this.learningBlackout = false;
            this.learningMasterDimmer = false;
        }
    }

    public boolean isLearningMasterDimmer() {
        return learningMasterDimmer;
    }

    public void setLearningMasterDimmer(boolean learningMasterDimmer) {
        this.learningMasterDimmer = learningMasterDimmer;
        if (learningMasterDimmer) {
            this.learning = false;
            this.learningBlackout = false;
            this.learningStrobe = false;
        }
    }

    public boolean isBeatHeld() {
        return beatHeld;
    }

    public boolean isBlackoutHeld() {
        return blackoutHeld;
    }

    public boolean isStrobeHeld() {
        return strobeHeld;
    }

    public void setOnBeatTrigger(Runnable onBeatTrigger) {
        this.onBeatTrigger = onBeatTrigger;
    }

    public void setOnBeatHoldChange(Consumer<Boolean> onBeatHoldChange) {
        this.onBeatHoldChange = onBeatHoldChange;
    }

    public void setOnBlackoutTrigger(Runnable onBlackoutTrigger) {
        this.onBlackoutTrigger = onBlackoutTrigger;
    }

    public void setOnBlackoutHoldChange(Consumer<Boolean> onBlackoutHoldChange) {
        this.onBlackoutHoldChange = onBlackoutHoldChange;
    }

    public void setOnStrobeTrigger(Runnable onStrobeTrigger) {
        this.onStrobeTrigger = onStrobeTrigger;
    }

    public void setOnStrobeHoldChange(Consumer<Boolean> onStrobeHoldChange) {
        this.onStrobeHoldChange = onStrobeHoldChange;
    }

    public void setOnMasterDimmerChange(Consumer<Double> onMasterDimmerChange) {
        this.onMasterDimmerChange = onMasterDimmerChange;
    }

    public void setOnMidiEvent(Consumer<MidiEventInfo> onMidiEvent) {
        this.onMidiEvent = onMidiEvent;
    }

    public void setOnLearnComplete(Runnable onLearnComplete) {
        this.onLearnComplete = onLearnComplete;
    }

    public void setOnBlackoutLearnComplete(Runnable onBlackoutLearnComplete) {
        this.onBlackoutLearnComplete = onBlackoutLearnComplete;
    }

    public void setOnStrobeLearnComplete(Runnable onStrobeLearnComplete) {
        this.onStrobeLearnComplete = onStrobeLearnComplete;
    }

    public void setOnMasterDimmerLearnComplete(Runnable onMasterDimmerLearnComplete) {
        this.onMasterDimmerLearnComplete = onMasterDimmerLearnComplete;
    }

    public String formatBindingText() {
        if (boundData1 < 0 || "ANY".equalsIgnoreCase(boundType)) {
            return I18n.get("midi.binding.any");
        }
        String chStr = (boundChannel >= 0) ? ("Ch " + (boundChannel + 1)) : "All Ch";
        if ("CC".equalsIgnoreCase(boundType)) {
            return String.format("CC %d  ·  %s", boundData1, chStr);
        }
        return String.format("Note %d (%s)  ·  %s", boundData1, formatNoteName(boundData1), chStr);
    }

    public String formatBlackoutBindingText() {
        if (blackoutBoundData1 < 0 || blackoutBoundType == null || "NONE".equalsIgnoreCase(blackoutBoundType)) {
            return I18n.get("midi.binding.none");
        }
        String chStr = (blackoutBoundChannel >= 0) ? ("Ch " + (blackoutBoundChannel + 1)) : "All Ch";
        if ("CC".equalsIgnoreCase(blackoutBoundType)) {
            return String.format("CC %d  ·  %s", blackoutBoundData1, chStr);
        }
        return String.format("Note %d (%s)  ·  %s", blackoutBoundData1, formatNoteName(blackoutBoundData1), chStr);
    }

    public String formatStrobeBindingText() {
        if (strobeBoundData1 < 0 || strobeBoundType == null || "NONE".equalsIgnoreCase(strobeBoundType)) {
            return I18n.get("midi.binding.none");
        }
        String chStr = (strobeBoundChannel >= 0) ? ("Ch " + (strobeBoundChannel + 1)) : "All Ch";
        if ("CC".equalsIgnoreCase(strobeBoundType)) {
            return String.format("CC %d  ·  %s", strobeBoundData1, chStr);
        }
        return String.format("Note %d (%s)  ·  %s", strobeBoundData1, formatNoteName(strobeBoundData1), chStr);
    }

    public String formatMasterDimmerBindingText() {
        if (masterDimmerBoundData1 < 0 || masterDimmerBoundType == null || "NONE".equalsIgnoreCase(masterDimmerBoundType)) {
            return I18n.get("midi.binding.none");
        }
        String chStr = (masterDimmerBoundChannel >= 0) ? ("Ch " + (masterDimmerBoundChannel + 1)) : "All Ch";
        if ("CC".equalsIgnoreCase(masterDimmerBoundType)) {
            return String.format("CC %d  ·  %s", masterDimmerBoundData1, chStr);
        }
        return String.format("Note %d (%s)  ·  %s", masterDimmerBoundData1, formatNoteName(masterDimmerBoundData1), chStr);
    }

    public static String formatNoteName(int noteNumber) {
        if (noteNumber < 0 || noteNumber > 127) {
            return "?";
        }
        int octave = (noteNumber / 12) - 1;
        String note = NOTE_NAMES[noteNumber % 12];
        return note + octave;
    }

    /**
     * Verarbeitet eine eingehende MIDI-Kurzmitteilung (sowohl für echte Hardware als auch für Tests).
     * Unterstützt sowohl kurzen Anschlag als auch langes Gedrückthalten (Note On -> Note Off / Vel 0, CC >= 64 -> CC < 64).
     */
    public synchronized void handleShortMessage(int command, int channel, int data1, int data2) {
        int ch = clampMidiChannel(channel);
        int d1 = clampMidiData(data1);
        int d2 = clampMidiData(data2);
        int idx = ch * 128 + d1;

        if (command == ShortMessage.NOTE_ON && d2 > 0) {
            boolean wasLearning = learningBlackout || learningStrobe || learningMasterDimmer;
            if (!wasLearning) {
                heldNotes[idx] = true;
            }
            processIncomingTrigger("NOTE", ch, d1, d2);
            updateBeatHeldState();
        } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && d2 == 0)) {
            heldNotes[idx] = false;
            updateBeatHeldState();
        } else if (command == ShortMessage.CONTROL_CHANGE) {
            if (learningMasterDimmer) {
                this.masterDimmerBoundType = "CC";
                this.masterDimmerBoundChannel = ch;
                this.masterDimmerBoundData1 = d1;
                this.learningMasterDimmer = false;
                if (onMasterDimmerLearnComplete != null) {
                    onMasterDimmerLearnComplete.run();
                }
                double dimVal = Math.max(0.0, Math.min(1.0, d2 / 127.0));
                if (onMasterDimmerChange != null) {
                    onMasterDimmerChange.accept(dimVal);
                }
                if (onMidiEvent != null) {
                    onMidiEvent.accept(new MidiEventInfo("CC", ch, d1, d2, true));
                }
                return;
            }

            if (matchesMasterDimmerBinding("CC", ch, d1)) {
                double dimVal = Math.max(0.0, Math.min(1.0, d2 / 127.0));
                if (onMasterDimmerChange != null) {
                    onMasterDimmerChange.accept(dimVal);
                }
                if (onMidiEvent != null) {
                    onMidiEvent.accept(new MidiEventInfo("CC", ch, d1, d2, true));
                }
                return;
            }

            if (d2 >= 64) {
                boolean wasLearning = learningBlackout || learningStrobe;
                if (!wasLearning) {
                    heldCcs[idx] = true;
                }
                if (!ccHighState[d1]) {
                    ccHighState[d1] = true;
                    processIncomingTrigger("CC", ch, d1, d2);
                }
                updateBeatHeldState();
            } else {
                ccHighState[d1] = false;
                heldCcs[idx] = false;
                updateBeatHeldState();
            }
        }
    }

    private void resetHeldState() {
        java.util.Arrays.fill(heldNotes, false);
        java.util.Arrays.fill(heldCcs, false);
        java.util.Arrays.fill(ccHighState, false);
        if (beatHeld) {
            beatHeld = false;
            if (onBeatHoldChange != null) {
                onBeatHoldChange.accept(false);
            }
        }
        if (blackoutHeld) {
            blackoutHeld = false;
            if (onBlackoutHoldChange != null) {
                onBlackoutHoldChange.accept(false);
            }
        }
        if (strobeHeld) {
            strobeHeld = false;
            if (onStrobeHoldChange != null) {
                onStrobeHoldChange.accept(false);
            }
        }
    }

    private void updateBeatHeldState() {
        boolean anyMatchingHeld = false;
        boolean anyBlackoutHeld = false;
        boolean anyStrobeHeld = false;
        for (int ch = 0; ch < 16; ch++) {
            for (int d1 = 0; d1 < 128; d1++) {
                int idx = ch * 128 + d1;
                if (heldNotes[idx]) {
                    if (matchesBlackoutBinding("NOTE", ch, d1)) {
                        anyBlackoutHeld = true;
                    } else if (matchesStrobeBinding("NOTE", ch, d1)) {
                        anyStrobeHeld = true;
                    } else if (matchesBinding("NOTE", ch, d1)) {
                        anyMatchingHeld = true;
                    }
                }
                if (heldCcs[idx]) {
                    if (matchesBlackoutBinding("CC", ch, d1)) {
                        anyBlackoutHeld = true;
                    } else if (matchesStrobeBinding("CC", ch, d1)) {
                        anyStrobeHeld = true;
                    } else if (matchesBinding("CC", ch, d1)) {
                        anyMatchingHeld = true;
                    }
                }
            }
        }
        if (this.beatHeld != anyMatchingHeld) {
            this.beatHeld = anyMatchingHeld;
            if (onBeatHoldChange != null) {
                onBeatHoldChange.accept(anyMatchingHeld);
            }
        }
        if (this.blackoutHeld != anyBlackoutHeld) {
            this.blackoutHeld = anyBlackoutHeld;
            if (onBlackoutHoldChange != null) {
                onBlackoutHoldChange.accept(anyBlackoutHeld);
            }
        }
        if (this.strobeHeld != anyStrobeHeld) {
            this.strobeHeld = anyStrobeHeld;
            if (onStrobeHoldChange != null) {
                onStrobeHoldChange.accept(anyStrobeHeld);
            }
        }
    }

    private void processIncomingTrigger(String type, int channel, int data1, int data2) {
        if (learningBlackout) {
            this.blackoutBoundType = type;
            this.blackoutBoundChannel = channel;
            this.blackoutBoundData1 = data1;
            this.learningBlackout = false;
            if (onBlackoutLearnComplete != null) {
                onBlackoutLearnComplete.run();
            }
            if (onMidiEvent != null) {
                onMidiEvent.accept(new MidiEventInfo(type, channel, data1, data2, true));
            }
            return;
        }

        if (learningStrobe) {
            this.strobeBoundType = type;
            this.strobeBoundChannel = channel;
            this.strobeBoundData1 = data1;
            this.learningStrobe = false;
            if (onStrobeLearnComplete != null) {
                onStrobeLearnComplete.run();
            }
            if (onMidiEvent != null) {
                onMidiEvent.accept(new MidiEventInfo(type, channel, data1, data2, true));
            }
            return;
        }

        if (learningMasterDimmer) {
            this.masterDimmerBoundType = type;
            this.masterDimmerBoundChannel = channel;
            this.masterDimmerBoundData1 = data1;
            this.learningMasterDimmer = false;
            if (onMasterDimmerLearnComplete != null) {
                onMasterDimmerLearnComplete.run();
            }
            double dimVal = Math.max(0.0, Math.min(1.0, data2 / 127.0));
            if (onMasterDimmerChange != null) {
                onMasterDimmerChange.accept(dimVal);
            }
            if (onMidiEvent != null) {
                onMidiEvent.accept(new MidiEventInfo(type, channel, data1, data2, true));
            }
            return;
        }

        if (matchesMasterDimmerBinding(type, channel, data1)) {
            double dimVal = Math.max(0.0, Math.min(1.0, data2 / 127.0));
            if (onMasterDimmerChange != null) {
                onMasterDimmerChange.accept(dimVal);
            }
            if (onMidiEvent != null) {
                onMidiEvent.accept(new MidiEventInfo(type, channel, data1, data2, true));
            }
            return;
        }

        if (learning) {
            this.boundType = type;
            this.boundChannel = channel;
            this.boundData1 = data1;
            this.learning = false;
            if (onLearnComplete != null) {
                onLearnComplete.run();
            }
        }

        if (matchesBlackoutBinding(type, channel, data1)) {
            if (onBlackoutTrigger != null) {
                onBlackoutTrigger.run();
            }
            if (onMidiEvent != null) {
                onMidiEvent.accept(new MidiEventInfo(type, channel, data1, data2, true));
            }
            return;
        }

        if (matchesStrobeBinding(type, channel, data1)) {
            if (onStrobeTrigger != null) {
                onStrobeTrigger.run();
            }
            if (onMidiEvent != null) {
                onMidiEvent.accept(new MidiEventInfo(type, channel, data1, data2, true));
            }
            return;
        }

        boolean matches = matchesBinding(type, channel, data1);
        if (matches && onBeatTrigger != null) {
            onBeatTrigger.run();
        }
        if (onMidiEvent != null) {
            onMidiEvent.accept(new MidiEventInfo(type, channel, data1, data2, matches));
        }
    }

    private boolean matchesBlackoutBinding(String type, int channel, int data1) {
        if (blackoutBoundData1 < 0 || blackoutBoundType == null || "NONE".equalsIgnoreCase(blackoutBoundType)) {
            return false;
        }
        if (!blackoutBoundType.equalsIgnoreCase(type)) {
            return false;
        }
        if (blackoutBoundChannel >= 0 && blackoutBoundChannel != channel) {
            return false;
        }
        return blackoutBoundData1 == data1;
    }

    private boolean matchesStrobeBinding(String type, int channel, int data1) {
        if (strobeBoundData1 < 0 || strobeBoundType == null || "NONE".equalsIgnoreCase(strobeBoundType)) {
            return false;
        }
        if (!strobeBoundType.equalsIgnoreCase(type)) {
            return false;
        }
        if (strobeBoundChannel >= 0 && strobeBoundChannel != channel) {
            return false;
        }
        return strobeBoundData1 == data1;
    }

    private boolean matchesMasterDimmerBinding(String type, int channel, int data1) {
        if (masterDimmerBoundData1 < 0 || masterDimmerBoundType == null || "NONE".equalsIgnoreCase(masterDimmerBoundType)) {
            return false;
        }
        if (!masterDimmerBoundType.equalsIgnoreCase(type)) {
            return false;
        }
        if (masterDimmerBoundChannel >= 0 && masterDimmerBoundChannel != channel) {
            return false;
        }
        return masterDimmerBoundData1 == data1;
    }

    private boolean matchesBinding(String type, int channel, int data1) {
        if (matchesBlackoutBinding(type, channel, data1) || matchesStrobeBinding(type, channel, data1) || matchesMasterDimmerBinding(type, channel, data1)) {
            return false;
        }
        if (boundData1 < 0 || "ANY".equalsIgnoreCase(boundType)) {
            return "NOTE".equals(type) || "CC".equals(type);
        }
        if (!boundType.equalsIgnoreCase(type)) {
            return false;
        }
        if (boundChannel >= 0 && boundChannel != channel) {
            return false;
        }
        return boundData1 == data1;
    }

    private class MidiInputReceiver implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (message instanceof ShortMessage sm) {
                handleShortMessage(sm.getCommand(), sm.getChannel(), sm.getData1(), sm.getData2());
            }
        }

        @Override
        public void close() {
        }
    }
}

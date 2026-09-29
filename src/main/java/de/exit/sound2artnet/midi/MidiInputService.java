package de.exit.sound2artnet.midi;

import de.exit.sound2artnet.util.I18n;

import javax.sound.midi.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verwaltet MIDI-Eingangsgeräte (USB-MIDI-Keyboards, Pad-Controller, DJ-Controller),
 * MIDI-Learn für Tasten-/Pad-Zuweisung und das latenzfreie Auslösen manueller Beats.
 */
public class MidiInputService {
    private static final Logger LOGGER = Logger.getLogger(MidiInputService.class.getName());
    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

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
    private String activeDeviceName = "";

    // Filter / Binding (-1 = Alle Kanäle / Alle Tasten)
    private volatile String boundType = "ANY"; // "ANY", "NOTE", "CC"
    private volatile int boundChannel = -1;    // -1 oder 0..15
    private volatile int boundData1 = -1;      // -1 oder 0..127

    private volatile boolean learning = false;
    private final boolean[] ccHighState = new boolean[128];

    private Runnable onBeatTrigger;
    private Consumer<MidiEventInfo> onMidiEvent;
    private Runnable onLearnComplete;

    /**
     * Listet alle verfügbaren MIDI-Eingangsgeräte (Transmitter) auf.
     */
    public static List<MidiDeviceInfo> listInputDevices() {
        List<MidiDeviceInfo> result = new ArrayList<>();
        try {
            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
            for (MidiDevice.Info info : infos) {
                try {
                    MidiDevice dev = MidiSystem.getMidiDevice(info);
                    // Nur echte Eingangsgeräte (MaxTransmitters != 0) und keinen internen Sequencer
                    if (dev.getMaxTransmitters() != 0 && !(dev instanceof Sequencer) && !(dev instanceof Synthesizer)) {
                        String rawName = info.getName() != null ? info.getName().trim() : "MIDI Input";
                        if (rawName.equalsIgnoreCase("Real Time Sequencer")) {
                            continue;
                        }
                        result.add(new MidiDeviceInfo(
                                rawName,
                                info.getVendor() != null ? info.getVendor().trim() : "",
                                info.getDescription() != null ? info.getDescription().trim() : "",
                                info
                        ));
                    }
                } catch (MidiUnavailableException ignored) {
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Fehler beim Scannen der MIDI-Geräte: " + t.getMessage(), t);
        }
        return result;
    }

    public synchronized boolean openDevice(MidiDeviceInfo deviceInfo) {
        closeDevice();
        if (deviceInfo == null || deviceInfo.midiInfo() == null) {
            return false;
        }
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
            LOGGER.info("MIDI-Eingangsgerät geöffnet: " + activeDeviceName);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte MIDI-Gerät nicht öffnen (" + deviceInfo.name() + "): " + e.getMessage(), e);
            closeDevice();
            return false;
        }
    }

    public synchronized void closeDevice() {
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
    }

    public synchronized boolean isOpen() {
        return activeDevice != null && activeDevice.isOpen();
    }

    public String getActiveDeviceName() {
        return activeDeviceName;
    }

    public void setBinding(String type, int channel, int data1) {
        this.boundType = (type != null && !type.isBlank()) ? type : "ANY";
        this.boundChannel = channel;
        this.boundData1 = data1;
    }

    public void clearBinding() {
        this.boundType = "ANY";
        this.boundChannel = -1;
        this.boundData1 = -1;
        this.learning = false;
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

    public boolean isLearning() {
        return learning;
    }

    public void setLearning(boolean learning) {
        this.learning = learning;
    }

    public void setOnBeatTrigger(Runnable onBeatTrigger) {
        this.onBeatTrigger = onBeatTrigger;
    }

    public void setOnMidiEvent(Consumer<MidiEventInfo> onMidiEvent) {
        this.onMidiEvent = onMidiEvent;
    }

    public void setOnLearnComplete(Runnable onLearnComplete) {
        this.onLearnComplete = onLearnComplete;
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
     */
    public void handleShortMessage(int command, int channel, int data1, int data2) {
        if (command == ShortMessage.NOTE_ON && data2 > 0) {
            processIncomingTrigger("NOTE", channel, data1, data2);
        } else if (command == ShortMessage.CONTROL_CHANGE) {
            int ccIdx = Math.max(0, Math.min(127, data1));
            if (data2 >= 64) {
                if (!ccHighState[ccIdx]) {
                    ccHighState[ccIdx] = true;
                    processIncomingTrigger("CC", channel, data1, data2);
                }
            } else {
                ccHighState[ccIdx] = false;
            }
        }
    }

    private void processIncomingTrigger(String type, int channel, int data1, int data2) {
        if (learning) {
            this.boundType = type;
            this.boundChannel = channel;
            this.boundData1 = data1;
            this.learning = false;
            if (onLearnComplete != null) {
                onLearnComplete.run();
            }
        }

        boolean matches = matchesBinding(type, channel, data1);
        if (matches && onBeatTrigger != null) {
            onBeatTrigger.run();
        }
        if (onMidiEvent != null) {
            onMidiEvent.accept(new MidiEventInfo(type, channel, data1, data2, matches));
        }
    }

    private boolean matchesBinding(String type, int channel, int data1) {
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

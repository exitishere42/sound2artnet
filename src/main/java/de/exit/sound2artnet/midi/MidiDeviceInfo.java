package de.exit.sound2artnet.midi;

import javax.sound.midi.MidiDevice;

/**
 * Repräsentiert ein verfügbares MIDI-Eingangsgerät (Controller, Keyboard, Pad)
 * plattformübergreifend für Windows (WinMM / Java MIDI) und Linux (ALSA Sequencer / PipeWire / RawMIDI).
 */
public record MidiDeviceInfo(
        String name,
        String vendor,
        String description,
        MidiDevice.Info midiInfo,
        String alsaSeqPort,
        String alsaRawPort
) {
    public MidiDeviceInfo(String name, String vendor, String description, MidiDevice.Info midiInfo) {
        this(name, vendor, description, midiInfo, null, null);
    }

    @Override
    public String toString() {
        return name;
    }
}

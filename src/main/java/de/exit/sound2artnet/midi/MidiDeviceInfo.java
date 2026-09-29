package de.exit.sound2artnet.midi;

import javax.sound.midi.MidiDevice;

/**
 * Repräsentiert ein verfügbares MIDI-Eingangsgerät (Controller, Keyboard, Pad).
 */
public record MidiDeviceInfo(
        String name,
        String vendor,
        String description,
        MidiDevice.Info midiInfo
) {
    @Override
    public String toString() {
        return name;
    }
}

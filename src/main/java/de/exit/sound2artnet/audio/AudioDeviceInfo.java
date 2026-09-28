package de.exit.sound2artnet.audio;

import javax.sound.sampled.Mixer;

/**
 * Information über ein verfügbares Audio-Eingabegerät.
 */
public record AudioDeviceInfo(
    String name,
    String description,
    Mixer.Info mixerInfo,
    boolean isDefault,
    boolean isLoopback
) {
    public AudioDeviceInfo(String name, String description, Mixer.Info mixerInfo, boolean isDefault) {
        this(name, description, mixerInfo, isDefault, false);
    }

    public static AudioDeviceInfo pcSoundLoopback() {
        return new AudioDeviceInfo(
            "PC-Sound",
            "Direkter PC-Sound",
            null,
            false,
            true
        );
    }

    @Override
    public String toString() {
        return name;
    }
}

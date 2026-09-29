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
    boolean isLoopback,
    String linuxTargetId
) {
    public AudioDeviceInfo(String name, String description, Mixer.Info mixerInfo, boolean isDefault) {
        this(name, description, mixerInfo, isDefault, false, null);
    }

    public AudioDeviceInfo(String name, String description, Mixer.Info mixerInfo, boolean isDefault, boolean isLoopback) {
        this(name, description, mixerInfo, isDefault, isLoopback, null);
    }

    public static AudioDeviceInfo pcSoundLoopback() {
        return new AudioDeviceInfo(
            "PC-Sound",
            "Direkter PC-Sound",
            null,
            false,
            true,
            null
        );
    }

    public static AudioDeviceInfo linuxMicrophone(String name, String targetId, boolean isDefault) {
        return new AudioDeviceInfo(
            name,
            "Mikrofon (" + name + ")",
            null,
            isDefault,
            false,
            targetId
        );
    }

    public boolean isLinuxStream() {
        return linuxTargetId != null || (mixerInfo == null && !isLoopback && AudioCaptureService.isLinux());
    }

    @Override
    public String toString() {
        return name;
    }
}

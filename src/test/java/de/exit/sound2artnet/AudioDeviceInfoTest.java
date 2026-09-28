package de.exit.sound2artnet;

import de.exit.sound2artnet.audio.AudioCaptureService;
import de.exit.sound2artnet.audio.AudioDeviceInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AudioDeviceInfoTest {

    @Test
    void testPcSoundLoopbackDeviceInfo() {
        AudioDeviceInfo dev = AudioDeviceInfo.pcSoundLoopback();
        assertNotNull(dev);
        assertEquals("PC-Sound", dev.name());
        assertEquals("PC-Sound", dev.toString());
        assertTrue(dev.isLoopback());
        assertFalse(dev.isDefault());
        assertNull(dev.mixerInfo());
        assertEquals("Direkter PC-Sound", dev.description());
    }

    @Test
    void testLoopbackSupportedOnCurrentOs() {
        boolean supported = AudioCaptureService.isLoopbackSupported();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") || os.contains("linux") || os.contains("unix")) {
            assertTrue(supported, "Loopback sollte unter Windows und Linux als unterstützt gemeldet werden");
        }
    }

    @Test
    void testListInputDevicesContainsLoopbackIfSupported() {
        List<AudioDeviceInfo> devices = AudioCaptureService.listInputDevices();
        if (AudioCaptureService.isLoopbackSupported()) {
            assertFalse(devices.isEmpty(), "Geräteliste darf nicht leer sein");
            AudioDeviceInfo first = devices.get(0);
            assertEquals("PC-Sound", first.name(), "Erstes Gerät sollte PC-Sound sein");
            assertTrue(first.isLoopback(), "Erstes Gerät sollte loopback sein");
        }
    }

    @Test
    void testBuildLinuxLoopbackCommandsSyntax() {
        // Syntax- und Formatvalidierung der Linux-Befehlsgenerierung
        List<List<String>> commands = AudioCaptureService.buildLinuxLoopbackCommands();
        assertNotNull(commands);
        for (List<String> cmd : commands) {
            assertFalse(cmd.isEmpty());
            String tool = cmd.get(0);
            assertTrue(List.of("pw-record", "parec", "pacat", "ffmpeg").contains(tool),
                    "Tool sollte eines der bekannten Linux-Audiowerkzeuge sein: " + tool);
        }
    }

    @Test
    void testResolvePulseDefaultMonitorFallback() {
        String monitor = AudioCaptureService.resolvePulseDefaultMonitor();
        assertNotNull(monitor);
        assertFalse(monitor.isBlank());
    }
}

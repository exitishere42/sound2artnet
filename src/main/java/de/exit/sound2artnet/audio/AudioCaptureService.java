package de.exit.sound2artnet.audio;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Erfasst Audiodaten in Echtzeit über das Java Sound API (javax.sound.sampled).
 */
public class AudioCaptureService {
    private static final Logger LOGGER = Logger.getLogger(AudioCaptureService.class.getName());

    public static final float SAMPLE_RATE = 44100.0f;
    public static final int SAMPLE_SIZE_IN_BITS = 16;
    public static final int CHANNELS = 1; // Mono für effiziente Analyse
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = false;

    private final AudioSpectrumAnalyzer analyzer = new AudioSpectrumAnalyzer(SAMPLE_RATE);
    private TargetDataLine targetLine;
    private Thread captureThread;
    private volatile boolean isRunning = false;
    private String currentDeviceName = "Standard-Eingabe";

    private Process wasapiProcess;

    /**
     * Ermittelt alle verfügbaren Aufnahme-Audiogeräte.
     * Unter Windows wird an erster Stelle der direkte PC-Sound (WASAPI Loopback) angeboten.
     */
    public static List<AudioDeviceInfo> listInputDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();

        // 1. Unter Windows: Direkter PC-Sound Loopback an erster Stelle
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            devices.add(AudioDeviceInfo.pcSoundLoopback());
        }

        // 2. Reguläre Java Sound Capture-Geräte auflisten
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        AudioFormat testFormat = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, testFormat);

        boolean first = devices.isEmpty();
        for (Mixer.Info info : mixerInfos) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(lineInfo)) {
                    devices.add(new AudioDeviceInfo(info.getName(), info.getDescription(), info, first));
                    first = false;
                }
            } catch (Exception ignored) {
            }
        }
        return devices;
    }

    public static void printAllAudioDevices() {
        System.out.println("=== Java Sound Audio-Geräte ===");
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (int i = 0; i < mixerInfos.length; i++) {
            Mixer.Info info = mixerInfos[i];
            System.out.printf("[%d] Name: %s | Desc: %s%n", i, info.getName(), info.getDescription());
            try {
                Mixer m = AudioSystem.getMixer(info);
                for (Line.Info tli : m.getTargetLineInfo()) {
                    System.out.println("    -> Aufnahme (Input): " + tli);
                }
                for (Line.Info sli : m.getSourceLineInfo()) {
                    System.out.println("    -> Wiedergabe (Output): " + sli);
                }
            } catch (Exception e) {
                System.out.println("    Fehler: " + e.getMessage());
            }
        }
    }

    /**
     * Startet die Audioaufnahme mit dem angegebenen AudioDeviceInfo (unterstützt auch WASAPI Loopback).
     */
    public synchronized void start(AudioDeviceInfo deviceInfo) throws Exception {
        stop();
        if (deviceInfo != null && deviceInfo.isLoopback()) {
            startWasapiLoopback();
        } else {
            start(deviceInfo != null ? deviceInfo.mixerInfo() : null);
        }
    }

    /**
     * Startet die Audioaufnahme mit dem angegebenen Mixer.
     * Wenn mixerInfo null ist, wird das Standard-Aufnahmegerät des Betriebssystems verwendet.
     */
    public synchronized void start(Mixer.Info mixerInfo) throws LineUnavailableException {
        stop();

        AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (mixerInfo != null) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            targetLine = (TargetDataLine) mixer.getLine(info);
            currentDeviceName = mixerInfo.getName();
        } else {
            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            currentDeviceName = "Standard-Eingabe";
        }

        // Puffergröße: 2048 Samples (ca. 46ms bei 44.1kHz)
        int bufferByteSize = AudioSpectrumAnalyzer.FFT_SIZE * 2 * CHANNELS;
        targetLine.open(format, bufferByteSize * 2);
        targetLine.start();

        analyzer.setSampleRate(SAMPLE_RATE);
        isRunning = true;
        captureThread = new Thread(this::captureLoop, "AudioCaptureThread");
        captureThread.setDaemon(true);
        captureThread.setPriority(Thread.MAX_PRIORITY);
        captureThread.start();

        LOGGER.info("Audioaufnahme gestartet mit: " + currentDeviceName);
    }

    private void startWasapiLoopback() throws IOException {
        File exe = resolveWasapiExecutable();
        if (exe == null || !exe.exists()) {
            throw new IOException("wasapi_loopback.exe wurde nicht gefunden. Bitte sicherstellen, dass die Datei existiert.");
        }

        ProcessBuilder pb = new ProcessBuilder(exe.getAbsolutePath());
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        wasapiProcess = pb.start();

        float sampleRate = 48000.0f;
        try {
            java.io.BufferedReader errReader = new java.io.BufferedReader(new java.io.InputStreamReader(wasapiProcess.getErrorStream()));
            String line = errReader.readLine();
            if (line != null && line.startsWith("WASAPI_READY:")) {
                String[] parts = line.split(":");
                if (parts.length >= 2) {
                    sampleRate = Float.parseFloat(parts[1]);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte Header von WASAPI Loopback nicht lesen: " + e.getMessage());
        }

        analyzer.setSampleRate(sampleRate);
        currentDeviceName = "PC-Sound";
        isRunning = true;

        captureThread = new Thread(() -> loopbackCaptureLoop(wasapiProcess.getInputStream()), "WasapiLoopbackThread");
        captureThread.setDaemon(true);
        captureThread.setPriority(Thread.MAX_PRIORITY);
        captureThread.start();

        LOGGER.info("WASAPI Loopback gestartet mit Abtastrate " + sampleRate + " Hz");
    }

    public static File resolveWasapiExecutable() {
        String[] possibleNames = {
            "tools/wasapi-loopback.exe",
            "tools/wasapi_loopback.exe",
            "src/main/resources/native/wasapi_loopback.exe",
            "src/main/resources/native/wasapi-loopback.exe",
            "wasapi-loopback.exe",
            "wasapi_loopback.exe"
        };
        for (String path : possibleNames) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return f;
            }
        }

        // Aus JAR extrahieren (falls als Fat-JAR betrieben)
        try {
            var stream = AudioCaptureService.class.getResourceAsStream("/native/wasapi_loopback.exe");
            if (stream == null) {
                stream = AudioCaptureService.class.getResourceAsStream("/native/wasapi-loopback.exe");
            }
            if (stream != null) {
                File tempFile = new File(System.getProperty("java.io.tmpdir"), "sound2artnet-wasapi-loopback.exe");
                if (!tempFile.exists() || tempFile.length() < 1000) {
                    java.nio.file.Files.copy(stream, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                stream.close();
                tempFile.setExecutable(true);
                return tempFile;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte wasapi_loopback.exe nicht aus JAR extrahieren: " + e.getMessage());
        }

        return null;
    }

    private void loopbackCaptureLoop(java.io.InputStream in) {
        int fftSize = AudioSpectrumAnalyzer.FFT_SIZE;
        int hopSize = AudioSpectrumAnalyzer.HOP_SIZE;
        byte[] byteBuffer = new byte[hopSize * 2]; // 1024 Bytes
        float[] ringBuffer = new float[fftSize];
        int samplesCollected = 0;
        int leftoverByte = -1;

        try {
            while (isRunning && wasapiProcess != null && wasapiProcess.isAlive()) {
                int bytesRead = in.read(byteBuffer, 0, byteBuffer.length);
                if (bytesRead <= 0) {
                    if (bytesRead == -1) break;
                    Thread.sleep(2);
                    continue;
                }

                int byteIdx = 0;
                // Falls vom vorherigen Read-Aufruf noch ein unvollständiges Byte übrig war
                if (leftoverByte != -1) {
                    int low = leftoverByte;
                    int high = byteBuffer[byteIdx++];
                    short val = (short) ((high << 8) | low);
                    ringBuffer[samplesCollected++] = val / 32768.0f;
                    leftoverByte = -1;
                    if (samplesCollected == fftSize) {
                        analyzer.processSamples(ringBuffer, fftSize);
                        System.arraycopy(ringBuffer, hopSize, ringBuffer, 0, hopSize);
                        samplesCollected = hopSize;
                    }
                }

                while (byteIdx + 1 < bytesRead) {
                    int low = byteBuffer[byteIdx++] & 0xFF;
                    int high = byteBuffer[byteIdx++];
                    short val = (short) ((high << 8) | low);
                    ringBuffer[samplesCollected++] = val / 32768.0f;

                    if (samplesCollected == fftSize) {
                        analyzer.processSamples(ringBuffer, fftSize);
                        System.arraycopy(ringBuffer, hopSize, ringBuffer, 0, hopSize);
                        samplesCollected = hopSize;
                    }
                }

                // Falls am Ende genau 1 Byte übrig bleibt, für den nächsten Durchlauf merken
                if (byteIdx < bytesRead) {
                    leftoverByte = byteBuffer[byteIdx] & 0xFF;
                }
            }
        } catch (Exception e) {
            if (isRunning) {
                LOGGER.log(Level.WARNING, "Fehler beim Lesen des WASAPI-Loopback-Streams: " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        isRunning = false;

        if (wasapiProcess != null) {
            try {
                wasapiProcess.getOutputStream().close();
                wasapiProcess.destroy();
                wasapiProcess.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (wasapiProcess.isAlive()) {
                    wasapiProcess.destroyForcibly();
                }
            } catch (Exception ignored) {}
            wasapiProcess = null;
        }

        if (targetLine != null) {
            try {
                targetLine.stop();
                targetLine.close();
            } catch (Exception ignored) {}
            targetLine = null;
        }
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        LOGGER.info("Audioaufnahme gestoppt.");
    }

    private void captureLoop() {
        int fftSize = AudioSpectrumAnalyzer.FFT_SIZE;
        int hopSize = AudioSpectrumAnalyzer.HOP_SIZE;
        byte[] byteBuffer = new byte[hopSize * 2]; // 16-bit mono = 2 Bytes pro Sample
        float[] ringBuffer = new float[fftSize];
        int samplesCollected = 0;

        while (isRunning && targetLine != null && targetLine.isOpen()) {
            int bytesRead = targetLine.read(byteBuffer, 0, byteBuffer.length);
            if (bytesRead <= 0) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            int samplesRead = bytesRead / 2;
            for (int i = 0; i < samplesRead; i++) {
                int low = byteBuffer[i * 2] & 0xFF;
                int high = byteBuffer[i * 2 + 1];
                short val = (short) ((high << 8) | low);
                ringBuffer[samplesCollected++] = val / 32768.0f;

                if (samplesCollected == fftSize) {
                    analyzer.processSamples(ringBuffer, fftSize);
                    System.arraycopy(ringBuffer, hopSize, ringBuffer, 0, hopSize);
                    samplesCollected = hopSize;
                }
            }
        }
    }

    public boolean isRunning() {
        return isRunning && ((targetLine != null && targetLine.isOpen()) || (wasapiProcess != null && wasapiProcess.isAlive()));
    }

    public AudioSpectrumAnalyzer getAnalyzer() {
        return analyzer;
    }

    public String getCurrentDeviceName() {
        return currentDeviceName;
    }
}

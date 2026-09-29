package de.exit.sound2artnet.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Erfasst Audiodaten in Echtzeit über das Java Sound API (javax.sound.sampled)
 * oder native Desktop-Audio- und Mikrofon-Streams unter Windows und Linux.
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

    private Process captureProcess;

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux") || os.contains("unix");
    }

    public static boolean isLoopbackSupported() {
        return isWindows() || isLinux();
    }

    public static boolean isLinuxAudioSupported() {
        if (!isLinux()) return false;
        return isCommandAvailable("pw-record")
            || isCommandAvailable("parec")
            || isCommandAvailable("pacat")
            || isCommandAvailable("ffmpeg");
    }

    public static boolean isLinuxLoopbackSupported() {
        return isLinuxAudioSupported();
    }

    /**
     * Ermittelt alle verfügbaren Aufnahme-Audiogeräte.
     * Unter Linux werden native PipeWire/PulseAudio Quellen (PC-Sound und Mikrofone) bevorzugt.
     * Unter Windows wird an erster Stelle der direkte PC-Sound (WASAPI) angeboten.
     */
    public static List<AudioDeviceInfo> listInputDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();

        if (isLinux()) {
            List<AudioDeviceInfo> linuxDevs = listLinuxDevices();
            if (!linuxDevs.isEmpty()) {
                return linuxDevs;
            }
        }

        // 1. Loopback (PC-Sound) an erster Stelle anbieten (Windows / ALSA-Fallback)
        if (isLoopbackSupported()) {
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

    /**
     * Ermittelt unter Linux die nativen Audioquellen via PipeWire (pw-dump) oder PulseAudio (pactl).
     */
    public static List<AudioDeviceInfo> listLinuxDevices() {
        List<AudioDeviceInfo> list = new ArrayList<>();
        if (!isLinuxLoopbackSupported()) {
            return list;
        }

        // 1. PC-Sound (Desktop Loopback)
        list.add(AudioDeviceInfo.pcSoundLoopback());

        // 2. Spezifische Mikrofone via PipeWire (pw-dump) ermitteln
        boolean foundSpecific = false;
        if (isCommandAvailable("pw-dump")) {
            try {
                Process p = new ProcessBuilder("pw-dump")
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(p.getInputStream());
                p.waitFor();
                if (root != null && root.isArray()) {
                    for (JsonNode node : root) {
                        if ("PipeWire:Interface:Node".equals(node.path("type").asText())) {
                            JsonNode props = node.path("info").path("props");
                            String mediaClass = props.path("media.class").asText("");
                            if ("Audio/Source".equalsIgnoreCase(mediaClass)) {
                                String desc = props.path("node.description").asText("").trim();
                                String name = props.path("node.name").asText("").trim();
                                int id = node.path("id").asInt(0);
                                String displayName = !desc.isBlank() ? desc : name;
                                if (!displayName.isBlank() && !displayName.endsWith(".monitor")) {
                                    // Bereinigen von Klammerzusätzen gemäß UI-Richtlinie
                                    displayName = displayName.replaceAll("\\s*\\([^)]*\\)", "").trim();
                                    if (displayName.isBlank()) {
                                        displayName = "Mikrofon";
                                    }
                                    list.add(AudioDeviceInfo.linuxMicrophone(displayName, String.valueOf(id), !foundSpecific));
                                    foundSpecific = true;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.fine("pw-dump Parsing fehlgeschlagen: " + e.getMessage());
            }
        }

        // 3. Fallback über pactl
        if (!foundSpecific && isCommandAvailable("pactl")) {
            try {
                Process p = new ProcessBuilder("pactl", "list", "short", "sources")
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            String sourceName = parts[1];
                            if (!sourceName.endsWith(".monitor")) {
                                list.add(AudioDeviceInfo.linuxMicrophone("Mikrofon " + parts[0], sourceName, !foundSpecific));
                                foundSpecific = true;
                            }
                        }
                    }
                }
                p.waitFor();
            } catch (Exception ignored) {}
        }

        // 4. Falls keine spezifischen Quellen benannt wurden: Standard "Mikrofon"
        if (!foundSpecific) {
            list.add(AudioDeviceInfo.linuxMicrophone("Mikrofon", null, true));
        }

        return list;
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
     * Startet die Audioaufnahme mit dem angegebenen AudioDeviceInfo (unterstützt WASAPI und Linux native Capture).
     */
    public synchronized void start(AudioDeviceInfo deviceInfo) throws Exception {
        stop();
        if (deviceInfo != null && isLinux() && (deviceInfo.isLoopback() || deviceInfo.isLinuxStream())) {
            startLinuxCapture(deviceInfo);
        } else if (deviceInfo != null && deviceInfo.isLoopback()) {
            if (isWindows()) {
                startWasapiLoopback();
            } else {
                throw new UnsupportedOperationException("PC-Sound Loopback wird auf diesem Betriebssystem nicht unterstützt.");
            }
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
        captureProcess = pb.start();

        float sampleRate = 48000.0f;
        try {
            java.io.BufferedReader errReader = new java.io.BufferedReader(new java.io.InputStreamReader(captureProcess.getErrorStream()));
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

        captureThread = new Thread(() -> loopbackCaptureLoop(captureProcess.getInputStream()), "WasapiLoopbackThread");
        captureThread.setDaemon(true);
        captureThread.setPriority(Thread.MAX_PRIORITY);
        captureThread.start();

        LOGGER.info("WASAPI Loopback gestartet mit Abtastrate " + sampleRate + " Hz");
    }

    private void startLinuxCapture(AudioDeviceInfo deviceInfo) throws IOException {
        List<List<String>> candidates = buildLinuxCommands(deviceInfo);
        if (candidates.isEmpty()) {
            throw new IOException("Kein unterstütztes Linux-Audiotool gefunden. Bitte 'pipewire-bin', 'pulseaudio-utils' oder 'ffmpeg' installieren.");
        }

        IOException lastException = null;
        for (List<String> cmd : candidates) {
            Process proc = null;
            try {
                LOGGER.info("Versuche Linux-Audioaufnahme (" + deviceInfo.name() + ") mit: " + String.join(" ", cmd));
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectError(ProcessBuilder.Redirect.PIPE);
                proc = pb.start();

                final Process p = proc;
                Thread errDrainer = new Thread(() -> {
                    try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getErrorStream()))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            LOGGER.fine("[LinuxCapture stderr] " + line);
                        }
                    } catch (Exception ignored) {}
                }, "LinuxCapture-ErrDrainer");
                errDrainer.setDaemon(true);
                errDrainer.start();

                // Kurz prüfen (120ms), ob der Prozess aktiv bleibt
                boolean exited = proc.waitFor(120, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!exited && proc.isAlive()) {
                    captureProcess = proc;
                    analyzer.setSampleRate(SAMPLE_RATE);
                    currentDeviceName = deviceInfo.name();
                    isRunning = true;

                    captureThread = new Thread(() -> loopbackCaptureLoop(p.getInputStream()), "LinuxCaptureThread");
                    captureThread.setDaemon(true);
                    captureThread.setPriority(Thread.MAX_PRIORITY);
                    captureThread.start();

                    LOGGER.info("Linux Audioaufnahme erfolgreich gestartet (" + currentDeviceName + ") via: " + cmd.get(0));
                    return;
                } else {
                    int exitCode = proc.exitValue();
                    LOGGER.warning("Kandidat " + cmd.get(0) + " brach ab mit Exit-Code " + exitCode);
                }
            } catch (Exception e) {
                LOGGER.warning("Kandidat " + cmd.get(0) + " konnte nicht gestartet werden: " + e.getMessage());
                lastException = new IOException(e);
                if (proc != null && proc.isAlive()) {
                    proc.destroyForcibly();
                }
            }
        }

        throw new IOException("Linux Audioaufnahme (" + deviceInfo.name() + ") konnte nicht gestartet werden (PipeWire/PulseAudio nicht verfügbar oder Zugriff verweigert).", lastException);
    }

    public static List<List<String>> buildLinuxCommands(AudioDeviceInfo deviceInfo) {
        List<List<String>> list = new ArrayList<>();
        boolean isLoopback = deviceInfo != null && deviceInfo.isLoopback();
        String targetId = deviceInfo != null ? deviceInfo.linuxTargetId() : null;

        if (isLoopback) {
            // === PC-Sound (Desktop Sink Monitor) ===
            // 1. PipeWire: pw-record mit stream.capture.sink=true
            if (isCommandAvailable("pw-record")) {
                if (targetId != null) {
                    list.add(List.of("pw-record", "--target", targetId, "-P", "{ stream.capture.sink=true }", "--format", "s16", "--rate", "44100", "--channels", "1", "--raw", "-"));
                }
                list.add(List.of("pw-record", "-P", "{ stream.capture.sink=true }", "--format", "s16", "--rate", "44100", "--channels", "1", "--raw", "-"));
            }

            // 2. PulseAudio: parec
            String pulseMonitor = resolvePulseDefaultMonitor();
            if (isCommandAvailable("parec")) {
                list.add(List.of("parec", "-d", "@DEFAULT_MONITOR@", "--format=s16le", "--rate=44100", "--channels=1", "--raw"));
                if (pulseMonitor != null && !pulseMonitor.equals("@DEFAULT_MONITOR@")) {
                    list.add(List.of("parec", "-d", pulseMonitor, "--format=s16le", "--rate=44100", "--channels=1", "--raw"));
                }
                list.add(List.of("parec", "--format=s16le", "--rate=44100", "--channels=1", "--raw"));
            }

            // 3. PulseAudio: pacat
            if (isCommandAvailable("pacat")) {
                list.add(List.of("pacat", "--record", "-d", "@DEFAULT_MONITOR@", "--format=s16le", "--rate=44100", "--channels=1", "--raw"));
            }

            // 4. FFmpeg
            if (isCommandAvailable("ffmpeg")) {
                list.add(List.of("ffmpeg", "-nostats", "-loglevel", "error", "-f", "pulse", "-i", "@DEFAULT_MONITOR@", "-f", "s16le", "-ar", "44100", "-ac", "1", "-"));
                if (pulseMonitor != null && !pulseMonitor.equals("@DEFAULT_MONITOR@")) {
                    list.add(List.of("ffmpeg", "-nostats", "-loglevel", "error", "-f", "pulse", "-i", pulseMonitor, "-f", "s16le", "-ar", "44100", "-ac", "1", "-"));
                }
                list.add(List.of("ffmpeg", "-nostats", "-loglevel", "error", "-f", "pulse", "-i", "default", "-f", "s16le", "-ar", "44100", "-ac", "1", "-"));
            }
        } else {
            // === Mikrofon / Audio-Eingang ===
            // 1. PipeWire: pw-record (standardmäßig Mikrofon)
            if (isCommandAvailable("pw-record")) {
                if (targetId != null) {
                    list.add(List.of("pw-record", "--target", targetId, "--format", "s16", "--rate", "44100", "--channels", "1", "--raw", "-"));
                }
                list.add(List.of("pw-record", "--format", "s16", "--rate", "44100", "--channels", "1", "--raw", "-"));
            }

            // 2. PulseAudio: parec
            if (isCommandAvailable("parec")) {
                if (targetId != null) {
                    list.add(List.of("parec", "-d", targetId, "--format=s16le", "--rate=44100", "--channels=1", "--raw"));
                }
                list.add(List.of("parec", "--format=s16le", "--rate=44100", "--channels=1", "--raw"));
            }

            // 3. PulseAudio: pacat
            if (isCommandAvailable("pacat")) {
                list.add(List.of("pacat", "--record", "--format=s16le", "--rate=44100", "--channels=1", "--raw"));
            }

            // 4. FFmpeg
            if (isCommandAvailable("ffmpeg")) {
                list.add(List.of("ffmpeg", "-nostats", "-loglevel", "error", "-f", "pulse", "-i", "default", "-f", "s16le", "-ar", "44100", "-ac", "1", "-"));
            }
        }

        return list;
    }

    public static List<List<String>> buildLinuxLoopbackCommands() {
        return buildLinuxCommands(AudioDeviceInfo.pcSoundLoopback());
    }

    public static boolean isCommandAvailable(String cmd) {
        String path = System.getenv("PATH");
        if (path != null) {
            String[] dirs = path.split(File.pathSeparator);
            for (String dir : dirs) {
                File file = new File(dir, cmd);
                if (file.exists() && file.canExecute()) {
                    return true;
                }
            }
        }
        for (String dir : List.of("/usr/bin", "/usr/local/bin", "/bin", "/snap/bin")) {
            File file = new File(dir, cmd);
            if (file.exists() && file.canExecute()) {
                return true;
            }
        }
        return false;
    }

    public static String resolvePulseDefaultMonitor() {
        try {
            Process p = new ProcessBuilder("pactl", "get-default-sink")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String sink = reader.readLine();
                if (sink != null && !sink.isBlank() && !sink.contains(" ")) {
                    return sink.trim() + ".monitor";
                }
            }
        } catch (Exception ignored) {}

        try {
            Process p = new ProcessBuilder("pactl", "info")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().startsWith("default sink:")) {
                        String sink = line.substring(line.indexOf(':') + 1).trim();
                        if (!sink.isBlank()) {
                            return sink + ".monitor";
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return "@DEFAULT_MONITOR@";
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
            while (isRunning && captureProcess != null && captureProcess.isAlive()) {
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
                LOGGER.log(Level.WARNING, "Fehler beim Lesen des Audio-Streams: " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        isRunning = false;

        if (captureProcess != null) {
            try {
                captureProcess.getOutputStream().close();
                captureProcess.destroy();
                captureProcess.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (captureProcess.isAlive()) {
                    captureProcess.destroyForcibly();
                }
            } catch (Exception ignored) {}
            captureProcess = null;
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
        return isRunning && ((targetLine != null && targetLine.isOpen()) || (captureProcess != null && captureProcess.isAlive()));
    }

    public AudioSpectrumAnalyzer getAnalyzer() {
        return analyzer;
    }

    public String getCurrentDeviceName() {
        return currentDeviceName;
    }
}

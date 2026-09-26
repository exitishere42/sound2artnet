package de.exit.sound2artnet;

import de.exit.sound2artnet.artnet.ArtNetSender;
import de.exit.sound2artnet.audio.AudioCaptureService;
import de.exit.sound2artnet.audio.AudioDeviceInfo;
import de.exit.sound2artnet.config.AppConfig;
import de.exit.sound2artnet.config.ConfigManager;
import de.exit.sound2artnet.engine.Sound2LightEngine;

import java.util.logging.Logger;

/**
 * Headless CLI Runner für den Betrieb von sound2artnet ohne grafische Oberfläche
 * (z. B. auf Servern, Raspberry Pi oder im Hintergrund).
 */
public class HeadlessRunner {
    private static final Logger LOGGER = Logger.getLogger(HeadlessRunner.class.getName());

    public static void run(String[] args) {
        AppConfig config = ConfigManager.loadConfig();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--ip") && i + 1 < args.length) {
                config.setTargetIp(args[++i]);
            } else if (arg.equals("--universe") && i + 1 < args.length) {
                config.setUniverse(Integer.parseInt(args[++i]));
            } else if (arg.equals("--fps") && i + 1 < args.length) {
                config.setFps(Integer.parseInt(args[++i]));
            }
        }

        System.out.println("==================================================================");
        System.out.println("     sound2artnet - Sound-to-ArtNet Controller (Headless CLI)");
        System.out.println("==================================================================");
        System.out.println("  Art-Net Ziel:   " + config.getTargetIp() + ":" + ArtNetSender.DEFAULT_PORT);
        System.out.println("  Universum:      " + config.getUniverse());
        System.out.println("  Ziel-FPS:       " + config.getFps() + " Hz");
        System.out.println("  Bewegung:       " + config.getMovementPattern());
        System.out.println("  Patched Fixt.:  " + (config.getFixtures() != null ? config.getFixtures().size() : 0));
        System.out.println("==================================================================");

        AudioCaptureService audioService = new AudioCaptureService();
        ArtNetSender artNetSender = new ArtNetSender();
        Sound2LightEngine engine = new Sound2LightEngine(audioService, artNetSender);

        if (config.getFixtures() != null) {
            engine.setPatchedFixtures(config.getFixtures());
        }
        engine.setMovementPattern(config.getMovementPattern());
        engine.setDimmerMode(config.getDimmerMode());
        engine.getColorEngine().setPalette(config.getColorPalette());
        engine.getMovementGenerator().setCurrentSpeed(config.getMovementSpeed());
        engine.getMovementGenerator().setBaseAmplitude(config.getMovementSize());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nBeende sound2artnet Headless Dienst...");
            audioService.stop();
            artNetSender.stop();
            System.out.println("Tschüss!");
        }));

        try {
            artNetSender.start(config.getTargetIp(), config.getUniverse());

            AudioDeviceInfo targetDev = null;
            if (config.getAudioDevice() != null && !config.getAudioDevice().isBlank()) {
                for (AudioDeviceInfo dev : AudioCaptureService.listInputDevices()) {
                    if (dev.name().equalsIgnoreCase(config.getAudioDevice())) {
                        targetDev = dev;
                        break;
                    }
                }
            }
            audioService.start(targetDev);
            System.out.println("[✓] Sound2ArtNet läuft (" + audioService.getCurrentDeviceName() + "). Drücken Sie Strg+C zum Beenden.\n");

            long intervalMs = 1000 / Math.max(10, Math.min(44, config.getFps()));
            long lastStatTime = System.currentTimeMillis();

            while (artNetSender.isRunning() && audioService.isRunning()) {
                engine.tick();

                long now = System.currentTimeMillis();
                if (now - lastStatTime >= 3000) {
                    var analyzer = audioService.getAnalyzer();
                    System.out.printf("[Statistik] Art-Net: %.1f pkt/s (Gesamt: %d) | Audio RMS: %.1f%% | Beat: %s%n",
                            artNetSender.getPacketsPerSecond(),
                            artNetSender.getTotalPacketsSent(),
                            analyzer.getRmsLevel() * 100.0,
                            analyzer.getBeatDetector().isBeat() ? "KICK" : "-");
                    lastStatTime = now;
                }

                Thread.sleep(intervalMs);
            }
        } catch (Exception e) {
            System.err.println("Fehler im Headless-Betrieb: " + e.getMessage());
        }
    }
}

package de.exit.sound2artnet;

/**
 * Standard Einstiegspunkt für die Fat JAR Ausführung.
 * Startet standardmäßig die JavaFX GUI oder schaltet bei --cli / --headless
 * in den textbasierten Konsolenmodus um.
 */
public class Main {
    public static void main(String[] args) {
        boolean cliMode = false;
        for (String arg : args) {
            if (arg.equals("--devices") || arg.equals("--list-devices")) {
                de.exit.sound2artnet.audio.AudioCaptureService.printAllAudioDevices();
                return;
            }
            if (arg.equals("--cli") || arg.equals("--headless") || arg.equals("-c")) {
                cliMode = true;
                break;
            }
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux") && System.getenv("DISPLAY") == null && System.getenv("WAYLAND_DISPLAY") == null) {
            cliMode = true;
        }

        if (cliMode) {
            HeadlessRunner.run(args);
        } else {
            Sound2ArtNetApp.main(args);
        }
    }
}

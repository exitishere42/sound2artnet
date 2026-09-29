package de.exit.sound2artnet;

import de.exit.sound2artnet.ui.MainWindow;
import de.exit.sound2artnet.ui.MaterialTheme;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.InputStream;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * JavaFX Hauptanwendung für sound2artnet.
 */
public class Sound2ArtNetApp extends Application {
    private static final Logger LOGGER = Logger.getLogger(Sound2ArtNetApp.class.getName());
    private static HostServices hostServices;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tT] [%4$-7s] %5$s%n");
        try {
            Logger rootLogger = Logger.getLogger("");
            FileHandler fileHandler = new FileHandler("app.log", 5 * 1024 * 1024, 2, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.INFO);
            rootLogger.addHandler(fileHandler);
        } catch (Exception e) {
            System.err.println("Konnte FileHandler für app.log nicht initialisieren: " + e.getMessage());
        }
    }

    private MainWindow mainWindow;
    private Timeline tickTimeline;

    @Override
    public void start(Stage stage) {
        hostServices = getHostServices();
        LOGGER.info("==================================================================");
        LOGGER.info("Starte sound2artnet (JavaFX 21 LTS)");
        LOGGER.info("Log-Datei: " + new File("app.log").getAbsolutePath());
        LOGGER.info("==================================================================");

        mainWindow = new MainWindow();

        Scene scene = new Scene(mainWindow, 920, 760);
        scene.setFill(MaterialTheme.COLOR_BG);

        try {
            String css = getClass().getResource("/styles/material-dark.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {
            LOGGER.warning("Konnte material-dark.css nicht laden: " + e.getMessage());
        }

        // App Icon
        try (InputStream iconStream = getClass().getResourceAsStream("/icons/sound2artnet.png")) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {
        }

        stage.setTitle("sound2artnet");
        stage.setScene(scene);
        stage.setMinWidth(780);
        stage.setMinHeight(640);

        // 40 Hz Engine- und UI-Aktualisierung (alle 25 ms)
        tickTimeline = new Timeline(new KeyFrame(Duration.millis(25), e -> mainWindow.tick()));
        tickTimeline.setCycleCount(Timeline.INDEFINITE);
        tickTimeline.play();

        // F11 Shortcut für Vollbild / Maximieren
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.F11) {
                stage.setMaximized(!stage.isMaximized());
            }
        });

        stage.setOnCloseRequest(e -> {
            LOGGER.info("Beende sound2artnet...");
            if (tickTimeline != null) {
                tickTimeline.stop();
            }
            if (mainWindow != null) {
                mainWindow.shutdown();
            }
        });

        Parameters params = getParameters();
        if (params != null && (params.getRaw().contains("--maximized") || params.getRaw().contains("--fullscreen"))) {
            stage.setMaximized(true);
        }

        stage.show();
        LOGGER.info(String.format("Hauptfenster geöffnet: %.1fx%.1f", stage.getWidth(), stage.getHeight()));

        if (params != null && (params.getRaw().contains("--start") || params.getRaw().contains("--autostart"))
            || mainWindow.getConfig().isAutostart()) {
            LOGGER.info("Autostart aktiv -> Starte Sound2ArtNet Dienst...");
            mainWindow.toggleService();
        }
    }

    public static HostServices getAppHostServices() {
        return hostServices;
    }

    /**
     * Öffnet eine URL sicher im Standardbrowser des Nutzers.
     * Verwendet primär JavaFX HostServices und fällt bei Bedarf auf native OS-Kommandos zurück.
     * Verwendet bewusst KEIN java.awt.Desktop, um AWT/GTK-Konflikte und Abstürze auf Linux zu vermeiden.
     */
    public static void openWebpage(String url) {
        if (url == null || url.isBlank()) {
            return;
        }

        Thread openerThread = new Thread(() -> {
            boolean success = false;

            // 1. JavaFX HostServices versuchen
            if (hostServices != null) {
                try {
                    hostServices.showDocument(url);
                    success = true;
                } catch (Throwable t) {
                    LOGGER.warning("HostServices.showDocument fehlgeschlagen: " + t.getMessage());
                }
            }

            // 2. Nativer OS-Aufruf als Fallback (ohne AWT)
            if (!success) {
                String os = System.getProperty("os.name", "").toLowerCase();
                try {
                    if (os.contains("win")) {
                        new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", url).start();
                        success = true;
                    } else if (os.contains("mac")) {
                        new ProcessBuilder("open", url).start();
                        success = true;
                    } else {
                        // Linux / BSD: xdg-open oder gio
                        String[] commands = {"xdg-open", "gio", "sensible-browser", "x-www-browser"};
                        for (String cmd : commands) {
                            try {
                                Process p = "gio".equals(cmd)
                                        ? new ProcessBuilder("gio", "open", url).start()
                                        : new ProcessBuilder(cmd, url).start();
                                if (p.isAlive()) {
                                    success = true;
                                    break;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.warning("Natives Öffnen des Browsers fehlgeschlagen: " + t.getMessage());
                }
            }
        }, "BrowserOpenerThread");

        openerThread.setDaemon(true);
        openerThread.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

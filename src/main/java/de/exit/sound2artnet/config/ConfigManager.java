package de.exit.sound2artnet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.fixture.ChannelMapping;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lädt und speichert die Konfiguration in config.json.
 */
public final class ConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());
    private static final String CONFIG_FILE = "config.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ConfigManager() {}

    public static AppConfig loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists() && file.isFile()) {
            try {
                AppConfig cfg = MAPPER.readValue(file, AppConfig.class);
                LOGGER.info("Konfiguration erfolgreich geladen aus " + file.getAbsolutePath());
                if (cfg.getFixtures() == null) {
                    cfg.setFixtures(createDefaultPatches());
                } else {
                    for (FixturePatch patch : cfg.getFixtures()) {
                        if (patch.getProfile() != null && patch.getProfile().getChannels() != null) {
                            for (ChannelMapping cm : patch.getProfile().getChannels()) {
                                if ((cm.getFunction() == ChannelFunction.PAN || cm.getFunction() == ChannelFunction.TILT)
                                        && cm.getDefaultValue() == 0) {
                                    cm.setDefaultValue(128);
                                }
                            }
                        }
                    }
                }
                return cfg;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Fehler beim Laden von config.json, erstelle Standardkonfiguration: " + e.getMessage());
            }
        }

        AppConfig defaultCfg = new AppConfig();
        defaultCfg.setFixtures(createDefaultPatches());
        saveConfig(defaultCfg);
        return defaultCfg;
    }

    public static void saveConfig(AppConfig config) {
        if (config == null) return;
        try {
            MAPPER.writeValue(new File(CONFIG_FILE), config);
            LOGGER.fine("Konfiguration gespeichert.");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Fehler beim Speichern von config.json: " + e.getMessage(), e);
        }
    }

    private static List<FixturePatch> createDefaultPatches() {
        List<FixturePatch> list = new ArrayList<>();
        FixtureProfile spot9ch = FixtureLibrary.createGeneric9chSpot();

        // 2 standardmäßig gepatchte Moving Heads mit symmetrischem Phasenversatz
        FixturePatch mh1 = new FixturePatch("Moving Head Links", 1, spot9ch);
        mh1.setPhaseOffset(0.0);

        FixturePatch mh2 = new FixturePatch("Moving Head Rechts", 10, spot9ch.copy());
        mh2.setPhaseOffset(Math.PI);

        list.add(mh1);
        list.add(mh2);
        return list;
    }
}

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
                        upgradeLaserPatch(patch);
                    }
                    if (cfg.getPresets() != null) {
                        for (ArtNetPreset preset : cfg.getPresets()) {
                            if (preset.getFixtures() != null) {
                                for (FixturePatch patch : preset.getFixtures()) {
                                    upgradeLaserPatch(patch);
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

    private static void upgradeLaserPatch(FixturePatch patch) {
        if (patch == null || patch.getProfile() == null || patch.getProfile().getChannels() == null) return;
        boolean isMcLaser = (patch.getName() != null && patch.getName().toLowerCase().contains("laser")) ||
                (patch.getProfile().getName() != null && patch.getProfile().getName().toLowerCase().contains("laser")) ||
                (patch.getProfile().getId() != null && patch.getProfile().getId().toLowerCase().contains("laser"));
        if (isMcLaser && patch.getProfile().getChannelCount() == 19) {
            for (ChannelMapping cm : patch.getProfile().getChannels()) {
                if (cm.getOffset() == 10 && (cm.getFunction() == ChannelFunction.CONSTANT || cm.getFunction() == ChannelFunction.UNUSED)) {
                    cm.setFunction(ChannelFunction.LASER_PATTERN);
                    if (cm.getDefaultValue() == 0) cm.setDefaultValue(64);
                } else if (cm.getOffset() == 11 && (cm.getFunction() == ChannelFunction.CONSTANT || cm.getFunction() == ChannelFunction.UNUSED)) {
                    cm.setFunction(ChannelFunction.LASER_SIZE);
                    if (cm.getDefaultValue() == 0) cm.setDefaultValue(180);
                } else if (cm.getOffset() == 12 && (cm.getFunction() == ChannelFunction.CONSTANT || cm.getFunction() == ChannelFunction.UNUSED)) {
                    cm.setFunction(ChannelFunction.LASER_AMPLITUDE);
                    if (cm.getDefaultValue() == 0) cm.setDefaultValue(128);
                } else if (cm.getOffset() == 13 && (cm.getFunction() == ChannelFunction.CONSTANT || cm.getFunction() == ChannelFunction.UNUSED || cm.getFunction() == ChannelFunction.PAN_TILT_SPEED)) {
                    cm.setFunction(ChannelFunction.LASER_SPEED);
                    if (cm.getDefaultValue() == 0) cm.setDefaultValue(100);
                } else if (cm.getOffset() == 14 && (cm.getFunction() == ChannelFunction.CONSTANT || cm.getFunction() == ChannelFunction.UNUSED)) {
                    cm.setFunction(ChannelFunction.LASER_ROTATION);
                    if (cm.getDefaultValue() == 0) cm.setDefaultValue(128);
                } else if (cm.getOffset() == 17 && cm.getFunction() == ChannelFunction.FOCUS && cm.getDefaultValue() == 0) {
                    cm.setDefaultValue(180);
                } else if (cm.getOffset() == 18 && (cm.getFunction() == ChannelFunction.CONSTANT || cm.getFunction() == ChannelFunction.UNUSED)) {
                    cm.setFunction(ChannelFunction.LASER_PERSISTENCE);
                    if (cm.getDefaultValue() == 0) cm.setDefaultValue(200);
                }
            }
        }
    }
}

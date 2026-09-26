package de.exit.sound2artnet;

import de.exit.sound2artnet.engine.MovementPattern;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.util.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class I18nTest {

    @BeforeEach
    void setUp() {
        I18n.setLanguage("de");
    }

    @AfterEach
    void tearDown() {
        I18n.setLanguage("de");
    }

    @Test
    void testGermanTranslations() {
        I18n.setLanguage("de");
        assertEquals("de", I18n.getLanguage());
        assertEquals("Einstellungen", I18n.get("tab.settings"));
        assertEquals("BEREIT", I18n.get("status.ready"));
        assertEquals("Kreis", MovementPattern.CIRCLE.getDisplayName());
        assertEquals("Lautstärke", Sound2LightEngine.DimmerMode.AUDIO_LEVEL.getDisplayName());
        assertEquals("Ruhe", Sound2LightEngine.SpeedTier.IDLE.getDisplayName());
        assertEquals("Rot", ChannelFunction.RED.getDisplayName());
    }

    @Test
    void testEnglishTranslations() {
        I18n.setLanguage("en");
        assertEquals("en", I18n.getLanguage());
        assertEquals("Settings", I18n.get("tab.settings"));
        assertEquals("READY", I18n.get("status.ready"));
        assertEquals("Circle", MovementPattern.CIRCLE.getDisplayName());
        assertEquals("Audio Level", Sound2LightEngine.DimmerMode.AUDIO_LEVEL.getDisplayName());
        assertEquals("Idle", Sound2LightEngine.SpeedTier.IDLE.getDisplayName());
        assertEquals("Red", ChannelFunction.RED.getDisplayName());
    }

    @Test
    void testLanguageChangeListener() {
        AtomicBoolean changed = new AtomicBoolean(false);
        I18n.LanguageChangeListener listener = lang -> {
            if ("en".equals(lang)) {
                changed.set(true);
            }
        };
        I18n.addListener(listener);
        I18n.setLanguage("en");
        assertTrue(changed.get());
        I18n.removeListener(listener);
    }

    @Test
    void testFormattedStrings() {
        I18n.setLanguage("en");
        String formatted = I18n.get("statusbar.active", "192.168.1.10", 2, "Test Mic");
        assertTrue(formatted.contains("192.168.1.10"));
        assertTrue(formatted.contains("Univ: 2"));
        assertTrue(formatted.contains("Test Mic"));
    }
}

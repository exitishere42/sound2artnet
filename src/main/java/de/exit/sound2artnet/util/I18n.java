package de.exit.sound2artnet.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zentraler Internationalisierungsdienst für sound2artnet (Deutsch / English).
 */
public class I18n {
    public interface LanguageChangeListener {
        void onLanguageChanged(String newLang);
    }

    private static String currentLanguage = "de";
    private static final List<LanguageChangeListener> listeners = new ArrayList<>();

    private static final Map<String, String> DE = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        // --- Tabs ---
        DE.put("tab.updates", "Info & Updates");
        EN.put("tab.updates", "Info & Updates");
        DE.put("tab.settings", "Einstellungen");
        EN.put("tab.settings", "Settings");
        DE.put("tab.fixtures", "Moving Heads & Fixture Patch");
        EN.put("tab.fixtures", "Moving Heads & Fixture Patch");
        DE.put("tab.engine", "Sound-to-Light & Bewegungssteuerung");
        EN.put("tab.engine", "Sound-to-Light & Movement Engine");
        DE.put("tab.presets", "Profile");
        EN.put("tab.presets", "Profiles");

        // --- Art-Net Profile / Presets ---
        DE.put("preset.title", "Art-Net Profile & Presets");
        EN.put("preset.title", "Art-Net Profiles & Presets");
        DE.put("preset.active_banner", "Aktives Profil: %s");
        EN.put("preset.active_banner", "Active Profile: %s");
        DE.put("preset.active_badge", "AKTIV");
        EN.put("preset.active_badge", "ACTIVE");
        DE.put("preset.no_active", "Kein Profil ausgewählt");
        EN.put("preset.no_active", "No profile selected");
        DE.put("preset.custom", "Benutzerdefiniert");
        EN.put("preset.custom", "Custom");

        DE.put("preset.col.status", "Status");
        EN.put("preset.col.status", "Status");
        DE.put("preset.col.name", "Profil-Name");
        EN.put("preset.col.name", "Profile Name");
        DE.put("preset.col.ip", "Ziel-IP");
        EN.put("preset.col.ip", "Target IP");
        DE.put("preset.col.universe", "Universum");
        EN.put("preset.col.universe", "Universe");
        DE.put("preset.col.fps", "FPS");
        EN.put("preset.col.fps", "FPS");
        DE.put("preset.col.fixtures", "Scheinwerfer");
        EN.put("preset.col.fixtures", "Fixtures");
        DE.put("preset.col.modified", "Zuletzt geändert");
        EN.put("preset.col.modified", "Last Modified");

        DE.put("preset.btn.new", "Als neues Profil speichern");
        EN.put("preset.btn.new", "Save as new profile");
        DE.put("preset.btn.load", "Laden");
        EN.put("preset.btn.load", "Load");
        DE.put("preset.btn.update", "Aktualisieren");
        EN.put("preset.btn.update", "Update");
        DE.put("preset.btn.rename", "Umbenennen");
        EN.put("preset.btn.rename", "Rename");
        DE.put("preset.btn.delete", "Löschen");
        EN.put("preset.btn.delete", "Delete");

        DE.put("preset.dialog.save_title", "Aktuelle Einstellungen als Profil speichern");
        EN.put("preset.dialog.save_title", "Save current settings as profile");
        DE.put("preset.dialog.rename_title", "Profil umbenennen");
        EN.put("preset.dialog.rename_title", "Rename profile");
        DE.put("preset.dialog.name_label", "Profil-Name");
        EN.put("preset.dialog.name_label", "Profile Name");
        DE.put("preset.dialog.desc_label", "Beschreibung");
        EN.put("preset.dialog.desc_label", "Description");
        DE.put("preset.dialog.preview_title", "Profil-Vorschau:");
        EN.put("preset.dialog.preview_title", "Profile preview:");

        DE.put("preset.status.loaded", "Profil aktiviert: %s");
        EN.put("preset.status.loaded", "Profile activated: %s");
        DE.put("preset.status.saved", "Profil gespeichert: %s");
        EN.put("preset.status.saved", "Profile saved: %s");
        DE.put("preset.status.updated", "Profil mit aktuellen Werten aktualisiert: %s");
        EN.put("preset.status.updated", "Profile updated with current values: %s");
        DE.put("preset.status.renamed", "Profil umbenannt: %s");
        EN.put("preset.status.renamed", "Profile renamed: %s");
        DE.put("preset.status.deleted", "Profil gelöscht: %s");
        EN.put("preset.status.deleted", "Profile deleted: %s");

        // --- Globale Einstellungen ---
        DE.put("settings.language", "Sprache");
        EN.put("settings.language", "Language");
        DE.put("settings.autostart", "Dienst bei App-Start aktivieren");
        EN.put("settings.autostart", "Enable service on app startup");
        DE.put("settings.fps", "Standard-Framerate (FPS)");
        EN.put("settings.fps", "Default Frame Rate (FPS)");
        DE.put("settings.saved", "Einstellungen werden automatisch gespeichert.");
        EN.put("settings.saved", "Settings are saved automatically.");

        // --- Dialog Versionen & Update ---
        DE.put("dialog.installed", "INSTALLIERT");
        EN.put("dialog.installed", "INSTALLED");
        DE.put("dialog.latest", "NEUESTE VERSION");
        EN.put("dialog.latest", "LATEST VERSION");
        DE.put("dialog.checking", "Prüfe auf Updates bei GitHub...");
        EN.put("dialog.checking", "Checking GitHub for updates...");
        DE.put("dialog.checking_short", "Prüfe...");
        EN.put("dialog.checking_short", "Checking...");
        DE.put("dialog.unknown", "Unbekannt");
        EN.put("dialog.unknown", "Unknown");
        DE.put("dialog.up_to_date", "sound2artnet ist auf dem neuesten Stand.");
        EN.put("dialog.up_to_date", "sound2artnet is up to date.");
        DE.put("dialog.update_available", "Neue Version verfügbar: ");
        EN.put("dialog.update_available", "New version available: ");
        DE.put("dialog.update_now", "Jetzt aktualisieren");
        EN.put("dialog.update_now", "Update now");
        DE.put("dialog.check_again", "Erneut prüfen");
        EN.put("dialog.check_again", "Check again");
        DE.put("dialog.offline", "Update-Prüfung nicht möglich (offline).");
        EN.put("dialog.offline", "Update check unavailable (offline).");
        DE.put("dialog.starting_download", "Starte Download...");
        EN.put("dialog.starting_download", "Starting download...");
        DE.put("dialog.download_failed", "Update fehlgeschlagen: ");
        EN.put("dialog.download_failed", "Update failed: ");

        // --- Hauptfenster Status & Aktionen ---
        DE.put("status.ready", "BEREIT");
        EN.put("status.ready", "READY");
        DE.put("status.active", "AKTIV");
        EN.put("status.active", "ACTIVE");
        DE.put("btn.start", "Start");
        EN.put("btn.start", "Start");
        DE.put("btn.stop", "Stop");
        EN.put("btn.stop", "Stop");
        DE.put("btn.scan", "Scan");
        EN.put("btn.scan", "Scan");
        DE.put("btn.cancel", "Abbrechen");
        EN.put("btn.cancel", "Cancel");
        DE.put("btn.save", "Speichern");
        EN.put("btn.save", "Save");
        DE.put("btn.load", "Laden");
        EN.put("btn.load", "Load");
        DE.put("tooltip.logo", "Klicken für Versionsinformationen, Updates & globale Einstellungen");
        EN.put("tooltip.logo", "Click for version info, updates & global settings");

        // --- Metriken ---
        DE.put("metric.audio_rms", "AUDIO RMS");
        EN.put("metric.audio_rms", "AUDIO RMS");
        DE.put("metric.peak", "PEAK");
        EN.put("metric.peak", "PEAK");
        DE.put("metric.tempo", "TEMPO");
        EN.put("metric.tempo", "TEMPO");
        DE.put("metric.tier", "STUFE");
        EN.put("metric.tier", "TIER");
        DE.put("metric.artnet_out", "ART-NET OUT");
        EN.put("metric.artnet_out", "ART-NET OUT");
        DE.put("metric.sent", "GESENDET");
        EN.put("metric.sent", "SENT");
        DE.put("metric.chart_title", "ART-NET (PKT/S)");
        EN.put("metric.chart_title", "ART-NET (PKT/S)");

        // --- Speed Tiers ---
        DE.put("tier.idle", "Ruhe");
        EN.put("tier.idle", "Idle");
        DE.put("tier.slow", "Langsam");
        EN.put("tier.slow", "Slow");
        DE.put("tier.medium", "Mittel");
        EN.put("tier.medium", "Medium");
        DE.put("tier.fast", "Schnell");
        EN.put("tier.fast", "Fast");
        DE.put("tier.rave", "Extrem");
        EN.put("tier.rave", "Rave");

        // --- Schnell-Steuerung ---
        DE.put("ctrl.audio_source", "AUDIO QUELLE");
        EN.put("ctrl.audio_source", "AUDIO SOURCE");
        DE.put("ctrl.audio_source.tt_title", "Eingangs-Audioquelle");
        EN.put("ctrl.audio_source.tt_title", "Audio Input Source");
        DE.put("ctrl.audio_source.tt_desc", "Wählt das Aufnahmegerät:\n\n• Mikrofon / Line-In\n• Virtuelles Audiokabel (CABLE Output)\n• Stereo-Mixer für Desktop-Sound");
        EN.put("ctrl.audio_source.tt_desc", "Selects the audio capture device:\n\n• Microphone / Line-In\n• Virtual Audio Cable (CABLE Output)\n• Stereo Mix for desktop sound");

        DE.put("ctrl.target_ip", "ZIEL-IP");
        EN.put("ctrl.target_ip", "TARGET IP");
        DE.put("ctrl.target_ip.tt_title", "Art-Net Empfänger IP-Adresse");
        EN.put("ctrl.target_ip.tt_title", "Art-Net Receiver IP Address");
        DE.put("ctrl.target_ip.tt_desc", "Zieladresse der Art-Net Pakete:\n\n• 127.0.0.1: Lokaler Empfang (QLC+, Resolume, GrandMA onPC)\n• 255.255.255.255: Netzwerk-Broadcast\n• Spezifische IP: z. B. 192.168.1.50");
        EN.put("ctrl.target_ip.tt_desc", "Target address for Art-Net packets:\n\n• 127.0.0.1: Local loopback (QLC+, Resolume, GrandMA onPC)\n• 255.255.255.255: Network broadcast\n• Specific IP: e.g. 192.168.1.50");

        DE.put("ctrl.universe", "UNIVERSUM");
        EN.put("ctrl.universe", "UNIVERSE");
        DE.put("ctrl.universe.tt_title", "Art-Net DMX Universum");
        EN.put("ctrl.universe.tt_title", "Art-Net DMX Universe");
        DE.put("ctrl.universe.tt_desc", "Das Art-Net SubUni/Universum (0 - 15):\n\n• 0: Erstes Standard-Universum\n• Muss mit der Zielsoftware / dem DMX-Node übereinstimmen");
        EN.put("ctrl.universe.tt_desc", "The Art-Net SubUni/Universe (0 - 15):\n\n• 0: First default universe\n• Must match target software or DMX node");

        DE.put("ctrl.fps", "FPS");
        EN.put("ctrl.fps", "FPS");
        DE.put("ctrl.fps.tt_title", "Art-Net Bildrate (Hz)");
        EN.put("ctrl.fps.tt_title", "Art-Net Frame Rate (Hz)");
        DE.put("ctrl.fps.tt_desc", "Sendefrequenz der Art-Net Pakete:\n\n• 40 Hz: Empfohlener Standard für flüssige Moving-Head-Fahrten\n• 20 - 44 Hz: Konform zu DMX512-Timing");
        EN.put("ctrl.fps.tt_desc", "Transmission frequency of Art-Net packets:\n\n• 40 Hz: Recommended standard for smooth moving head motion\n• 20 - 44 Hz: Compliant with DMX512 timing");

        DE.put("ctrl.gain_agc", "GAIN / AGC");
        EN.put("ctrl.gain_agc", "GAIN / AGC");
        DE.put("ctrl.gain_agc.tt_title", "Audio-Verstärkung & Normalisierung");
        EN.put("ctrl.gain_agc.tt_title", "Audio Gain & Normalization");
        DE.put("ctrl.gain_agc.tt_desc", "Empfindlichkeitsregelung:\n\n• Gain-Regler: Manuelle Vorverstärkung (0.5x bis 5x)\n• AGC: Auto-Gain-Control passt Pegel automatisch an leise/laute Musik an");
        EN.put("ctrl.gain_agc.tt_desc", "Sensitivity control:\n\n• Gain slider: Manual pre-amplification (0.5x to 5x)\n• AGC: Auto Gain Control automatically adjusts level for soft/loud music");

        DE.put("ctrl.action", "AKTION");
        EN.put("ctrl.action", "ACTION");
        DE.put("ctrl.action.tt_title", "Sound2ArtNet Aktivierung");
        EN.put("ctrl.action.tt_title", "Sound2ArtNet Activation");
        DE.put("ctrl.action.tt_desc", "Startet oder stoppt die Audio-Erfassung und das Art-Net Senden.");
        EN.put("ctrl.action.tt_desc", "Starts or stops audio capture and Art-Net transmission.");

        // --- Fixture Patch Tabelle & Buttons ---
        DE.put("patch.col.active", "Aktiv");
        EN.put("patch.col.active", "Active");
        DE.put("patch.col.name", "Name des Scheinwerfers");
        EN.put("patch.col.name", "Fixture Name");
        DE.put("patch.col.profile", "Profil / Modell");
        EN.put("patch.col.profile", "Profile / Model");
        DE.put("patch.col.dmx", "DMX Adressen");
        EN.put("patch.col.dmx", "DMX Addresses");
        DE.put("patch.col.limits", "Pan/Tilt Invert & Limits");
        EN.put("patch.col.limits", "Pan/Tilt Invert & Limits");
        DE.put("patch.col.phase", "Phasenversatz");
        EN.put("patch.col.phase", "Phase Offset");
        DE.put("patch.limits.default", "Standard");
        EN.put("patch.limits.default", "Default");

        DE.put("btn.new_fixture", "+ Neues Fixture");
        EN.put("btn.new_fixture", "+ New Fixture");
        DE.put("btn.edit", "Bearbeiten");
        EN.put("btn.edit", "Edit");
        DE.put("btn.delete", "Löschen");
        EN.put("btn.delete", "Delete");
        DE.put("btn.qlc_import", "QLC+ Import");
        EN.put("btn.qlc_import", "QLC+ Import");

        // --- Engine Steuerung ---
        DE.put("engine.active_control", "AKTIVE STEUERUNG:");
        EN.put("engine.active_control", "ACTIVE CONTROL:");
        DE.put("engine.movement", "Bewegung");
        EN.put("engine.movement", "Movement");
        DE.put("engine.light", "Licht");
        EN.put("engine.light", "Light");
        DE.put("engine.strobe", "Strobo");
        EN.put("engine.strobe", "Strobe");
        DE.put("engine.detection_mode", "ERKENNUNGS-MODUS:");
        EN.put("engine.detection_mode", "DETECTION MODE:");
        DE.put("engine.beat_sensitivity", "BEAT-EMPFINDLICHKEIT:");
        EN.put("engine.beat_sensitivity", "BEAT SENSITIVITY:");
        DE.put("engine.movement_pattern", "BEWEGUNGSMUSTER:");
        EN.put("engine.movement_pattern", "MOVEMENT PATTERN:");
        DE.put("engine.speed", "GESCHWINDIGKEIT:");
        EN.put("engine.speed", "SPEED:");
        DE.put("engine.range", "AUSLENKUNG / WEITE:");
        EN.put("engine.range", "RANGE / AMPLITUDE:");
        DE.put("engine.dimmer_response", "DIMMER-REAKTION:");
        EN.put("engine.dimmer_response", "DIMMER RESPONSE:");
        DE.put("engine.intensity", "Intensität");
        EN.put("engine.intensity", "Intensity");
        DE.put("engine.max_level", "Max. Level");
        EN.put("engine.max_level", "Max Level");
        DE.put("engine.color_palette", "FARBPALETTE:");
        EN.put("engine.color_palette", "COLOR PALETTE:");

        // Detection Modes
        DE.put("detect.level_detect", "Level-Detect");
        EN.put("detect.level_detect", "Level Detect");
        DE.put("detect.beat_detect", "Beat-Detect");
        EN.put("detect.beat_detect", "Beat Detect");

        // Movement Patterns
        DE.put("pattern.auto_bpm", "Auto-BPM");
        EN.put("pattern.auto_bpm", "Auto-BPM");
        DE.put("pattern.circle", "Kreis");
        EN.put("pattern.circle", "Circle");
        DE.put("pattern.figure_8", "Acht");
        EN.put("pattern.figure_8", "Figure 8");
        DE.put("pattern.ballyhoo", "Ballyhoo");
        EN.put("pattern.ballyhoo", "Ballyhoo");
        DE.put("pattern.wave", "Welle");
        EN.put("pattern.wave", "Wave");
        DE.put("pattern.pan_sweep", "Pan-Sweep");
        EN.put("pattern.pan_sweep", "Pan Sweep");
        DE.put("pattern.tilt_swing", "Tilt-Swing");
        EN.put("pattern.tilt_swing", "Tilt Swing");
        DE.put("pattern.beat_bounce", "Beat-Bounce");
        EN.put("pattern.beat_bounce", "Beat Bounce");

        // Dimmer Modes
        DE.put("dimmer.audio_level", "Lautstärke");
        EN.put("dimmer.audio_level", "Audio Level");
        DE.put("dimmer.beat_pulse", "Beat-Flash");
        EN.put("dimmer.beat_pulse", "Beat Flash");
        DE.put("dimmer.always_on", "Dauerhaft an");
        EN.put("dimmer.always_on", "Always On");

        // Color Palettes
        DE.put("palette.club_neon", "Club Neon");
        EN.put("palette.club_neon", "Club Neon");
        DE.put("palette.cyberpunk", "Cyberpunk");
        EN.put("palette.cyberpunk", "Cyberpunk");
        DE.put("palette.fire_and_ice", "Fire & Ice");
        EN.put("palette.fire_and_ice", "Fire & Ice");
        DE.put("palette.rainbow", "Regenbogen");
        EN.put("palette.rainbow", "Rainbow");
        DE.put("palette.monochrome_teal", "Material Teal");
        EN.put("palette.monochrome_teal", "Material Teal");

        // --- Visualizer ---
        DE.put("visualizer.title", "DMX512 KANÄLE (1 - 512)");
        EN.put("visualizer.title", "DMX512 CHANNELS (1 - 512)");
        DE.put("visualizer.range", "Bereich:");
        EN.put("visualizer.range", "Range:");

        // --- Statusleiste ---
        DE.put("statusbar.ready", "Bereit. Klicken Sie auf Start um Audio-Erfassung und Art-Net zu aktivieren.");
        EN.put("statusbar.ready", "Ready. Click Start to activate audio capture and Art-Net transmission.");
        DE.put("statusbar.active", "Aktiv: Sende Art-Net an %s (Univ: %d) | Audio: %s");
        EN.put("statusbar.active", "Active: Sending Art-Net to %s (Univ: %d) | Audio: %s");
        DE.put("statusbar.stopped", "Gestoppt. Klicken Sie auf Start um die Übertragung fortzusetzen.");
        EN.put("statusbar.stopped", "Stopped. Click Start to resume transmission.");
        DE.put("statusbar.error_start", "Fehler beim Starten: %s");
        EN.put("statusbar.error_start", "Error starting: %s");
        DE.put("statusbar.qlc_imported", "QLC+ Fixture importiert: %s an DMX %d");
        EN.put("statusbar.qlc_imported", "QLC+ Fixture imported: %s at DMX %d");
        DE.put("statusbar.qlc_error", "Fehler beim Importieren: %s");
        EN.put("statusbar.qlc_error", "Error importing: %s");

        // --- Fixture Editor Dialog ---
        DE.put("editor.title_edit", "Fixture bearbeiten: %s");
        EN.put("editor.title_edit", "Edit Fixture: %s");
        DE.put("editor.title_new", "Neues Fixture patchen");
        EN.put("editor.title_new", "Patch New Fixture");
        DE.put("editor.name", "NAME DES GERAETS");
        EN.put("editor.name", "DEVICE NAME");
        DE.put("editor.quantity", "ANZAHL");
        EN.put("editor.quantity", "QUANTITY");
        DE.put("editor.dmx_start", "DMX START");
        EN.put("editor.dmx_start", "DMX START");
        DE.put("editor.preset_bar", "PROFIL-VORLAGE:");
        EN.put("editor.preset_bar", "PROFILE PRESET:");
        DE.put("editor.preset_prompt", "Vorlage wählen...");
        EN.put("editor.preset_prompt", "Select preset...");
        DE.put("editor.btn_add_ch", "+ Kanal");
        EN.put("editor.btn_add_ch", "+ Channel");
        DE.put("editor.btn_del_ch", "- Kanal");
        EN.put("editor.btn_del_ch", "- Channel");
        DE.put("editor.channel_mapping", "KANALBELEGUNG");
        EN.put("editor.channel_mapping", "CHANNEL MAPPING");
        DE.put("editor.col_offset", "Offset");
        EN.put("editor.col_offset", "Offset");
        DE.put("editor.col_dmx", "DMX Adr");
        EN.put("editor.col_dmx", "DMX Addr");
        DE.put("editor.col_function", "Funktion");
        EN.put("editor.col_function", "Function");
        DE.put("editor.col_default", "Standard-Wert");
        EN.put("editor.col_default", "Default Value");
        DE.put("editor.limits_header", "MOVING HEAD SCHUTZGRENZEN & BEWEGUNGSPARAMETER");
        EN.put("editor.limits_header", "MOVING HEAD LIMITS & MOTION PARAMETERS");
        DE.put("editor.invert_pan", "Pan invertieren");
        EN.put("editor.invert_pan", "Invert Pan");
        DE.put("editor.invert_tilt", "Tilt invertieren");
        EN.put("editor.invert_tilt", "Invert Tilt");
        DE.put("editor.pan_limit", "Pan Limit:");
        EN.put("editor.pan_limit", "Pan Limit:");
        DE.put("editor.tilt_limit", "Tilt Limit:");
        EN.put("editor.tilt_limit", "Tilt Limit:");
        DE.put("editor.phase_offset", "Phasenversatz:");
        EN.put("editor.phase_offset", "Phase Offset:");
        DE.put("editor.min", "Min");
        EN.put("editor.min", "Min");
        DE.put("editor.max", "Max");
        EN.put("editor.max", "Max");
        DE.put("editor.default_name", "Neuer Moving Head");
        EN.put("editor.default_name", "New Moving Head");
        DE.put("editor.profile_suffix", " Profil");
        EN.put("editor.profile_suffix", " Profile");
        DE.put("editor.qlc_chooser_title", "QLC+ Fixture Definition (*.qxf) auswählen");
        EN.put("editor.qlc_chooser_title", "Select QLC+ Fixture Definition (*.qxf)");
        DE.put("editor.qlc_error_title", "Fehler beim Laden der QLC+ Datei: %s");
        EN.put("editor.qlc_error_title", "Error loading QLC+ file: %s");

        // --- QLC+ Import Dialog ---
        DE.put("qlc.modal_title", "QLC+ Fixture Import");
        EN.put("qlc.modal_title", "QLC+ Fixture Import");
        DE.put("qlc.no_pan", "Nur Tilt (Kein Pan)");
        EN.put("qlc.no_pan", "Tilt only (No Pan)");
        DE.put("qlc.title", "QLC+ Fixture Import: %s %s");
        EN.put("qlc.title", "QLC+ Fixture Import: %s %s");
        DE.put("qlc.detected", "QLC+ DEFINITION ERKANNT");
        EN.put("qlc.detected", "QLC+ DEFINITION DETECTED");
        DE.put("qlc.name", "NAME DES SCHEINWERFERS");
        EN.put("qlc.name", "FIXTURE NAME");
        DE.put("qlc.quantity", "ANZAHL");
        EN.put("qlc.quantity", "QUANTITY");
        DE.put("qlc.start_addr", "START-ADRESSE");
        EN.put("qlc.start_addr", "START ADDRESS");
        DE.put("qlc.mode", "DMX MODUS:");
        EN.put("qlc.mode", "DMX MODE:");
        DE.put("qlc.mapping_preview", "KANAL-MAPPING VORSCHAU");
        EN.put("qlc.mapping_preview", "CHANNEL MAPPING PREVIEW");
        DE.put("qlc.col_channel", "Kanal");
        EN.put("qlc.col_channel", "Channel");
        DE.put("qlc.col_qlc_name", "QLC+ Kanalname");
        EN.put("qlc.col_qlc_name", "QLC+ Channel Name");
        DE.put("qlc.col_function", "Erkannte Funktion");
        EN.put("qlc.col_function", "Detected Function");
        DE.put("qlc.col_default", "Default");
        EN.put("qlc.col_default", "Default");
        DE.put("btn.import_patch", "Importieren & Patchen");
        EN.put("btn.import_patch", "Import & Patch");

        // --- Channel Functions ---
        DE.put("function.pan", "Pan");
        EN.put("function.pan", "Pan");
        DE.put("function.pan_fine", "Pan Fine");
        EN.put("function.pan_fine", "Pan Fine");
        DE.put("function.tilt", "Tilt");
        EN.put("function.tilt", "Tilt");
        DE.put("function.tilt_fine", "Tilt Fine");
        EN.put("function.tilt_fine", "Tilt Fine");
        DE.put("function.pan_tilt_speed", "PT Speed");
        EN.put("function.pan_tilt_speed", "PT Speed");
        DE.put("function.dimmer", "Master Dimmer");
        EN.put("function.dimmer", "Master Dimmer");
        DE.put("function.strobe", "Strobe");
        EN.put("function.strobe", "Strobe");
        DE.put("function.red", "Rot");
        EN.put("function.red", "Red");
        DE.put("function.green", "Grün");
        EN.put("function.green", "Green");
        DE.put("function.blue", "Blau");
        EN.put("function.blue", "Blue");
        DE.put("function.cyan", "Cyan");
        EN.put("function.cyan", "Cyan");
        DE.put("function.magenta", "Magenta");
        EN.put("function.magenta", "Magenta");
        DE.put("function.yellow", "Gelb");
        EN.put("function.yellow", "Yellow");
        DE.put("function.white", "Weiß");
        EN.put("function.white", "White");
        DE.put("function.amber", "Amber");
        EN.put("function.amber", "Amber");
        DE.put("function.uv", "UV");
        EN.put("function.uv", "UV");
        DE.put("function.color_wheel", "Farbrad");
        EN.put("function.color_wheel", "Color Wheel");
        DE.put("function.gobo_wheel", "Goborad");
        EN.put("function.gobo_wheel", "Gobo Wheel");
        DE.put("function.prism", "Prisma");
        EN.put("function.prism", "Prism");
        DE.put("function.focus", "Fokus");
        EN.put("function.focus", "Focus");
        DE.put("function.constant", "Fester Wert");
        EN.put("function.constant", "Constant");
        DE.put("function.unused", "Nicht belegt");
        EN.put("function.unused", "Unused");
    }

    public static synchronized String getLanguage() {
        return currentLanguage;
    }

    public static synchronized void setLanguage(String lang) {
        if (lang == null || lang.isBlank()) {
            lang = "de";
        }
        lang = lang.trim().toLowerCase();
        if (!lang.equals("en")) {
            lang = "de";
        }
        if (!lang.equals(currentLanguage)) {
            currentLanguage = lang;
            notifyListeners();
        }
    }

    public static String get(String key) {
        Map<String, String> dict = "en".equals(currentLanguage) ? EN : DE;
        String val = dict.get(key);
        if (val == null) {
            val = DE.get(key);
        }
        return val != null ? val : key;
    }

    public static String get(String key, Object... args) {
        String text = get(key);
        if (args != null && args.length > 0) {
            try {
                return String.format(text, args);
            } catch (Exception e) {
                return text;
            }
        }
        return text;
    }

    public static synchronized void addListener(LanguageChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static synchronized void removeListener(LanguageChangeListener listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        for (LanguageChangeListener listener : new ArrayList<>(listeners)) {
            try {
                listener.onLanguageChanged(currentLanguage);
            } catch (Exception ignored) {
            }
        }
    }
}

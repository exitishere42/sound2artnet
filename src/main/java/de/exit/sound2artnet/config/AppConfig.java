package de.exit.sound2artnet.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.exit.sound2artnet.audio.BeatDetector;
import de.exit.sound2artnet.engine.ColorEngine;
import de.exit.sound2artnet.engine.MovementPattern;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixturePatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistente Anwendungskonfiguration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
    private String targetIp = "127.0.0.1";
    private int universe = 0;
    private int fps = 40;
    private String audioDevice = "PC-Sound";
    private double gain = 1.0;
    private boolean agc = true;
    private BeatDetector.DetectionMode detectionMode = BeatDetector.DetectionMode.LEVEL_DETECT;
    private double beatSensitivity = 1.0;
    private boolean movementEnabled = true;
    private boolean lightEnabled = true;
    private boolean strobeEnabled = true;
    private MovementPattern movementPattern = MovementPattern.AUTO_BPM;
    private double movementSpeed = 1.0;
    private double movementSize = 0.8;
    private Sound2LightEngine.DimmerMode dimmerMode = Sound2LightEngine.DimmerMode.AUDIO_LEVEL;
    private ColorEngine.Palette colorPalette = ColorEngine.Palette.CLUB_NEON;
    private List<FixturePatch> fixtures = new ArrayList<>();
    private List<ArtNetPreset> presets = new ArrayList<>();
    private String activePresetId = null;
    private double alwaysOnIntensity = 1.0;
    private double audioLevelMax = 1.0;
    private String midiDevice = "";
    private String midiBoundType = "ANY";
    private int midiBoundChannel = -1;
    private int midiBoundData1 = -1;
    private boolean autostart = false;
    private String language = "de";

    public AppConfig() {}

    public String getTargetIp() { return targetIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp; }

    public int getUniverse() { return universe; }
    public void setUniverse(int universe) { this.universe = universe; }

    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }

    public String getAudioDevice() { return audioDevice; }
    public void setAudioDevice(String audioDevice) { this.audioDevice = audioDevice; }

    public double getGain() { return gain; }
    public void setGain(double gain) { this.gain = gain; }

    public boolean isAgc() { return agc; }
    public void setAgc(boolean agc) { this.agc = agc; }

    public BeatDetector.DetectionMode getDetectionMode() { return detectionMode; }
    public void setDetectionMode(BeatDetector.DetectionMode detectionMode) {
        this.detectionMode = detectionMode != null ? detectionMode : BeatDetector.DetectionMode.LEVEL_DETECT;
    }

    public double getBeatSensitivity() { return beatSensitivity; }
    public void setBeatSensitivity(double beatSensitivity) {
        this.beatSensitivity = Math.max(0.2, Math.min(2.0, beatSensitivity));
    }

    public boolean isMovementEnabled() { return movementEnabled; }
    public void setMovementEnabled(boolean movementEnabled) { this.movementEnabled = movementEnabled; }

    public boolean isLightEnabled() { return lightEnabled; }
    public void setLightEnabled(boolean lightEnabled) { this.lightEnabled = lightEnabled; }

    public boolean isStrobeEnabled() { return strobeEnabled; }
    public void setStrobeEnabled(boolean strobeEnabled) { this.strobeEnabled = strobeEnabled; }

    public MovementPattern getMovementPattern() { return movementPattern; }
    public void setMovementPattern(MovementPattern movementPattern) { this.movementPattern = movementPattern; }

    public double getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(double movementSpeed) { this.movementSpeed = movementSpeed; }

    public double getMovementSize() { return movementSize; }
    public void setMovementSize(double movementSize) { this.movementSize = movementSize; }

    public Sound2LightEngine.DimmerMode getDimmerMode() { return dimmerMode; }
    public void setDimmerMode(Sound2LightEngine.DimmerMode dimmerMode) { this.dimmerMode = dimmerMode; }

    public double getAlwaysOnIntensity() { return alwaysOnIntensity; }
    public void setAlwaysOnIntensity(double alwaysOnIntensity) {
        this.alwaysOnIntensity = Math.max(0.0, Math.min(1.0, alwaysOnIntensity));
    }

    public double getAudioLevelMax() { return audioLevelMax; }
    public void setAudioLevelMax(double audioLevelMax) {
        this.audioLevelMax = Math.max(0.0, Math.min(1.0, audioLevelMax));
    }

    public ColorEngine.Palette getColorPalette() { return colorPalette; }
    public void setColorPalette(ColorEngine.Palette colorPalette) { this.colorPalette = colorPalette; }

    public List<FixturePatch> getFixtures() { return fixtures; }
    public void setFixtures(List<FixturePatch> fixtures) { this.fixtures = fixtures != null ? fixtures : new ArrayList<>(); }

    public List<ArtNetPreset> getPresets() {
        if (presets == null) presets = new ArrayList<>();
        return presets;
    }
    public void setPresets(List<ArtNetPreset> presets) {
        this.presets = presets != null ? presets : new ArrayList<>();
    }

    public String getActivePresetId() { return activePresetId; }
    public void setActivePresetId(String activePresetId) { this.activePresetId = activePresetId; }

    /**
     * Stellt sicher, dass mindestens ein Standard-Profil existiert.
     */
    public void ensureDefaultPreset() {
        if (presets == null) presets = new ArrayList<>();
        if (presets.isEmpty()) {
            ArtNetPreset standard = new ArtNetPreset(
                    null,
                    "Standard",
                    "Standard-Setup",
                    this.targetIp != null ? this.targetIp : "127.0.0.1",
                    this.universe,
                    this.fps,
                    this.fixtures != null ? new ArrayList<>(this.fixtures) : new ArrayList<>(),
                    this.movementPattern,
                    this.movementSpeed,
                    this.movementSize,
                    this.dimmerMode,
                    this.alwaysOnIntensity,
                    this.audioLevelMax,
                    this.colorPalette,
                    this.movementEnabled,
                    this.lightEnabled,
                    this.strobeEnabled,
                    null
            );
            presets.add(standard);
            this.activePresetId = standard.getId();
        }
    }

    public String getMidiDevice() { return midiDevice != null ? midiDevice : ""; }
    public void setMidiDevice(String midiDevice) { this.midiDevice = midiDevice != null ? midiDevice : ""; }

    public String getMidiBoundType() { return midiBoundType != null ? midiBoundType : "ANY"; }
    public void setMidiBoundType(String midiBoundType) { this.midiBoundType = midiBoundType != null ? midiBoundType : "ANY"; }

    public int getMidiBoundChannel() { return midiBoundChannel; }
    public void setMidiBoundChannel(int midiBoundChannel) { this.midiBoundChannel = midiBoundChannel; }

    public int getMidiBoundData1() { return midiBoundData1; }
    public void setMidiBoundData1(int midiBoundData1) { this.midiBoundData1 = midiBoundData1; }

    public boolean isAutostart() { return autostart; }
    public void setAutostart(boolean autostart) { this.autostart = autostart; }

    public String getLanguage() { return language != null ? language : "de"; }
    public void setLanguage(String language) { this.language = language != null ? language : "de"; }
}

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
    private List<Integer> targetUniverses = new ArrayList<>(List.of(0));
    private int fps = 40;
    private String audioDevice = "PC-Sound";
    private double gain = 1.0;
    private boolean agc = true;
    private BeatDetector.DetectionMode detectionMode = BeatDetector.DetectionMode.LEVEL_DETECT;
    private double beatSensitivity = 1.0;
    private boolean movementEnabled = true;
    private boolean lightEnabled = true;
    private boolean strobeEnabled = true;
    private boolean goboEnabled = false;
    private MovementPattern movementPattern = MovementPattern.AUTO_BPM;
    private double movementSpeed = 1.0;
    private double movementSize = 0.8;
    private Sound2LightEngine.DimmerMode dimmerMode = Sound2LightEngine.DimmerMode.AUDIO_LEVEL;
    private ColorEngine.Palette colorPalette = ColorEngine.Palette.CLUB_NEON;
    private ColorEngine.GoboMode goboMode = ColorEngine.GoboMode.AUTO_BEAT;
    private List<FixturePatch> fixtures = new ArrayList<>();
    private List<ArtNetPreset> presets = new ArrayList<>();
    private String activePresetId = null;
    private double alwaysOnIntensity = 1.0;
    private double audioLevelMax = 1.0;
    private String midiDevice = "";
    private String midiBoundType = "ANY";
    private int midiBoundChannel = -1;
    private int midiBoundData1 = -1;
    private String midiBlackoutBoundType = "NONE";
    private int midiBlackoutBoundChannel = -1;
    private int midiBlackoutBoundData1 = -1;
    private boolean autostart = false;
    private String language = "de";

    public AppConfig() {}

    public String getTargetIp() { return targetIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp; }

    public int getUniverse() { return universe; }
    public void setUniverse(int universe) {
        this.universe = Math.max(0, Math.min(15, universe));
        if (this.targetUniverses == null || this.targetUniverses.isEmpty() || (this.targetUniverses.size() == 1 && this.targetUniverses.contains(0))) {
            this.targetUniverses = new ArrayList<>(List.of(this.universe));
        } else if (!this.targetUniverses.contains(this.universe)) {
            this.targetUniverses.add(0, this.universe);
        }
    }

    public List<Integer> getTargetUniverses() {
        if (targetUniverses == null || targetUniverses.isEmpty()) {
            return List.of(universe);
        }
        return targetUniverses;
    }

    public void setTargetUniverses(List<Integer> targetUniverses) {
        if (targetUniverses != null && !targetUniverses.isEmpty()) {
            this.targetUniverses = new ArrayList<>(targetUniverses.stream().map(u -> Math.max(0, Math.min(15, u))).distinct().toList());
            if (!this.targetUniverses.contains(this.universe)) {
                this.universe = this.targetUniverses.get(0);
            }
        } else {
            this.targetUniverses = new ArrayList<>(List.of(0));
            this.universe = 0;
        }
    }

    public String formatUniversesText() {
        List<Integer> list = getTargetUniverses();
        return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
    }

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

    public boolean isGoboEnabled() { return goboEnabled; }
    public void setGoboEnabled(boolean goboEnabled) { this.goboEnabled = goboEnabled; }

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

    public ColorEngine.GoboMode getGoboMode() {
        return goboMode != null ? goboMode : ColorEngine.GoboMode.AUTO_BEAT;
    }
    public void setGoboMode(ColorEngine.GoboMode goboMode) {
        this.goboMode = goboMode != null ? goboMode : ColorEngine.GoboMode.AUTO_BEAT;
    }

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
                    this.targetUniverses,
                    this.fps,
                    this.fixtures != null ? new ArrayList<>(this.fixtures) : new ArrayList<>(),
                    this.movementPattern,
                    this.movementSpeed,
                    this.movementSize,
                    this.dimmerMode,
                    this.alwaysOnIntensity,
                    this.audioLevelMax,
                    this.colorPalette,
                    this.goboMode,
                    this.movementEnabled,
                    this.lightEnabled,
                    this.strobeEnabled,
                    this.goboEnabled,
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

    public String getMidiBlackoutBoundType() { return midiBlackoutBoundType != null ? midiBlackoutBoundType : "NONE"; }
    public void setMidiBlackoutBoundType(String midiBlackoutBoundType) {
        this.midiBlackoutBoundType = midiBlackoutBoundType != null ? midiBlackoutBoundType : "NONE";
    }

    public int getMidiBlackoutBoundChannel() { return midiBlackoutBoundChannel; }
    public void setMidiBlackoutBoundChannel(int midiBlackoutBoundChannel) {
        this.midiBlackoutBoundChannel = midiBlackoutBoundChannel;
    }

    public int getMidiBlackoutBoundData1() { return midiBlackoutBoundData1; }
    public void setMidiBlackoutBoundData1(int midiBlackoutBoundData1) {
        this.midiBlackoutBoundData1 = midiBlackoutBoundData1;
    }

    private String midiStrobeBoundType = "NONE";
    private int midiStrobeBoundChannel = -1;
    private int midiStrobeBoundData1 = -1;

    public String getMidiStrobeBoundType() { return midiStrobeBoundType != null ? midiStrobeBoundType : "NONE"; }
    public void setMidiStrobeBoundType(String midiStrobeBoundType) {
        this.midiStrobeBoundType = midiStrobeBoundType != null ? midiStrobeBoundType : "NONE";
    }

    public int getMidiStrobeBoundChannel() { return midiStrobeBoundChannel; }
    public void setMidiStrobeBoundChannel(int midiStrobeBoundChannel) {
        this.midiStrobeBoundChannel = midiStrobeBoundChannel;
    }

    public int getMidiStrobeBoundData1() { return midiStrobeBoundData1; }
    public void setMidiStrobeBoundData1(int midiStrobeBoundData1) {
        this.midiStrobeBoundData1 = midiStrobeBoundData1;
    }

    public boolean isAutostart() { return autostart; }
    public void setAutostart(boolean autostart) { this.autostart = autostart; }

    public String getLanguage() { return language != null ? language : "de"; }
    public void setLanguage(String language) { this.language = language != null ? language : "de"; }
}

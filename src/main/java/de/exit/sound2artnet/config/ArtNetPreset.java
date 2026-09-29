package de.exit.sound2artnet.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.exit.sound2artnet.engine.ColorEngine;
import de.exit.sound2artnet.engine.MovementPattern;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixturePatch;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Modelliert ein gespeichertes Art-Net Setup / Profil (Preset) für verschiedene Venues oder Shows.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArtNetPreset {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private String id;
    private String name;
    private String description;
    private String targetIp;
    private int universe;
    private int fps;
    private List<FixturePatch> fixtures;

    // Sound-to-Light & Movement Engine Snapshots
    private MovementPattern movementPattern;
    private double movementSpeed;
    private double movementSize;
    private Sound2LightEngine.DimmerMode dimmerMode;
    private double alwaysOnIntensity = 1.0;
    private double audioLevelMax = 1.0;
    private ColorEngine.Palette colorPalette;
    private ColorEngine.GoboMode goboMode;
    private boolean movementEnabled;
    private boolean lightEnabled;
    private boolean strobeEnabled;
    private boolean goboEnabled;

    private String lastModified;

    public ArtNetPreset() {
        this.id = UUID.randomUUID().toString();
        this.name = "Preset";
        this.description = "";
        this.targetIp = "127.0.0.1";
        this.universe = 0;
        this.fps = 40;
        this.fixtures = new ArrayList<>();
        this.movementPattern = MovementPattern.AUTO_BPM;
        this.movementSpeed = 1.0;
        this.movementSize = 0.8;
        this.dimmerMode = Sound2LightEngine.DimmerMode.AUDIO_LEVEL;
        this.alwaysOnIntensity = 1.0;
        this.audioLevelMax = 1.0;
        this.colorPalette = ColorEngine.Palette.CLUB_NEON;
        this.goboMode = ColorEngine.GoboMode.AUTO_BEAT;
        this.movementEnabled = true;
        this.lightEnabled = true;
        this.strobeEnabled = true;
        this.goboEnabled = false;
        this.lastModified = LocalDateTime.now().format(FORMATTER);
    }

    @JsonCreator
    public ArtNetPreset(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("targetIp") String targetIp,
            @JsonProperty("universe") int universe,
            @JsonProperty("fps") int fps,
            @JsonProperty("fixtures") List<FixturePatch> fixtures,
            @JsonProperty("movementPattern") MovementPattern movementPattern,
            @JsonProperty("movementSpeed") double movementSpeed,
            @JsonProperty("movementSize") double movementSize,
            @JsonProperty("dimmerMode") Sound2LightEngine.DimmerMode dimmerMode,
            @JsonProperty("alwaysOnIntensity") Double alwaysOnIntensity,
            @JsonProperty("audioLevelMax") Double audioLevelMax,
            @JsonProperty("colorPalette") ColorEngine.Palette colorPalette,
            @JsonProperty("goboMode") ColorEngine.GoboMode goboMode,
            @JsonProperty("movementEnabled") Boolean movementEnabled,
            @JsonProperty("lightEnabled") Boolean lightEnabled,
            @JsonProperty("strobeEnabled") Boolean strobeEnabled,
            @JsonProperty("goboEnabled") Boolean goboEnabled,
            @JsonProperty("lastModified") String lastModified) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name != null && !name.isBlank() ? name.trim() : "Preset";
        this.description = description != null ? description.trim() : "";
        this.targetIp = targetIp != null && !targetIp.isBlank() ? targetIp.trim() : "127.0.0.1";
        this.universe = Math.max(0, Math.min(15, universe));
        this.fps = fps > 0 ? fps : 40;
        this.fixtures = fixtures != null ? fixtures : new ArrayList<>();
        this.movementPattern = movementPattern != null ? movementPattern : MovementPattern.AUTO_BPM;
        this.movementSpeed = movementSpeed > 0 ? movementSpeed : 1.0;
        this.movementSize = movementSize > 0 ? movementSize : 0.8;
        this.dimmerMode = dimmerMode != null ? dimmerMode : Sound2LightEngine.DimmerMode.AUDIO_LEVEL;
        this.alwaysOnIntensity = alwaysOnIntensity != null ? Math.max(0.0, Math.min(1.0, alwaysOnIntensity)) : 1.0;
        this.audioLevelMax = audioLevelMax != null ? Math.max(0.0, Math.min(1.0, audioLevelMax)) : 1.0;
        this.colorPalette = colorPalette != null ? colorPalette : ColorEngine.Palette.CLUB_NEON;
        this.goboMode = goboMode != null ? goboMode : ColorEngine.GoboMode.AUTO_BEAT;
        this.movementEnabled = movementEnabled != null ? movementEnabled : true;
        this.lightEnabled = lightEnabled != null ? lightEnabled : true;
        this.strobeEnabled = strobeEnabled != null ? strobeEnabled : true;
        this.goboEnabled = goboEnabled != null ? goboEnabled : false;
        this.lastModified = lastModified != null ? lastModified : LocalDateTime.now().format(FORMATTER);
    }

    public ArtNetPreset(
            String id,
            String name,
            String description,
            String targetIp,
            int universe,
            int fps,
            List<FixturePatch> fixtures,
            MovementPattern movementPattern,
            double movementSpeed,
            double movementSize,
            Sound2LightEngine.DimmerMode dimmerMode,
            Double alwaysOnIntensity,
            Double audioLevelMax,
            ColorEngine.Palette colorPalette,
            Boolean movementEnabled,
            Boolean lightEnabled,
            Boolean strobeEnabled,
            String lastModified) {
        this(id, name, description, targetIp, universe, fps, fixtures, movementPattern, movementSpeed, movementSize,
                dimmerMode, alwaysOnIntensity, audioLevelMax, colorPalette, ColorEngine.GoboMode.AUTO_BEAT,
                movementEnabled, lightEnabled, strobeEnabled, false, lastModified);
    }

    /**
     * Erstellt eine tiefe Kopie der enthaltenen Fixture-Patches.
     */
    public List<FixturePatch> copyFixtures() {
        List<FixturePatch> copies = new ArrayList<>();
        if (fixtures != null) {
            for (FixturePatch fp : fixtures) {
                copies.add(fp.copy());
            }
        }
        return copies;
    }

    public void touch() {
        this.lastModified = LocalDateTime.now().format(FORMATTER);
    }

    // Getter & Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null && !name.isBlank() ? name.trim() : "Preset"; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description != null ? description.trim() : ""; }

    public String getTargetIp() { return targetIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp != null && !targetIp.isBlank() ? targetIp.trim() : "127.0.0.1"; }

    public int getUniverse() { return universe; }
    public void setUniverse(int universe) { this.universe = Math.max(0, Math.min(15, universe)); }

    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps > 0 ? fps : 40; }

    public List<FixturePatch> getFixtures() { return fixtures; }
    public void setFixtures(List<FixturePatch> fixtures) { this.fixtures = fixtures != null ? fixtures : new ArrayList<>(); }

    public MovementPattern getMovementPattern() { return movementPattern; }
    public void setMovementPattern(MovementPattern movementPattern) { this.movementPattern = movementPattern; }

    public double getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(double movementSpeed) { this.movementSpeed = movementSpeed; }

    public double getMovementSize() { return movementSize; }
    public void setMovementSize(double movementSize) { this.movementSize = movementSize; }

    public Sound2LightEngine.DimmerMode getDimmerMode() { return dimmerMode; }
    public void setDimmerMode(Sound2LightEngine.DimmerMode dimmerMode) { this.dimmerMode = dimmerMode; }

    public double getAlwaysOnIntensity() { return alwaysOnIntensity; }
    public void setAlwaysOnIntensity(double alwaysOnIntensity) { this.alwaysOnIntensity = Math.max(0.0, Math.min(1.0, alwaysOnIntensity)); }

    public double getAudioLevelMax() { return audioLevelMax; }
    public void setAudioLevelMax(double audioLevelMax) { this.audioLevelMax = Math.max(0.0, Math.min(1.0, audioLevelMax)); }

    public ColorEngine.Palette getColorPalette() { return colorPalette; }
    public void setColorPalette(ColorEngine.Palette colorPalette) { this.colorPalette = colorPalette; }

    public ColorEngine.GoboMode getGoboMode() { return goboMode != null ? goboMode : ColorEngine.GoboMode.AUTO_BEAT; }
    public void setGoboMode(ColorEngine.GoboMode goboMode) { this.goboMode = goboMode != null ? goboMode : ColorEngine.GoboMode.AUTO_BEAT; }

    public boolean isMovementEnabled() { return movementEnabled; }
    public void setMovementEnabled(boolean movementEnabled) { this.movementEnabled = movementEnabled; }

    public boolean isLightEnabled() { return lightEnabled; }
    public void setLightEnabled(boolean lightEnabled) { this.lightEnabled = lightEnabled; }

    public boolean isStrobeEnabled() { return strobeEnabled; }
    public void setStrobeEnabled(boolean strobeEnabled) { this.strobeEnabled = strobeEnabled; }

    public boolean isGoboEnabled() { return goboEnabled; }
    public void setGoboEnabled(boolean goboEnabled) { this.goboEnabled = goboEnabled; }

    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) { this.lastModified = lastModified; }

    @Override
    public String toString() {
        return name + " (" + targetIp + ", Uni " + universe + ")";
    }
}

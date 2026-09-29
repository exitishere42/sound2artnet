package de.exit.sound2artnet.engine;

import de.exit.sound2artnet.artnet.ArtNetSender;
import de.exit.sound2artnet.audio.AudioCaptureService;
import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.fixture.ChannelMapping;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.util.I18n;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Zentrale Sound-to-Light & Moving Head Frame Engine.
 * Verknüpft Audio-Analyse, Bewegungsmuster, Farbwechsel und DMX512-Generierung.
 */
public class Sound2LightEngine {
    private static final Logger LOGGER = Logger.getLogger(Sound2LightEngine.class.getName());

    public enum DimmerMode {
        AUDIO_LEVEL("Lautstärke", "Dimmer folgt der Lautstärke/RMS"),
        BEAT_PULSE("Beat-Flash", "Kurzer Lichtblitz bei jedem Kick"),
        ALWAYS_ON("Dauerhaft an", "Konstante Helligkeit (100%)");

        private final String defaultDisplayName;
        private final String description;

        DimmerMode(String defaultDisplayName, String description) {
            this.defaultDisplayName = defaultDisplayName;
            this.description = description;
        }

        public String getDisplayName() { return I18n.get("dimmer." + name().toLowerCase()); }
        public String getDescription() { return description; }
        @Override public String toString() { return getDisplayName(); }
    }

    /**
     * BPM-basierte Geschwindigkeits- und Effekt-Stufen.
     * - SLOW (< 102 BPM, z. B. 90 BPM): Langsame, weiche Fahrten & sanfte Übergänge
     * - MEDIUM (102 - 116 BPM): Ausgewogene Club-Dynamik
     * - FAST (116 - 138 BPM, z. B. 120 BPM): Schnelle Effekte, knackige Snaps & Strobo-Akzente
     * - RAVE (>= 138 BPM, z. B. 156 BPM): Maximale Geschwindigkeit & intensive Strobo-Bursts
     */
    public enum SpeedTier {
        IDLE("Ruhe", 0.35),
        SLOW("Langsam", 0.55),
        MEDIUM("Mittel", 0.95),
        FAST("Schnell", 1.45),
        RAVE("Extrem", 2.00);

        private final String defaultDisplayName;
        private final double speedMultiplier;

        SpeedTier(String defaultDisplayName, double speedMultiplier) {
            this.defaultDisplayName = defaultDisplayName;
            this.speedMultiplier = speedMultiplier;
        }

        public String getDisplayName() { return I18n.get("tier." + name().toLowerCase()); }
        public double getSpeedMultiplier() { return speedMultiplier; }
        public boolean isFastEffectTier() { return this == FAST || this == RAVE; }
        @Override public String toString() { return getDisplayName(); }

        public static SpeedTier fromBpm(double bpm) {
            if (bpm < 40.0) return IDLE;
            if (bpm < 102.0) return SLOW;
            if (bpm < 116.0) return MEDIUM;
            if (bpm < 138.0) return FAST;
            return RAVE;
        }
    }

    private final AudioCaptureService audioCapture;
    private final ArtNetSender artNetSender;
    private final MovementGenerator movementGenerator = new MovementGenerator();
    private final ColorEngine colorEngine = new ColorEngine();

    private final List<FixturePatch> patchedFixtures = new CopyOnWriteArrayList<>();
    private final byte[] currentDmxFrame = new byte[512];

    private MovementPattern movementPattern = MovementPattern.CIRCLE;
    private DimmerMode dimmerMode = DimmerMode.AUDIO_LEVEL;
    private boolean movementEnabled = true;
    private boolean lightEnabled = true;
    private boolean strobeEnabled = true;
    private double alwaysOnIntensity = 1.0;
    private double audioLevelMax = 1.0;
    private volatile boolean lastTickBeat = false;
    private volatile double currentBpm = 0.0;
    private volatile SpeedTier currentSpeedTier = SpeedTier.IDLE;
    private Double manualBpmOverride = null;
    private Boolean manualBeatOverride = null;
    private Double manualRmsOverride = null;
    private double beatDimmer = 0.0;
    private long lastTickTime = System.nanoTime();

    public Sound2LightEngine(AudioCaptureService audioCapture, ArtNetSender artNetSender) {
        this.audioCapture = audioCapture;
        this.artNetSender = artNetSender;
    }

    /**
     * Wird zyklisch vom Timer aufgerufen (z. B. alle 25 ms bei 40 FPS).
     */
    public synchronized void tick() {
        long now = System.nanoTime();
        double deltaSeconds = (now - lastTickTime) / 1_000_000_000.0;
        if (deltaSeconds <= 0 || deltaSeconds > 0.5) {
            deltaSeconds = 0.025; // Fallback
        }
        lastTickTime = now;

        // 1. Audio-Zustand & BPM abfragen
        double rms = 0.0;
        double bass = 0.0;
        double treble = 0.0;
        boolean isBeat = false;
        double detectedBpm = 0.0;

        if (audioCapture != null && audioCapture.isRunning()) {
            var analyzer = audioCapture.getAnalyzer();
            rms = analyzer.getRmsLevel();
            double[] bands = analyzer.getBandLevels();
            bass = (bands[0] + bands[1]) * 0.5;
            treble = (bands[5] + bands[6] + bands[7]) / 3.0;
            var bd = analyzer.getBeatDetector();
            isBeat = bd.consumeBeat();
            detectedBpm = bd.getEstimatedBpm();
        }

        if (manualBpmOverride != null) {
            detectedBpm = manualBpmOverride;
        }
        if (manualBeatOverride != null) {
            isBeat = manualBeatOverride;
            manualBeatOverride = null;
        }
        if (manualRmsOverride != null) {
            rms = manualRmsOverride;
        }

        this.lastTickBeat = isBeat;
        this.currentBpm = detectedBpm;
        this.currentSpeedTier = SpeedTier.fromBpm(detectedBpm);

        // 2. Dimmer Beat Flash aktualisieren (Abklingrate passt sich an BPM-Stufe an)
        if (isBeat) {
            beatDimmer = 1.0;
        } else {
            double dimmerDecay = switch (currentSpeedTier) {
                case IDLE, SLOW -> 2.6;
                case MEDIUM -> 4.0;
                case FAST -> 5.5;
                case RAVE -> 7.5;
            };
            beatDimmer = Math.max(0.0, beatDimmer - (deltaSeconds * dimmerDecay));
        }

        // 3. Movement & Color Engine updaten (mit aktueller BPM und Geschwindigkeits-Stufe)
        if (movementEnabled) {
            movementGenerator.update(isBeat, rms, currentBpm, currentSpeedTier, deltaSeconds);
        }
        if (lightEnabled) {
            colorEngine.update(isBeat, treble, currentBpm, currentSpeedTier, strobeEnabled, deltaSeconds);
        }

        // 4. DMX512 Frame generieren
        byte[] frame = new byte[512];
        Color activeColor = colorEngine.getCurrentColor();
        boolean strobeActive = lightEnabled && strobeEnabled && colorEngine.isStrobeActive();
        boolean strobeShutterOn = colorEngine.isStrobeShutterOn();
        int strobeDmxVal = colorEngine.getStrobeDmxValue();

        int masterDimmerVal = 0;
        if (lightEnabled) {
            switch (dimmerMode) {
                case BEAT_PULSE -> masterDimmerVal = (int) Math.round(beatDimmer * 255);
                case ALWAYS_ON -> masterDimmerVal = (int) Math.round(alwaysOnIntensity * 255);
                case AUDIO_LEVEL -> {
                    double val = Math.min(1.0, rms * 3.5) * audioLevelMax;
                    masterDimmerVal = (int) Math.round(val * 255);
                }
                default -> masterDimmerVal = (int) Math.round(alwaysOnIntensity * 255);
            }
        }

        // Jedes gepatchte Fixture belegen
        for (FixturePatch patch : patchedFixtures) {
            if (!patch.isEnabled() || patch.getProfile() == null) {
                continue;
            }

            int startAddr = patch.getStartAddress(); // 1-basiert
            var profile = patch.getProfile();

            boolean hasStrobeChannel = false;
            boolean hasDimmerChannel = false;
            boolean hasColorMixing = false;
            for (ChannelMapping cm : profile.getChannels()) {
                ChannelFunction fn = cm.getFunction();
                if (fn == ChannelFunction.STROBE) hasStrobeChannel = true;
                if (fn == ChannelFunction.DIMMER) hasDimmerChannel = true;
                if (fn == ChannelFunction.RED || fn == ChannelFunction.GREEN || fn == ChannelFunction.BLUE ||
                    fn == ChannelFunction.CYAN || fn == ChannelFunction.MAGENTA || fn == ChannelFunction.YELLOW) {
                    hasColorMixing = true;
                }
            }

            // Pan / Tilt berechnen
            double[] normPos = movementGenerator.computePosition(patch, movementPattern, rms);
            int panDmx = patch.computePanDmx(normPos[0]);
            int tiltDmx = patch.computeTiltDmx(normPos[1]);

            int fixtureDimmer = masterDimmerVal;
            double rgbStrobeMod = 1.0;
            if (strobeActive) {
                if (hasStrobeChannel) {
                    // Bei aktivem Hardware-Strobe-Kanal muss Dimmer auf 255 stehen, damit der Blitz sichtbar ist
                    fixtureDimmer = 255;
                } else if (hasDimmerChannel) {
                    // Software-Strobo über den Dimmer-Kanal (20 Hz Blitz)
                    fixtureDimmer = strobeShutterOn ? 255 : 0;
                } else {
                    // Reine RGB-Fixtures ohne Dimmer-/Strobe-Kanal blitzen direkt über die Farbkanäle
                    rgbStrobeMod = strobeShutterOn ? 1.0 : 0.0;
                }
            }

            double colorDimmerMod = hasDimmerChannel ? 1.0 : (masterDimmerVal / 255.0);

            for (ChannelMapping cm : profile.getChannels()) {
                int targetDmx = startAddr - 1 + cm.getOffset();
                if (targetDmx < 0 || targetDmx >= 512) {
                    continue;
                }

                int val = cm.getDefaultValue();
                switch (cm.getFunction()) {
                    case PAN -> val = movementEnabled ? panDmx : (cm.getDefaultValue() > 0 ? cm.getDefaultValue() : patch.computePanDmx(0.5));
                    case PAN_FINE -> val = 0;
                    case TILT -> val = movementEnabled ? tiltDmx : (cm.getDefaultValue() > 0 ? cm.getDefaultValue() : patch.computeTiltDmx(0.5));
                    case TILT_FINE -> val = 0;
                    case PAN_TILT_SPEED -> val = cm.getDefaultValue();
                    case DIMMER -> val = lightEnabled ? fixtureDimmer : 0;
                    case STROBE -> val = strobeActive ? strobeDmxVal : cm.getDefaultValue();
                    case RED -> val = lightEnabled ? (int) Math.round(activeColor.getRed() * 255 * rgbStrobeMod * colorDimmerMod) : 0;
                    case GREEN -> val = lightEnabled ? (int) Math.round(activeColor.getGreen() * 255 * rgbStrobeMod * colorDimmerMod) : 0;
                    case BLUE -> val = lightEnabled ? (int) Math.round(activeColor.getBlue() * 255 * rgbStrobeMod * colorDimmerMod) : 0;
                    case CYAN -> val = lightEnabled ? (int) Math.round((1.0 - activeColor.getRed()) * 255) : 0;
                    case MAGENTA -> val = lightEnabled ? (int) Math.round((1.0 - activeColor.getGreen()) * 255) : 0;
                    case YELLOW -> val = lightEnabled ? (int) Math.round((1.0 - activeColor.getBlue()) * 255) : 0;
                    case WHITE, AMBER, UV -> val = lightEnabled ? (int) Math.round(cm.getDefaultValue() * colorDimmerMod) : 0;
                    case COLOR_WHEEL -> val = (lightEnabled && !hasColorMixing) ? colorEngine.getColorWheelIndex(activeColor) : cm.getDefaultValue();
                    case GOBO_WHEEL, PRISM, FOCUS, CONSTANT -> val = cm.getDefaultValue();
                    case UNUSED -> val = 0;
                }

                frame[targetDmx] = (byte) (Math.max(0, Math.min(255, val)) & 0xFF);
            }
        }

        // Aktuellen Frame zwischenspeichern
        System.arraycopy(frame, 0, currentDmxFrame, 0, 512);

        // 5. Per Art-Net aussenden
        if (artNetSender != null && artNetSender.isRunning()) {
            artNetSender.sendDmx(frame);
        }
    }

    public synchronized byte[] getCurrentDmxFrame() {
        return Arrays.copyOf(currentDmxFrame, currentDmxFrame.length);
    }

    public List<FixturePatch> getPatchedFixtures() {
        return patchedFixtures;
    }

    public void setPatchedFixtures(List<FixturePatch> list) {
        patchedFixtures.clear();
        if (list != null) {
            patchedFixtures.addAll(list);
        }
    }

    public MovementGenerator getMovementGenerator() {
        return movementGenerator;
    }

    public ColorEngine getColorEngine() {
        return colorEngine;
    }

    public MovementPattern getMovementPattern() {
        return movementPattern;
    }

    public void setMovementPattern(MovementPattern movementPattern) {
        this.movementPattern = movementPattern != null ? movementPattern : MovementPattern.CIRCLE;
    }

    public DimmerMode getDimmerMode() {
        return dimmerMode;
    }

    public void setDimmerMode(DimmerMode dimmerMode) {
        this.dimmerMode = dimmerMode != null ? dimmerMode : DimmerMode.AUDIO_LEVEL;
    }

    public boolean isMovementEnabled() {
        return movementEnabled;
    }

    public void setMovementEnabled(boolean movementEnabled) {
        this.movementEnabled = movementEnabled;
    }

    public boolean isLightEnabled() {
        return lightEnabled;
    }

    public void setLightEnabled(boolean lightEnabled) {
        this.lightEnabled = lightEnabled;
    }

    public boolean isStrobeEnabled() {
        return strobeEnabled;
    }

    public void setStrobeEnabled(boolean strobeEnabled) {
        this.strobeEnabled = strobeEnabled;
    }

    public boolean isLastTickBeat() {
        return lastTickBeat;
    }

    public double getCurrentBpm() {
        return currentBpm;
    }

    public SpeedTier getCurrentSpeedTier() {
        return currentSpeedTier;
    }

    public void setSimulatedBpmAndBeat(Double bpm, Boolean beat) {
        this.manualBpmOverride = bpm;
        this.manualBeatOverride = beat;
    }

    public void setSimulatedRms(Double rms) {
        this.manualRmsOverride = rms;
    }

    public double getAlwaysOnIntensity() {
        return alwaysOnIntensity;
    }

    public void setAlwaysOnIntensity(double alwaysOnIntensity) {
        this.alwaysOnIntensity = Math.max(0.0, Math.min(1.0, alwaysOnIntensity));
    }

    public double getAudioLevelMax() {
        return audioLevelMax;
    }

    public void setAudioLevelMax(double audioLevelMax) {
        this.audioLevelMax = Math.max(0.0, Math.min(1.0, audioLevelMax));
    }
}

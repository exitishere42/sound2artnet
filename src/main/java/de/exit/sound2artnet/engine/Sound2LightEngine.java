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
    private volatile boolean manualStrobe = false;
    private boolean goboEnabled = false;
    private volatile boolean blackout = false;
    private double alwaysOnIntensity = 1.0;
    private double audioLevelMax = 1.0;
    private volatile boolean lastTickBeat = false;
    private volatile double currentBpm = 0.0;
    private volatile SpeedTier currentSpeedTier = SpeedTier.IDLE;
    private Double manualBpmOverride = null;
    private Boolean manualBeatOverride = null;
    private Double manualRmsOverride = null;
    private double beatDimmer = 0.0;
    private boolean wasBeatHeld = false;
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
        boolean isBeatHeld = false;
        double detectedBpm = 0.0;
        boolean isManualMode = false;

        if (audioCapture != null) {
            var analyzer = audioCapture.getAnalyzer();
            var bd = analyzer.getBeatDetector();
            isManualMode = (bd.getDetectionMode() == de.exit.sound2artnet.audio.BeatDetector.DetectionMode.MANUAL);
            isBeatHeld = bd.isManualBeatHeld();
            if (audioCapture.isRunning()) {
                rms = analyzer.getRmsLevel();
                double[] bands = analyzer.getBandLevels();
                bass = (bands[0] + bands[1]) * 0.5;
                treble = (bands[5] + bands[6] + bands[7]) / 3.0;
                isBeat = bd.consumeBeat();
                detectedBpm = bd.getEstimatedBpm();
            } else if (isManualMode) {
                isBeat = bd.consumeBeat();
                detectedBpm = bd.getEstimatedBpm();
            }
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

        this.lastTickBeat = isBeat || isBeatHeld;
        this.currentBpm = detectedBpm;
        this.currentSpeedTier = SpeedTier.fromBpm(detectedBpm);

        // 2. Dimmer Beat Flash aktualisieren (hält exakt solange die MIDI-/Beat-Taste gedrückt bleibt)
        if (isBeat || isBeatHeld) {
            beatDimmer = 1.0;
        } else if (wasBeatHeld) {
            // Sobald die gehaltene MIDI-/Beat-Taste losgelassen wird, endet der gehaltene Beat sofort
            beatDimmer = 0.0;
            colorEngine.stopStrobeBurst();
        } else {
            double dimmerDecay = switch (currentSpeedTier) {
                case IDLE, SLOW -> 2.6;
                case MEDIUM -> 3.8;
                case FAST -> 5.0;
                case RAVE -> 6.2;
            };
            beatDimmer = Math.max(0.0, beatDimmer - (deltaSeconds * dimmerDecay));
        }
        wasBeatHeld = isBeatHeld;

        // 3. Movement & Color Engine updaten (mit aktueller BPM und Geschwindigkeits-Stufe)
        double effectiveRms = (isManualMode && (rms < 0.02 || isBeatHeld)) ? Math.max(rms, beatDimmer * 0.65) : rms;
        if (movementEnabled) {
            movementGenerator.update(isBeat, effectiveRms, currentBpm, currentSpeedTier, deltaSeconds);
        }
        if (lightEnabled) {
            colorEngine.update(isBeat, isBeatHeld, treble, currentBpm, currentSpeedTier, strobeEnabled, deltaSeconds);
        }

        // 4. DMX512 Frame generieren
        byte[] frame = new byte[512];
        boolean effectiveLight = lightEnabled && !blackout;
        Color activeColor = colorEngine.getCurrentColor();
        boolean manualStrobeActive = effectiveLight && manualStrobe;
        boolean autoStrobeActive = effectiveLight && strobeEnabled && colorEngine.isStrobeActive();
        boolean strobeActive = manualStrobeActive || autoStrobeActive;
        boolean strobeShutterOn = manualStrobeActive ? (System.currentTimeMillis() % 60 < 30) : colorEngine.isStrobeShutterOn();
        int strobeDmxVal = manualStrobeActive ? 255 : colorEngine.getStrobeDmxValue();

        int masterDimmerVal = 0;
        if (effectiveLight) {
            switch (dimmerMode) {
                case BEAT_PULSE -> masterDimmerVal = (int) Math.round(beatDimmer * 255);
                case ALWAYS_ON -> masterDimmerVal = (int) Math.round(alwaysOnIntensity * 255);
                case AUDIO_LEVEL -> {
                    double baseLevel = (isBeatHeld || (isManualMode && rms < 0.02))
                            ? beatDimmer
                            : Math.min(1.0, rms * 3.5);
                    double val = baseLevel * audioLevelMax;
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
            int totalGoboWheels = 0;
            int lastGoboWheelOffset = -10;
            for (ChannelMapping cm : profile.getChannels()) {
                ChannelFunction fn = cm.getFunction();
                if (fn == ChannelFunction.STROBE) hasStrobeChannel = true;
                if (fn == ChannelFunction.DIMMER) hasDimmerChannel = true;
                if (fn == ChannelFunction.RED || fn == ChannelFunction.GREEN || fn == ChannelFunction.BLUE ||
                    fn == ChannelFunction.CYAN || fn == ChannelFunction.MAGENTA || fn == ChannelFunction.YELLOW) {
                    hasColorMixing = true;
                }
                if (fn == ChannelFunction.GOBO_WHEEL) {
                    totalGoboWheels++;
                    lastGoboWheelOffset = cm.getOffset();
                }
            }

            // Pan / Tilt (inkl. 16-Bit Fine) berechnen
            double[] normPos = movementGenerator.computePosition(patch, movementPattern, rms);
            int panDmx = patch.computePanDmx(normPos[0]);
            int panFineDmx = patch.computePanFineDmx(normPos[0]);
            int tiltDmx = patch.computeTiltDmx(normPos[1]);
            int tiltFineDmx = patch.computeTiltFineDmx(normPos[1]);

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
            int goboWheelCounter = 0;

            for (ChannelMapping cm : profile.getChannels()) {
                int targetDmx = startAddr - 1 + cm.getOffset();
                if (targetDmx < 0 || targetDmx >= 512) {
                    continue;
                }

                int val = cm.getDefaultValue();
                switch (cm.getFunction()) {
                    case PAN -> val = movementEnabled ? panDmx : (cm.getDefaultValue() > 0 ? cm.getDefaultValue() : patch.computePanDmx(0.5));
                    case PAN_FINE -> val = movementEnabled ? panFineDmx : 0;
                    case TILT -> val = movementEnabled ? tiltDmx : (cm.getDefaultValue() > 0 ? cm.getDefaultValue() : patch.computeTiltDmx(0.5));
                    case TILT_FINE -> val = movementEnabled ? tiltFineDmx : 0;
                    case PAN_TILT_SPEED -> val = cm.getDefaultValue();
                    case DIMMER -> val = effectiveLight ? fixtureDimmer : 0;
                    case STROBE -> val = blackout ? 0 : (strobeActive ? strobeDmxVal : cm.getDefaultValue());
                    case RED -> val = effectiveLight ? (int) Math.round(activeColor.getRed() * 255 * rgbStrobeMod * colorDimmerMod) : 0;
                    case GREEN -> val = effectiveLight ? (int) Math.round(activeColor.getGreen() * 255 * rgbStrobeMod * colorDimmerMod) : 0;
                    case BLUE -> val = effectiveLight ? (int) Math.round(activeColor.getBlue() * 255 * rgbStrobeMod * colorDimmerMod) : 0;
                    case CYAN -> val = effectiveLight ? (int) Math.round((1.0 - activeColor.getRed()) * 255) : 0;
                    case MAGENTA -> val = effectiveLight ? (int) Math.round((1.0 - activeColor.getGreen()) * 255) : 0;
                    case YELLOW -> val = effectiveLight ? (int) Math.round((1.0 - activeColor.getBlue()) * 255) : 0;
                    case WHITE, AMBER, UV -> val = effectiveLight ? (int) Math.round(cm.getDefaultValue() * colorDimmerMod) : 0;
                    case COLOR_WHEEL -> val = (effectiveLight && !hasColorMixing) ? colorEngine.getColorWheelIndex(activeColor) : cm.getDefaultValue();
                    case GOBO_WHEEL -> {
                        int wheelIdx = goboWheelCounter++;
                        if (effectiveLight && goboEnabled) {
                            val = (wheelIdx == 0) ? colorEngine.getGoboWheel1Dmx(totalGoboWheels) : colorEngine.getGoboWheel2Dmx();
                        } else {
                            val = cm.getDefaultValue();
                        }
                    }
                    case PRISM, FOCUS -> val = cm.getDefaultValue();
                    case CONSTANT -> {
                        // Gobo-Rotationskanal direkt hinter dem rotierenden Goborad (z. B. Kanal 20 beim ROBE MegaPointe mit Default=128)
                        if (effectiveLight && goboEnabled && totalGoboWheels >= 1 &&
                            cm.getOffset() == lastGoboWheelOffset + 1 && cm.getDefaultValue() == 128) {
                            val = colorEngine.getGoboRotationDmx();
                        } else {
                            val = cm.getDefaultValue();
                        }
                    }
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

    public boolean isManualStrobe() {
        return manualStrobe;
    }

    public void setManualStrobe(boolean manualStrobe) {
        this.manualStrobe = manualStrobe;
    }

    public boolean isGoboEnabled() {
        return goboEnabled;
    }

    public void setGoboEnabled(boolean goboEnabled) {
        this.goboEnabled = goboEnabled;
    }

    public boolean isBlackout() {
        return blackout;
    }

    public void setBlackout(boolean blackout) {
        this.blackout = blackout;
    }

    public boolean toggleBlackout() {
        this.blackout = !this.blackout;
        return this.blackout;
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

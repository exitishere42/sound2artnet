package de.exit.sound2artnet.engine;

import de.exit.sound2artnet.fixture.FixturePatch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Erzeugt flüssige, musikalisch gekoppelte Koordinaten (Pan X, Tilt Y)
 * für Moving Heads im normalisierten Bereich [0.0, 1.0].
 *
 * Kombiniert volle 170-BPM-Dynamik (Reaktion auf jeden Beat) mit einer
 * gleichmäßigen S-Kurven-Fahrt über das gesamte Beat-Intervall (statt 1-Frame-Sprung)
 * sowie einem 1.2s-Crossfade zwischen verschiedenen Bewegungseffekten.
 */
public class MovementGenerator {
    /** Dauer des weichen Crossfades beim Wechsel zwischen zwei Bewegungseffekten in Sekunden */
    private static final double EFFECT_TRANSITION_SECONDS = 1.2;

    private double phase = 0.0;
    private double currentSpeed = 1.0;
    private double baseAmplitude = 0.8;
    private double audioReactivity = 0.5;

    private int bounceIndex = 0;
    private int beatCount = 0;
    private double beatPulse = 0.0;
    private double currentBpm = 0.0;
    private Sound2LightEngine.SpeedTier currentTier = Sound2LightEngine.SpeedTier.MEDIUM;
    private double lastDeltaSeconds = 0.025;

    // Weiche Richtungsumkehr ohne harten Ruck
    private double smoothedDirection = 1.0;

    // Effekt-zu-Effekt-Crossfade (0.0 = alter Effekt, 1.0 = neuer Effekt)
    private MovementPattern activeResolvedPattern = null;
    private MovementPattern previousResolvedPattern = null;
    private double patternTransitionProgress = 1.0;

    // Weiche Symmetrie-Überblendung für Fixtures mit Phasenversatz
    private double mirrorBlend = 0.0;

    private static final double[][] BOUNCE_POINTS = {
        {0.20, 0.30}, {0.80, 0.70}, {0.30, 0.80}, {0.70, 0.20},
        {0.50, 0.50}, {0.15, 0.45}, {0.85, 0.55}, {0.25, 0.20},
        {0.75, 0.80}, {0.50, 0.18}, {0.50, 0.82}, {0.35, 0.50}
    };
    private double startBounceX = 0.5;
    private double startBounceY = 0.5;
    private double targetBounceX = BOUNCE_POINTS[0][0];
    private double targetBounceY = BOUNCE_POINTS[0][1];
    private double currentBounceX = 0.5;
    private double currentBounceY = 0.5;
    private double bounceProgress = 1.0;
    private double currentBounceDurationSec = 0.38;

    // Leichte Mikro-Glättung pro Fixture gegen 1-Frame-Spitzen ohne Tempoverlust
    private final Map<String, double[]> smoothedFixturePositions = new ConcurrentHashMap<>();

    public synchronized void update(boolean isBeat, double audioEnergy, double deltaSeconds) {
        update(isBeat, audioEnergy, currentBpm, currentTier, deltaSeconds);
    }

    public synchronized void update(boolean isBeat, double audioEnergy, double bpm,
                                    Sound2LightEngine.SpeedTier tier, double deltaSeconds) {
        this.currentBpm = bpm;
        this.currentTier = (tier != null) ? tier : Sound2LightEngine.SpeedTier.MEDIUM;
        this.lastDeltaSeconds = Math.max(0.005, Math.min(0.25, deltaSeconds));

        double tierMult = this.currentTier.getSpeedMultiplier();
        double bpmRatio = (bpm >= 40.0) ? (bpm / 108.0) : tierMult;
        double tempoFactor = (tierMult * 0.70) + (bpmRatio * 0.30);

        double beatIntervalSec = (bpm >= 40.0) ? (60.0 / bpm) : 0.50;

        if (isBeat) {
            beatCount++;
            beatPulse = 1.0;

            // Auf JEDEN Beat ein neues Ziel ansteuern, aber die Fahrt über das gesamte Beat-Intervall
            // (~330 ms bei 170 BPM = 13-14 Frames) als saubere S-Kurve ausführen statt in 2 Frames zu springen!
            startBounceX = currentBounceX;
            startBounceY = currentBounceY;
            int step = (this.currentTier == Sound2LightEngine.SpeedTier.RAVE && beatCount % 4 == 0) ? 2 : 1;
            bounceIndex = (bounceIndex + step) % BOUNCE_POINTS.length;
            targetBounceX = BOUNCE_POINTS[bounceIndex][0];
            targetBounceY = BOUNCE_POINTS[bounceIndex][1];
            bounceProgress = 0.0;

            // Übergang nutzt ~92 % der Zeit zwischen zwei Beats (bei 170 BPM ~0.32s, bei 120 BPM ~0.46s)
            double rawDur = beatIntervalSec * 0.92;
            currentBounceDurationSec = Math.max(0.24, Math.min(0.75, rawDur / Math.max(0.4, currentSpeed)));
        } else {
            double pulseDecay = switch (this.currentTier) {
                case IDLE, SLOW -> 2.5;
                case MEDIUM -> 4.0;
                case FAST -> 5.5;
                case RAVE -> 7.0;
            };
            beatPulse = Math.max(0.0, beatPulse - (this.lastDeltaSeconds * pulseDecay));
        }

        // 1. Schnelle, aber kontinuierliche S-Kurven-Fahrt von Punkt zu Punkt über alle Frames des Beats
        if (bounceProgress < 1.0) {
            bounceProgress = Math.min(1.0, bounceProgress + (this.lastDeltaSeconds / currentBounceDurationSec));
        }
        double ease = smoothstep(bounceProgress);
        currentBounceX = startBounceX + (targetBounceX - startBounceX) * ease;
        currentBounceY = startBounceY + (targetBounceY - startBounceY) * ease;

        // 2. Effekt-zu-Effekt-Crossfade (1.2 Sekunden)
        if (patternTransitionProgress < 1.0) {
            patternTransitionProgress = Math.min(1.0,
                    patternTransitionProgress + (this.lastDeltaSeconds / EFFECT_TRANSITION_SECONDS));
        }

        // 3. Weiche Symmetrie-Überblendung über 1.0 Sekunde (statt hartem Teleport)
        double targetMirror = ((beatCount / 8) % 2 == 0) ? 0.0 : 1.0;
        if (mirrorBlend < targetMirror) {
            mirrorBlend = Math.min(1.0, mirrorBlend + (this.lastDeltaSeconds / 1.0));
        } else if (mirrorBlend > targetMirror) {
            mirrorBlend = Math.max(0.0, mirrorBlend - (this.lastDeltaSeconds / 1.0));
        }

        // 4. Dynamischer Phasenfortschritt passend zur BPM mit weicher Richtungsumkehr
        double beatBoost = this.currentTier.isFastEffectTier() ? (beatPulse * 0.38) : (beatPulse * 0.15);
        double effectiveSpeed = currentSpeed * tempoFactor * (1.0 + (audioEnergy * audioReactivity * 0.55) + beatBoost);

        double targetDirection = ((beatCount / 16) % 2 == 0) ? 1.0 : -1.0;
        smoothedDirection += (targetDirection - smoothedDirection) * Math.min(1.0, this.lastDeltaSeconds * 3.0);

        phase += smoothedDirection * effectiveSpeed * this.lastDeltaSeconds * Math.PI * 0.82;
        if (Math.abs(phase) > 2.0 * Math.PI * 1000.0) {
            phase %= (2.0 * Math.PI);
        }
    }

    /**
     * Ermittelt das aktuell aktive Bewegungsmuster (löst AUTO_BPM anhand der BPM-Stufe und Taktphrase auf).
     */
    public MovementPattern resolveActivePattern(MovementPattern configuredPattern) {
        if (configuredPattern != null && configuredPattern != MovementPattern.AUTO_BPM) {
            return configuredPattern;
        }
        int beatsPerPhrase = currentTier.isFastEffectTier() ? 16 : 8;
        int phrase = (beatCount / beatsPerPhrase);
        return switch (currentTier) {
            case IDLE -> MovementPattern.WAVE;
            case SLOW -> switch (phrase % 3) {
                case 0 -> MovementPattern.WAVE;
                case 1 -> MovementPattern.CIRCLE;
                default -> MovementPattern.PAN_SWEEP;
            };
            case MEDIUM -> switch (phrase % 3) {
                case 0 -> MovementPattern.CIRCLE;
                case 1 -> MovementPattern.FIGURE_8;
                default -> MovementPattern.TILT_SWING;
            };
            case FAST -> switch (phrase % 3) {
                case 0 -> MovementPattern.BALLYHOO;
                case 1 -> MovementPattern.BEAT_BOUNCE;
                default -> MovementPattern.FIGURE_8;
            };
            case RAVE -> switch (phrase % 3) {
                case 0 -> MovementPattern.BEAT_BOUNCE;
                case 1 -> MovementPattern.BALLYHOO;
                default -> MovementPattern.FIGURE_8;
            };
        };
    }

    /**
     * Berechnet die normalisierten Koordinaten (0.0 bis 1.0) für ein bestimmtes Fixture
     * inklusive weichem 1.2s-Crossfade bei Effektwechseln.
     */
    public synchronized double[] computePosition(FixturePatch patch, MovementPattern pattern, double audioEnergy) {
        MovementPattern targetPattern = resolveActivePattern(pattern);
        if (activeResolvedPattern == null) {
            activeResolvedPattern = targetPattern;
            previousResolvedPattern = targetPattern;
            patternTransitionProgress = 1.0;
        } else if (targetPattern != activeResolvedPattern) {
            previousResolvedPattern = activeResolvedPattern;
            activeResolvedPattern = targetPattern;
            patternTransitionProgress = 0.0;
        }

        double phaseOffset = (patch != null) ? patch.getPhaseOffset() : 0.0;
        double p = phase + phaseOffset;

        double tierAmpBoost = currentTier.isFastEffectTier() ? (beatPulse * 0.16) : (beatPulse * 0.06);
        double amp = Math.min(1.0, baseAmplitude * (0.65 + (audioEnergy * audioReactivity * 0.6) + tierAmpBoost));

        double[] currentPos = computeSinglePatternPosition(activeResolvedPattern, p, phaseOffset, amp);

        double rawPan = currentPos[0];
        double rawTilt = currentPos[1];

        // Weicher Crossfade (1.2s S-Kurve) zwischen vorherigem und neuem Effekt
        if (patternTransitionProgress < 1.0 && previousResolvedPattern != null && previousResolvedPattern != activeResolvedPattern) {
            double[] prevPos = computeSinglePatternPosition(previousResolvedPattern, p, phaseOffset, amp);
            double blend = smoothstep(patternTransitionProgress);
            rawPan = prevPos[0] * (1.0 - blend) + currentPos[0] * blend;
            rawTilt = prevPos[1] * (1.0 - blend) + currentPos[1] * blend;
        }

        // Schnelle Folge-Glättung pro Fixture (verhindert 1-Frame-Spitzen, hält aber 100 % das 170-BPM-Tempo)
        String fixtureKey = (patch != null && patch.getId() != null) ? patch.getId() : "default";
        double[] smoothed = smoothedFixturePositions.get(fixtureKey);
        if (smoothed == null) {
            smoothed = new double[]{rawPan, rawTilt};
            smoothedFixturePositions.put(fixtureKey, smoothed);
        } else {
            double smoothAlpha = Math.min(1.0, lastDeltaSeconds * 22.0);
            smoothed[0] += (rawPan - smoothed[0]) * smoothAlpha;
            smoothed[1] += (rawTilt - smoothed[1]) * smoothAlpha;
        }

        return new double[]{
            Math.max(0.0, Math.min(1.0, smoothed[0])),
            Math.max(0.0, Math.min(1.0, smoothed[1]))
        };
    }

    private double[] computeSinglePatternPosition(MovementPattern pat, double p, double phaseOffset, double amp) {
        double x = 0.0;
        double y = 0.0;

        switch (pat) {
            case CIRCLE -> {
                x = Math.sin(p);
                y = Math.cos(p);
            }
            case FIGURE_8 -> {
                x = Math.sin(p);
                y = Math.sin(2.0 * p) * 0.65;
            }
            case BALLYHOO -> {
                x = Math.sin(1.3 * p) * 0.7 + Math.sin(2.7 * p) * 0.3;
                y = Math.cos(1.7 * p) * 0.6 + Math.cos(0.9 * p) * 0.4;
            }
            case WAVE -> {
                x = Math.sin(p * 0.7);
                y = Math.sin(p * 1.4 + Math.PI / 4.0);
            }
            case PAN_SWEEP -> {
                x = Math.sin(p);
                y = Math.sin(p * 2.0) * (currentTier.isFastEffectTier() ? 0.25 : 0.05);
            }
            case TILT_SWING -> {
                x = Math.cos(p * 0.5) * (currentTier.isFastEffectTier() ? 0.25 : 0.05);
                y = Math.sin(p);
            }
            case BEAT_BOUNCE, AUTO_BPM -> {
                double bx = currentBounceX;
                double by = currentBounceY;

                // Weiche Symmetrie-Überblendung für Fixtures mit Phasenversatz
                if (Math.abs(phaseOffset) > 0.05) {
                    double m = smoothstep(mirrorBlend);
                    double panMirroredX = 1.0 - bx;
                    double tiltMirroredY = 1.0 - by;
                    bx = panMirroredX * (1.0 - m) + bx * m;
                    by = by * (1.0 - m) + tiltMirroredY * m;
                }

                // Subtiler organischer Schwung
                double idleDriftX = Math.sin(p * 0.4) * 0.05;
                double idleDriftY = Math.cos(p * 0.6) * 0.05;

                double pan = 0.5 + ((bx - 0.5) * baseAmplitude) + (idleDriftX * baseAmplitude);
                double tilt = 0.5 + ((by - 0.5) * baseAmplitude) + (idleDriftY * baseAmplitude);

                return new double[]{
                    Math.max(0.0, Math.min(1.0, pan)),
                    Math.max(0.0, Math.min(1.0, tilt))
                };
            }
        }

        double pan = 0.5 + (x * 0.5 * amp);
        double tilt = 0.5 + (y * 0.5 * amp);

        return new double[]{
            Math.max(0.0, Math.min(1.0, pan)),
            Math.max(0.0, Math.min(1.0, tilt))
        };
    }

    /**
     * Kubische S-Kurve (Smoothstep): 0.0 -> 1.0 für schnelles, aber ruckfreies Anfahren und Abbremsen.
     */
    private static double smoothstep(double t) {
        double x = Math.max(0.0, Math.min(1.0, t));
        return x * x * (3.0 - 2.0 * x);
    }

    public double getPatternTransitionProgress() {
        return patternTransitionProgress;
    }

    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(double currentSpeed) {
        this.currentSpeed = Math.max(0.1, Math.min(5.0, currentSpeed));
    }

    public double getBaseAmplitude() {
        return baseAmplitude;
    }

    public void setBaseAmplitude(double baseAmplitude) {
        this.baseAmplitude = Math.max(0.1, Math.min(1.0, baseAmplitude));
    }

    public double getAudioReactivity() {
        return audioReactivity;
    }

    public void setAudioReactivity(double audioReactivity) {
        this.audioReactivity = Math.max(0.0, Math.min(1.0, audioReactivity));
    }
}

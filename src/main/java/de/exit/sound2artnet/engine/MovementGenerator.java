package de.exit.sound2artnet.engine;

import de.exit.sound2artnet.fixture.FixturePatch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Erzeugt flüssige, musikalisch gekoppelte Koordinaten (Pan X, Tilt Y)
 * für Moving Heads im normalisierten Bereich [0.0, 1.0].
 *
 * Besondere Merkmale für hohe Tempi (z. B. 170 BPM):
 * - Großer, weicher Crossfade (3.5 s S-Kurve) beim Wechsel zwischen Effekten
 * - Kontinuierliche Catmull-Rom-/Cosinus-Interpolation im Beat-Bounce über ganze Takte (statt hektischem Zucken)
 * - Kritisch gedämpfter 2.-Ordnung-Trägheitsfilter (Position + Geschwindigkeit) pro Fixture für 100 % ruckfreie Kurven
 */
public class MovementGenerator {
    /** Große, weiche Überblenddauer zwischen zwei Bewegungseffekten in Sekunden */
    private static final double EFFECT_TRANSITION_SECONDS = 3.5;

    private double phase = 0.0;
    private double currentSpeed = 1.0;
    private double baseAmplitude = 0.8;
    private double audioReactivity = 0.5;

    private int bounceIndex = 0;
    private int beatCount = 0;
    private double beatPulse = 0.0;
    private double smoothedEnergy = 0.0;
    private double currentBpm = 0.0;
    private Sound2LightEngine.SpeedTier currentTier = Sound2LightEngine.SpeedTier.MEDIUM;
    private double lastDeltaSeconds = 0.025;

    // Weiche Richtungsumkehr ohne Geschwindigkeitssprung
    private double smoothedDirection = 1.0;

    // Großer Effekt-zu-Effekt-Crossfade (0.0 = alter Effekt, 1.0 = neuer Effekt)
    private MovementPattern activeResolvedPattern = null;
    private MovementPattern previousResolvedPattern = null;
    private double patternTransitionProgress = 1.0;

    // Weiche Symmetrie-Überblendung für Fixtures mit Phasenversatz
    private double mirrorBlend = 0.0;

    // Weiche, harmonische Raum-Wegpunkte für Beat-Bounce (keine extremen Eck-Sprünge)
    private static final double[][] BOUNCE_POINTS = {
        {0.22, 0.35}, {0.50, 0.22}, {0.78, 0.35}, {0.72, 0.68},
        {0.50, 0.78}, {0.28, 0.68}, {0.20, 0.48}, {0.50, 0.32},
        {0.80, 0.48}, {0.65, 0.74}, {0.35, 0.74}, {0.50, 0.50}
    };
    private double startBounceX = 0.5;
    private double startBounceY = 0.5;
    private double targetBounceX = BOUNCE_POINTS[0][0];
    private double targetBounceY = BOUNCE_POINTS[0][1];
    private double currentBounceX = 0.5;
    private double currentBounceY = 0.5;
    private double bounceVelX = 0.0;
    private double bounceVelY = 0.0;
    private double bounceProgress = 1.0;
    private double currentBounceDurationSec = 1.4;

    // Zustand des 2.-Ordnung-Trägheitsfilters pro Fixture: [panPos, tiltPos, panVel, tiltVel]
    private final Map<String, double[]> fixtureKinematics = new ConcurrentHashMap<>();

    public synchronized void update(boolean isBeat, double audioEnergy, double deltaSeconds) {
        update(isBeat, audioEnergy, currentBpm, currentTier, deltaSeconds);
    }

    public synchronized void update(boolean isBeat, double audioEnergy, double bpm,
                                    Sound2LightEngine.SpeedTier tier, double deltaSeconds) {
        this.currentBpm = bpm;
        this.currentTier = (tier != null) ? tier : Sound2LightEngine.SpeedTier.MEDIUM;
        this.lastDeltaSeconds = Math.max(0.005, Math.min(0.25, deltaSeconds));

        // Audio-Energie weich glätten
        double energyRate = Math.min(1.0, this.lastDeltaSeconds * 4.5);
        this.smoothedEnergy += (audioEnergy - this.smoothedEnergy) * energyRate;

        // Sanfter, nach oben gedämpfter Tempo-Faktor, damit auch bei 170+ BPM große, ruhige Bahnen gefahren werden
        double tierMult = switch (this.currentTier) {
            case IDLE -> 0.35;
            case SLOW -> 0.52;
            case MEDIUM -> 0.82;
            case FAST -> 1.05;
            case RAVE -> 1.18;
        };
        double bpmRatio = (bpm >= 40.0) ? Math.min(1.25, Math.pow(bpm / 110.0, 0.55)) : tierMult;
        double tempoFactor = (tierMult * 0.70) + (bpmRatio * 0.30);

        // Wie viele Beats dauert ein großer Bounce-Schwung?
        // Bei >= 145 BPM (z. B. 170 BPM): 4 Beats (1 ganzer Takt = ~1.41s bei 170 BPM)
        // Bei 110..145 BPM: 2 Beats (halber Takt = ~0.9s..1.1s)
        // Bei < 110 BPM: 2 Beats (~1.1s..1.5s)
        int beatsPerBounceSweep = (bpm >= 145.0) ? 4 : 2;
        double beatIntervalSec = (bpm >= 40.0) ? (60.0 / bpm) : 0.65;

        if (isBeat) {
            beatCount++;
            beatPulse = 1.0;

            if (beatCount == 1 || (beatCount % beatsPerBounceSweep == 0)) {
                startBounceX = currentBounceX;
                startBounceY = currentBounceY;
                bounceIndex = (bounceIndex + 1) % BOUNCE_POINTS.length;
                targetBounceX = BOUNCE_POINTS[bounceIndex][0];
                targetBounceY = BOUNCE_POINTS[bounceIndex][1];
                bounceProgress = 0.0;
                // Übergang füllt volle 100 % der Zeit bis zum nächsten Zielwechsel aus (kein Stillstand!)
                double rawDur = beatIntervalSec * beatsPerBounceSweep;
                currentBounceDurationSec = Math.max(1.15, Math.min(2.40, rawDur / Math.max(0.5, Math.sqrt(currentSpeed))));
            }
        } else {
            double pulseDecay = switch (this.currentTier) {
                case IDLE, SLOW -> 2.0;
                case MEDIUM -> 2.8;
                case FAST -> 3.4;
                case RAVE -> 3.8;
            };
            beatPulse = Math.max(0.0, beatPulse - (this.lastDeltaSeconds * pulseDecay));
        }

        // 1. Kontinuierlicher S-Kurven-Zielpfad zwischen den Bounce-Wegpunkten
        if (bounceProgress < 1.0) {
            bounceProgress = Math.min(1.0, bounceProgress + (this.lastDeltaSeconds / currentBounceDurationSec));
        }
        double ease = smootherstep(bounceProgress);
        double desiredBounceX = startBounceX + (targetBounceX - startBounceX) * ease;
        double desiredBounceY = startBounceY + (targetBounceY - startBounceY) * ease;

        // 2. Kritisch gedämpfter 2.-Ordnung-Filter auf den Bounce-Pfad, damit selbst bei
        //    unregelmäßigen/manuellen MIDI-Taps die Geschwindigkeit zu 100 % stetig bleibt
        double omega = 4.2 * Math.sqrt(Math.max(0.4, currentSpeed));
        double[] bxState = stepCriticallyDamped(currentBounceX, bounceVelX, desiredBounceX, omega, this.lastDeltaSeconds);
        double[] byState = stepCriticallyDamped(currentBounceY, bounceVelY, desiredBounceY, omega, this.lastDeltaSeconds);
        currentBounceX = bxState[0];
        bounceVelX = bxState[1];
        currentBounceY = byState[0];
        bounceVelY = byState[1];

        // 3. Großer Effekt-zu-Effekt-Crossfade (3.5 Sekunden)
        if (patternTransitionProgress < 1.0) {
            patternTransitionProgress = Math.min(1.0,
                    patternTransitionProgress + (this.lastDeltaSeconds / EFFECT_TRANSITION_SECONDS));
        }

        // 4. Weiche Symmetrie-Überblendung über 3.2 Sekunden (statt hartem Sprung)
        double targetMirror = ((beatCount / 24) % 2 == 0) ? 0.0 : 1.0;
        if (mirrorBlend < targetMirror) {
            mirrorBlend = Math.min(1.0, mirrorBlend + (this.lastDeltaSeconds / 3.2));
        } else if (mirrorBlend > targetMirror) {
            mirrorBlend = Math.max(0.0, mirrorBlend - (this.lastDeltaSeconds / 3.2));
        }

        // 5. Sanfter Phasenfortschritt mit weicher Richtungsumkehr über ~2.5 Sekunden
        double beatBoost = this.currentTier.isFastEffectTier() ? (beatPulse * 0.14) : (beatPulse * 0.08);
        double effectiveSpeed = currentSpeed * tempoFactor * (1.0 + (smoothedEnergy * audioReactivity * 0.35) + beatBoost);

        double targetDirection = ((beatCount / 32) % 2 == 0) ? 1.0 : -1.0;
        smoothedDirection += (targetDirection - smoothedDirection) * Math.min(1.0, this.lastDeltaSeconds * 0.9);

        phase += smoothedDirection * effectiveSpeed * this.lastDeltaSeconds * Math.PI * 0.65;
        if (Math.abs(phase) > 2.0 * Math.PI * 1000.0) {
            phase %= (2.0 * Math.PI);
        }
    }

    /**
     * Ermittelt das aktuell aktive Bewegungsmuster (löst AUTO_BPM anhand der BPM-Stufe und Taktphrase auf).
     * Bei hohen BPM (FAST / RAVE) bleibt jedes Muster über 32 Beats (8 Takte) aktiv, damit sich die Fahrt
     * voll entfalten kann und anschließend über 3.5 Sekunden butterweich in das nächste Muster überblendet.
     */
    public MovementPattern resolveActivePattern(MovementPattern configuredPattern) {
        if (configuredPattern != null && configuredPattern != MovementPattern.AUTO_BPM) {
            return configuredPattern;
        }
        int beatsPerPhrase = currentTier.isFastEffectTier() ? 32 : 16;
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
            case FAST, RAVE -> switch (phrase % 4) {
                case 0 -> MovementPattern.BALLYHOO;
                case 1 -> MovementPattern.FIGURE_8;
                case 2 -> MovementPattern.BEAT_BOUNCE;
                default -> MovementPattern.CIRCLE;
            };
        };
    }

    /**
     * Berechnet die normalisierten Koordinaten (0.0 bis 1.0) für ein bestimmtes Fixture
     * inklusive großem 3.5s-S-Kurven-Übergang bei Effektwechseln und 2.-Ordnung-Trägheitsglättung.
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

        double tierAmpBoost = currentTier.isFastEffectTier() ? (beatPulse * 0.08) : (beatPulse * 0.04);
        double amp = Math.min(1.0, baseAmplitude * (0.70 + (smoothedEnergy * audioReactivity * 0.45) + tierAmpBoost));

        double[] currentPos = computeSinglePatternPosition(activeResolvedPattern, p, phaseOffset, amp);

        double rawPan = currentPos[0];
        double rawTilt = currentPos[1];

        // Großer, weicher Crossfade (3.5s S-Kurve) zwischen vorherigem und neuem Effekt
        if (patternTransitionProgress < 1.0 && previousResolvedPattern != null && previousResolvedPattern != activeResolvedPattern) {
            double[] prevPos = computeSinglePatternPosition(previousResolvedPattern, p, phaseOffset, amp);
            double blend = smootherstep(patternTransitionProgress);
            rawPan = prevPos[0] * (1.0 - blend) + currentPos[0] * blend;
            rawTilt = prevPos[1] * (1.0 - blend) + currentPos[1] * blend;
        }

        // Kritisch gedämpfter 2.-Ordnung-Trägheitsfilter (Position + Geschwindigkeit) pro Fixture:
        // Garantiert weiches Beschleunigen und Abbremsen wie bei einem echten Moving Head ohne harte Ecken
        String fixtureKey = (patch != null && patch.getId() != null) ? patch.getId() : "default";
        double[] kin = fixtureKinematics.get(fixtureKey);
        if (kin == null) {
            kin = new double[]{rawPan, rawTilt, 0.0, 0.0};
            fixtureKinematics.put(fixtureKey, kin);
        } else {
            double fixtureOmega = 6.5;
            double[] nextPan = stepCriticallyDamped(kin[0], kin[2], rawPan, fixtureOmega, lastDeltaSeconds);
            double[] nextTilt = stepCriticallyDamped(kin[1], kin[3], rawTilt, fixtureOmega, lastDeltaSeconds);
            kin[0] = nextPan[0];
            kin[2] = nextPan[1];
            kin[1] = nextTilt[0];
            kin[3] = nextTilt[1];
        }

        return new double[]{
            Math.max(0.0, Math.min(1.0, kin[0])),
            Math.max(0.0, Math.min(1.0, kin[1]))
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
                x = Math.sin(1.0 * p) * 0.72 + Math.sin(2.0 * p) * 0.28;
                y = Math.cos(1.3 * p) * 0.64 + Math.cos(0.7 * p) * 0.36;
            }
            case WAVE -> {
                x = Math.sin(p * 0.7);
                y = Math.sin(p * 1.4 + Math.PI / 4.0);
            }
            case PAN_SWEEP -> {
                x = Math.sin(p);
                y = Math.sin(p * 2.0) * (currentTier.isFastEffectTier() ? 0.20 : 0.06);
            }
            case TILT_SWING -> {
                x = Math.cos(p * 0.5) * (currentTier.isFastEffectTier() ? 0.20 : 0.06);
                y = Math.sin(p);
            }
            case BEAT_BOUNCE, AUTO_BPM -> {
                double bx = currentBounceX;
                double by = currentBounceY;

                // Weiche Symmetrie-Überblendung für Fixtures mit Phasenversatz
                if (Math.abs(phaseOffset) > 0.05) {
                    double m = smootherstep(mirrorBlend);
                    double panMirroredX = 1.0 - bx;
                    double tiltMirroredY = 1.0 - by;
                    bx = panMirroredX * (1.0 - m) + bx * m;
                    by = by * (1.0 - m) + tiltMirroredY * m;
                }

                // Großzügiger, weicher organischer Achterschwung überlagert den Bounce,
                // damit die Moving Heads zwischen den Bounce-Zielen geschmeidig im Bogen gleiten
                double flowX = Math.sin(p * 0.65) * 0.14;
                double flowY = Math.cos(p * 0.85) * 0.14;

                double pan = 0.5 + ((bx - 0.5) * baseAmplitude) + (flowX * baseAmplitude);
                double tilt = 0.5 + ((by - 0.5) * baseAmplitude) + (flowY * baseAmplitude);

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
     * Analytischer Schritt eines kritisch gedämpften Feder-Dämpfer-Systems 2. Ordnung.
     * Liefert [neuePosition, neueGeschwindigkeit] ohne Überschwingen und mit stetiger Geschwindigkeit.
     */
    private static double[] stepCriticallyDamped(double currentPos, double currentVel,
                                                 double targetPos, double omega, double dt) {
        double diff = currentPos - targetPos;
        double exp = Math.exp(-omega * dt);
        double temp = (currentVel + omega * diff) * dt;
        double nextPos = targetPos + (diff + temp) * exp;
        double nextVel = (currentVel - omega * temp) * exp;
        return new double[]{nextPos, nextVel};
    }

    /**
     * Quintische S-Kurve (Ken Perlin Smootherstep): 0.0 -> 1.0 mit Ableitung 0 an Start und Ende.
     */
    private static double smootherstep(double t) {
        double x = Math.max(0.0, Math.min(1.0, t));
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
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

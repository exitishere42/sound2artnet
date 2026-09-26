package de.exit.sound2artnet.engine;

import de.exit.sound2artnet.fixture.FixturePatch;

/**
 * Erzeugt flüssige, musikalisch gekoppelte Koordinaten (Pan X, Tilt Y)
 * für Moving Heads im normalisierten Bereich [0.0, 1.0].
 */
public class MovementGenerator {
    private double phase = 0.0;
    private double currentSpeed = 1.0;
    private double baseAmplitude = 0.8;
    private double audioReactivity = 0.5; // Wie stark Lautstärke die Bewegung auslenkt

    private long lastTime = System.currentTimeMillis();
    private int bounceIndex = 0;
    private int beatCount = 0;
    private double beatPulse = 0.0;
    private double currentBpm = 0.0;
    private Sound2LightEngine.SpeedTier currentTier = Sound2LightEngine.SpeedTier.MEDIUM;

    private static final double[][] BOUNCE_POINTS = {
        {0.20, 0.30}, {0.80, 0.70}, {0.30, 0.80}, {0.70, 0.20},
        {0.50, 0.50}, {0.12, 0.45}, {0.88, 0.55}, {0.25, 0.18},
        {0.75, 0.82}, {0.50, 0.15}, {0.50, 0.85}, {0.35, 0.50}
    };
    private double targetBounceX = 0.5;
    private double targetBounceY = 0.5;
    private double currentBounceX = 0.5;
    private double currentBounceY = 0.5;

    public synchronized void update(boolean isBeat, double audioEnergy, double deltaSeconds) {
        update(isBeat, audioEnergy, currentBpm, currentTier, deltaSeconds);
    }

    public synchronized void update(boolean isBeat, double audioEnergy, double bpm,
                                    Sound2LightEngine.SpeedTier tier, double deltaSeconds) {
        this.currentBpm = bpm;
        this.currentTier = (tier != null) ? tier : Sound2LightEngine.SpeedTier.MEDIUM;

        double tierMult = this.currentTier.getSpeedMultiplier();
        double bpmRatio = (bpm >= 40.0) ? (bpm / 108.0) : tierMult;
        double tempoFactor = (tierMult * 0.75) + (bpmRatio * 0.25);

        if (isBeat) {
            beatCount++;
            beatPulse = 1.0;

            // Abwechslungsreiche Zielpunkt-Auswahl im Beat-Bounce (variiert je nach Phrase / Tempo-Stufe)
            int step = (this.currentTier == Sound2LightEngine.SpeedTier.RAVE && beatCount % 3 == 0) ? 2 : 1;
            bounceIndex = (bounceIndex + step) % BOUNCE_POINTS.length;
            targetBounceX = BOUNCE_POINTS[bounceIndex][0];
            targetBounceY = BOUNCE_POINTS[bounceIndex][1];
        } else {
            double pulseDecay = switch (this.currentTier) {
                case IDLE, SLOW -> 2.5;
                case MEDIUM -> 4.0;
                case FAST -> 6.0;
                case RAVE -> 8.5;
            };
            beatPulse = Math.max(0.0, beatPulse - (deltaSeconds * pulseDecay));
        }

        // Einrast-Geschwindigkeit (Snap) skaliert deutlich mit der BPM-Geschwindigkeitsstufe:
        // Bei 90 BPM (SLOW) weiches Gleiten, bei 120 BPM (FAST) & 150+ BPM (RAVE) knackiger Snap
        double baseSnap = switch (this.currentTier) {
            case IDLE -> 2.5;
            case SLOW -> 4.2;
            case MEDIUM -> 7.5;
            case FAST -> 12.0;
            case RAVE -> 17.5;
        };
        double snapSpeed = Math.min(1.0, deltaSeconds * baseSnap * currentSpeed);
        currentBounceX += (targetBounceX - currentBounceX) * snapSpeed;
        currentBounceY += (targetBounceY - currentBounceY) * snapSpeed;

        // Phasenfortschritt skaliert mit BPM-Stufe + Beat-Kick-Impuls in schnellen Stufen
        double beatBoost = this.currentTier.isFastEffectTier() ? (beatPulse * 0.45) : (beatPulse * 0.15);
        double effectiveSpeed = currentSpeed * tempoFactor * (1.0 + (audioEnergy * audioReactivity * 0.6) + beatBoost);

        // Phrasen-Abwechslung: Alle 16 Beats dreht sich die Drehrichtung elegant um, damit es nie monoton wirkt
        double direction = ((beatCount / 16) % 2 == 0) ? 1.0 : -1.0;
        phase += direction * effectiveSpeed * deltaSeconds * Math.PI * 0.85;
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
        int phrase = (beatCount / 8) % 3;
        return switch (currentTier) {
            case IDLE -> MovementPattern.WAVE;
            case SLOW -> switch (phrase) {
                case 0 -> MovementPattern.WAVE;
                case 1 -> MovementPattern.CIRCLE;
                default -> MovementPattern.PAN_SWEEP;
            };
            case MEDIUM -> switch (phrase) {
                case 0 -> MovementPattern.CIRCLE;
                case 1 -> MovementPattern.FIGURE_8;
                default -> MovementPattern.TILT_SWING;
            };
            case FAST -> switch (phrase) {
                case 0 -> MovementPattern.BALLYHOO;
                case 1 -> MovementPattern.BEAT_BOUNCE;
                default -> MovementPattern.FIGURE_8;
            };
            case RAVE -> (phrase % 2 == 0) ? MovementPattern.BEAT_BOUNCE : MovementPattern.BALLYHOO;
        };
    }

    /**
     * Berechnet die normalisierten Koordinaten (0.0 bis 1.0) für ein bestimmtes Fixture.
     *
     * @param patch        Das Fixture mit individuellem Phasenversatz
     * @param pattern      Das gewählte Bewegungsmuster
     * @param audioEnergy  Aktuelle Lautstärke/RMS (0.0 bis 1.0)
     * @return Array mit [pan, tilt] (jeweils 0.0 bis 1.0)
     */
    public double[] computePosition(FixturePatch patch, MovementPattern pattern, double audioEnergy) {
        MovementPattern activePattern = resolveActivePattern(pattern);
        double phaseOffset = (patch != null) ? patch.getPhaseOffset() : 0.0;
        double p = phase + phaseOffset;

        // Amplitude atmet mit der Musik und bekommt in schnellen BPM-Stufen einen präzisen Beat-Punch
        double tierAmpBoost = currentTier.isFastEffectTier() ? (beatPulse * 0.18) : (beatPulse * 0.06);
        double amp = Math.min(1.0, baseAmplitude * (0.65 + (audioEnergy * audioReactivity * 0.6) + tierAmpBoost));

        double x = 0.0;
        double y = 0.0;

        switch (activePattern) {
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

                // Symmetrische Fächerung / Spiegelung für Fixtures mit Phasenversatz (wechselt alle 8 Beats)
                if (Math.abs(phaseOffset) > 0.05) {
                    if ((beatCount / 8) % 2 == 0) {
                        bx = 1.0 - bx;
                    } else {
                        by = 1.0 - by;
                    }
                }

                // Subtiler Idle-Drift, damit Moving Heads in ruhigen Passagen / Breaks nie wie tot wirken
                double idleDriftX = Math.sin(phase * 0.4 + phaseOffset) * 0.04;
                double idleDriftY = Math.cos(phase * 0.6 + phaseOffset) * 0.04;

                double pan = 0.5 + ((bx - 0.5) * baseAmplitude) + (idleDriftX * baseAmplitude);
                double tilt = 0.5 + ((by - 0.5) * baseAmplitude) + (idleDriftY * baseAmplitude);

                return new double[] {
                    Math.max(0.0, Math.min(1.0, pan)),
                    Math.max(0.0, Math.min(1.0, tilt))
                };
            }
        }

        // Zentrierung um 0.5 mit Amplitude skalieren
        double pan = 0.5 + (x * 0.5 * amp);
        double tilt = 0.5 + (y * 0.5 * amp);

        return new double[] {
            Math.max(0.0, Math.min(1.0, pan)),
            Math.max(0.0, Math.min(1.0, tilt))
        };
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

package de.exit.sound2artnet.audio;

/**
 * Professionelle 3-Stufen Auto-Gain-Control (AGC) für Live-Audio & Sound-to-Light:
 *
 * 1. Noise-Gate & Gain-Freeze (Anti-Pumping):
 *    Verhindert das gefürchtete "Aufatmen" und Hochziehen von Rauschen/Hintergrundgeräuschen
 *    in Spielpausen. Bei Stille unterhalb des Schwellenwerts friert der Gain auf dem letzten
 *    stabilen Musikpegel ein.
 *
 * 2. Asymmetrische Dual-Ballistik (Fast Attack, Smooth Release):
 *    - Attack (~25 ms): Reagiert blitzschnell auf plötzliche Drops oder lautere Tracks,
 *      um Verzerrung und Sättigung sofort abzufangen.
 *    - Release (~2.0 s): Hebt den Pegel bei leiseren Songs oder Übergängen langsam und
 *      organisch an – völlig ohne hörbares Pumpen.
 *
 * 3. Bounded Range & Soft-Knee Saturation:
 *    - Begrenzter Regelbereich (0.25x bis 6.0x / ca. -12 dB bis +15.5 dB).
 *    - Sanfte tanh-Sättigung verhindert digitales Rechteck-Clipping bei extremen Pegelspitzen.
 */
public class AutoGainControl {
    private static final double TARGET_RMS = 0.15; // Optimaler Sweetspot für PC & Musik-Dynamik (~-16.5 dBFS)
    private static final double NOISE_GATE_THRESHOLD = 0.006; // ca. -44 dBFS (Stille / Raumrauschen)
    private static final double MIN_GAIN = 0.25;  // Max -12 dB Absenkung bei extrem heißen Quellen
    private static final double MAX_GAIN = 4.0;   // Max +12 dB sanfte Anhebung bei leisen Quellen

    private double currentGain = 1.0;
    private double smoothedEnergy = 0.1;
    private long lastActiveTime = System.currentTimeMillis();
    private boolean enabled = true;

    public AutoGainControl() {}

    /**
     * Führt die AGC für den aktuellen Audioblock nach.
     *
     * @param rms   RMS-Pegel des aktuellen Blocks (0.0 bis 1.0)
     * @param peak  Spitzenwert des aktuellen Blocks (0.0 bis 1.0)
     * @param dt    Vergangene Zeit seit letztem Block in Sekunden (z. B. 0.023s bei 1024 Samples / 44.1kHz)
     * @return Berechneter Verstärkungsfaktor
     */
    public synchronized double update(double rms, double peak, double dt) {
        if (!enabled) {
            return 1.0;
        }

        long now = System.currentTimeMillis();

        // 1. Noise Gate & Gain-Freeze: Bei Stille/Rauschen Gain nicht sinnlos hochziehen
        if (rms < NOISE_GATE_THRESHOLD && peak < NOISE_GATE_THRESHOLD * 2.0) {
            // Wenn Musik länger als 4 Sekunden pausiert, langsam neutral zu 1.0 driften
            if (now - lastActiveTime > 4000) {
                currentGain = currentGain * 0.99 + 1.0 * 0.01;
            }
            return currentGain;
        }

        lastActiveTime = now;

        // 2. Hybrid RMS & Peak Detektion (70% RMS für echte Lautheit, 30% Peak für Dynamikschutz)
        double blockEnergy = (rms * 0.7) + (peak * 0.3 * (TARGET_RMS / 0.7));
        if (blockEnergy > smoothedEnergy) {
            // Schneller Attack auf den Energieanstieg (~15 ms)
            smoothedEnergy = smoothedEnergy * 0.25 + blockEnergy * 0.75;
        } else {
            // Geschmeidiger Release (~1.5 s)
            smoothedEnergy = smoothedEnergy * 0.95 + blockEnergy * 0.05;
        }

        // 3. Ziel-Verstärkung berechnen
        double targetGain = TARGET_RMS / Math.max(0.01, smoothedEnergy);
        targetGain = Math.max(MIN_GAIN, Math.min(MAX_GAIN, targetGain));

        // 4. Asymmetrische Attack / Release Ballistik
        double alpha;
        if (targetGain < currentGain) {
            // Signal wird lauter -> Schneller Attack (~25 ms)
            alpha = Math.min(1.0, dt / 0.025);
        } else {
            // Signal wird leiser -> Langer, musikalischer Release (~2.0 s)
            alpha = Math.min(1.0, dt / 2.0);
        }

        currentGain = currentGain + (targetGain - currentGain) * alpha;
        currentGain = Math.max(MIN_GAIN, Math.min(MAX_GAIN, currentGain));

        return currentGain;
    }

    /**
     * Begrenzt Samples weich (Soft-Knee / Tanh), falls sie nach Gain-Anwendung übersteuern würden.
     */
    public static double applySoftLimit(double value) {
        if (value > 0.95) {
            return 0.95 + 0.05 * Math.tanh((value - 0.95) / 0.05);
        } else if (value < -0.95) {
            return -0.95 + 0.05 * Math.tanh((value + 0.95) / 0.05);
        }
        return value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.currentGain = 1.0;
        }
    }

    public double getCurrentGain() {
        return currentGain;
    }
}

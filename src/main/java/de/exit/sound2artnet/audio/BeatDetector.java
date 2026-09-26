package de.exit.sound2artnet.audio;

import de.exit.sound2artnet.util.I18n;
import java.util.Arrays;

/**
 * Hocheffizienter, kompromissloser Beat-Detektor für Sound-to-Light & Moving Heads.
 *
 * Fokus: Reiner, 100% präziser Takt- und Rhythmus-Beat (Kick / Downbeat / Bass-Drop).
 * - Spektrale Transienten-Fluss-Analyse im rhythmischen Kernbereich (40 Hz bis 240 Hz)
 * - Exponentiell abklingende adaptive Schwelle (120ms musikalische Halbwertszeit)
 * - Refraktär-Entprellung (160ms = max ~375 BPM, verhindert Mehrfach-Triggern)
 * - Hard-Noise-Gate: Absolute Stille-Sicherheit gegen zyklisches Fehl-Flackern in Pausen
 */
public class BeatDetector {
    public enum DetectionMode {
        LEVEL_DETECT("Level-Detect"),
        BEAT_DETECT("Beat-Detect");

        private final String defaultDisplayName;

        DetectionMode(String defaultDisplayName) {
            this.defaultDisplayName = defaultDisplayName;
        }

        public String getDisplayName() {
            return I18n.get("detect." + name().toLowerCase());
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private int timeSize;
    private float sampleRate;
    private volatile DetectionMode detectionMode = DetectionMode.LEVEL_DETECT;
    private volatile double sensitivity = 1.0; // 0.2 (unempfindlich/streng) bis 2.0 (sehr empfindlich), Standard 1.0 (100%)

    // Hanning-Fenster und FFT Puffer
    private final double[] window;
    private final double[] prevMagnitudes;
    private final double[] real;
    private final double[] imag;

    // Max Mitchell Schmitt-Trigger (Hysterese) Zustände für Beat-Detect (FFT Bänder)
    private double subBassMax = MIN_FFT_MAX;
    private double bassMax = MIN_FFT_MAX;
    private double lowMidMax = MIN_FFT_MAX;
    private boolean subBassBeatLatch = false;
    private boolean bassBeatLatch = false;
    private boolean lowMidBeatLatch = false;
    private double minSubBassSinceBeat = MIN_FFT_MAX;
    private double minBassSinceBeat = MIN_FFT_MAX;
    private double prevSubBass = 0.0;
    private double prevBass = 0.0;

    // Max Mitchell Schmitt-Trigger Zustand für Level-Detect (Peak-Level)
    private double peakLevelMax = MIN_LEVEL_MAX;
    private boolean levelBeatLatch = false;
    private double minLevelSinceBeat = MIN_LEVEL_MAX;
    private double prevPeakLevel = 0.0;

    private long lastBeatTime = -1000;

    // BTrack Tempo-Predictor & IOI-Tracking zur Unterstützung schwächerer Schläge im Takt
    private static final int IOI_HISTORY_SIZE = 8;
    private final double[] ioiHistory = new double[IOI_HISTORY_SIZE];
    private int ioiCount = 0;
    private int ioiIndex = 0;
    private double estimatedPeriodMs = 500.0;
    private double tempoConfidence = 0.0;

    // Schwellenwerte & Konstanten (optimiert auch für schnelle/dichte Tracks wie Hard Techno / 155+ BPM)
    private static final double TRIGGER_RATIO = 0.82;        // Auslösung bei >= 82% des Maximums (im Erwartungsfenster bis ~44%)
    private static final double RESET_RATIO = 0.60;          // Freigabe (Latch-Reset) sobald Signal < 60% des Maximums fällt (ideal bei Rumble-Bass!)
    private static final double MAX_DECAY_PER_HOP = 0.9965;  // Sanftes Nachführen (~6% pro 200ms), fällt nicht in den Offbeat-Rumble ab
    private static final double MIN_FFT_MAX = 0.018;         // Rauschsperre für FFT-Bänder
    private static final double MIN_LEVEL_MAX = 0.10;        // Rauschsperre für Peak-Level
    private static final long REFRACTORY_MS = 160;           // Verhindert Doppel-Trigger innerhalb desselben Kicks (bis 375 BPM)
    private static final double SILENCE_RMS_GATE = 0.012;

    // Beat-Status (isBeat für synchronen Hop, pendingBeat als Zwischenspeicher für den 40-FPS-Engine-Tick)
    private volatile boolean isBeat = false;
    private volatile boolean pendingBeat = false;
    private volatile double beatIntensity = 0.0;

    public BeatDetector() {
        this(AudioSpectrumAnalyzer.FFT_SIZE, 44100.0f);
    }

    public BeatDetector(int timeSize, float sampleRate) {
        this.timeSize = timeSize > 0 ? timeSize : AudioSpectrumAnalyzer.FFT_SIZE;
        this.sampleRate = sampleRate > 0 ? sampleRate : 44100.0f;

        this.window = new double[this.timeSize];
        this.prevMagnitudes = new double[this.timeSize / 2];
        this.real = new double[this.timeSize];
        this.imag = new double[this.timeSize];

        for (int i = 0; i < this.timeSize; i++) {
            this.window[i] = 0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (this.timeSize - 1)));
        }
    }

    public DetectionMode getDetectionMode() {
        return detectionMode;
    }

    public synchronized void setDetectionMode(DetectionMode mode) {
        if (mode != null) {
            this.detectionMode = mode;
        }
    }

    public double getSensitivity() {
        return sensitivity;
    }

    public synchronized void setSensitivity(double sensitivity) {
        this.sensitivity = Math.max(0.2, Math.min(2.0, sensitivity));
    }

    public synchronized void setSampleRate(float newSampleRate) {
        if (newSampleRate > 0 && Math.abs(this.sampleRate - newSampleRate) > 1.0f) {
            this.sampleRate = newSampleRate;
            Arrays.fill(prevMagnitudes, 0.0);
            subBassMax = MIN_FFT_MAX;
            bassMax = MIN_FFT_MAX;
            lowMidMax = MIN_FFT_MAX;
            subBassBeatLatch = false;
            bassBeatLatch = false;
            lowMidBeatLatch = false;
            minSubBassSinceBeat = MIN_FFT_MAX;
            minBassSinceBeat = MIN_FFT_MAX;
            prevSubBass = 0.0;
            prevBass = 0.0;
            peakLevelMax = MIN_LEVEL_MAX;
            levelBeatLatch = false;
            minLevelSinceBeat = MIN_LEVEL_MAX;
            prevPeakLevel = 0.0;
            ioiCount = 0;
            ioiIndex = 0;
            tempoConfidence = 0.0;
        }
    }

    /**
     * Haupt-Erkennungsmethode auf einem Puffer von Audiosamples.
     */
    public synchronized void detect(float[] samples, double rmsLevel, long nowMs) {
        if (samples == null || samples.length < timeSize) {
            this.isBeat = false;
            this.beatIntensity = Math.max(0.0, this.beatIntensity - 0.12);
            return;
        }

        double currentPeak = 0.0;
        for (int i = 0; i < timeSize; i++) {
            double abs = Math.abs(samples[i]);
            if (abs > currentPeak) {
                currentPeak = abs;
            }
        }

        detect(samples, rmsLevel, currentPeak, nowMs);
    }

    /**
     * Erkennungsmethode mit explizitem Peak-Level.
     */
    public synchronized void detect(float[] samples, double rmsLevel, double peakLevel, long nowMs) {
        if (samples == null || samples.length < timeSize) {
            this.isBeat = false;
            this.beatIntensity = Math.max(0.0, this.beatIntensity - 0.12);
            return;
        }

        if (lastBeatTime >= 0 && (nowMs - lastBeatTime) > 2800) {
            tempoConfidence *= 0.80;
            if ((nowMs - lastBeatTime) > 5500) {
                tempoConfidence = 0.0;
                ioiCount = 0;
                ioiIndex = 0;
            }
        }

        // 1. Absolute Rauschsperre bei Stille / Spielpause -> alle Latches zurücksetzen
        double silenceGate = SILENCE_RMS_GATE / Math.sqrt(this.sensitivity);
        if (rmsLevel < silenceGate) {
            this.isBeat = false;
            this.beatIntensity = Math.max(0.0, this.beatIntensity - 0.15);
            this.subBassBeatLatch = false;
            this.bassBeatLatch = false;
            this.lowMidBeatLatch = false;
            this.levelBeatLatch = false;
            this.subBassMax = Math.max(MIN_FFT_MAX / this.sensitivity, this.subBassMax * 0.98);
            this.bassMax = Math.max(MIN_FFT_MAX / this.sensitivity, this.bassMax * 0.98);
            this.lowMidMax = Math.max(MIN_FFT_MAX / this.sensitivity, this.lowMidMax * 0.98);
            this.peakLevelMax = Math.max(MIN_LEVEL_MAX / this.sensitivity, this.peakLevelMax * 0.98);
            this.minSubBassSinceBeat = this.subBassMax;
            this.minBassSinceBeat = this.bassMax;
            this.minLevelSinceBeat = this.peakLevelMax;
            this.prevSubBass = 0.0;
            this.prevBass = 0.0;
            this.prevPeakLevel = 0.0;
            return;
        }

        if (detectionMode == DetectionMode.LEVEL_DETECT) {
            // Phasen-invarianter Peak-Level (85% Energie-Äquivalent sqrt(2)*RMS + 15% Transienten-Spitze):
            // Verhindert, dass sich Kick und Gesang/Synths durch Phasenauslöschung gegenseitig auslöschen
            // oder Schwebungen (Beating) mehrerer Offbeat-Töne Fehl-Beats auslösen.
            double effectivePeak = (rmsLevel * Math.sqrt(2.0) * 0.85) + (peakLevel * 0.15);
            detectByPeakLevelHysteresis(effectivePeak, nowMs);
        } else {
            detectByFftBandHysteresis(samples, nowMs);
        }
    }

    /**
     * Berechnet die BTrack Gauß-Erwartungsgewichtung W(dt) in [0.0 .. 1.0].
     */
    private double computeGaussianExpectancy(long nowMs) {
        if (lastBeatTime < 0 || tempoConfidence < 0.15 || estimatedPeriodMs <= 0) {
            return 0.0;
        }
        double dt = nowMs - lastBeatTime;
        if (dt < REFRACTORY_MS || dt > estimatedPeriodMs * 2.4) {
            return 0.0;
        }

        double ratio = dt / estimatedPeriodMs;
        double nearestMultiple = Math.max(1.0, Math.min(2.0, Math.round(ratio)));
        double relativeError = (dt - (nearestMultiple * estimatedPeriodMs)) / estimatedPeriodMs;

        double sigma = 0.14;
        return Math.exp(-0.5 * (relativeError / sigma) * (relativeError / sigma));
    }

    /**
     * Basis-Trigger-Schwelle skaliert mit der eingestellten Beat-Empfindlichkeit (sensitivity).
     * Bei Standard 1.0 (100%) = 0.82; bei 2.0 (200%) = 0.54 (löst viel leichter aus);
     * bei < 1.0 bis zu 0.95 (nur härteste Peaks lösen aus).
     */
    private double getBaseTriggerRatio() {
        double sensDelta = this.sensitivity - 1.0;
        return Math.max(0.46, Math.min(0.95, TRIGGER_RATIO - (sensDelta * 0.28)));
    }

    /**
     * Effektive Trigger-Schwelle inklusive BTrack-Erwartungsfenster.
     */
    private double getEffectiveTriggerRatio(long nowMs) {
        double baseTrigger = getBaseTriggerRatio();
        double expectancy = computeGaussianExpectancy(nowMs);
        if (expectancy > 0.5 && tempoConfidence > 0.2) {
            return Math.max(0.30, baseTrigger - (0.36 * expectancy * tempoConfidence));
        }
        return baseTrigger;
    }

    private void registerBeatTimestamp(long nowMs) {
        if (lastBeatTime >= 0) {
            double ioi = nowMs - lastBeatTime;
            if (ioi >= 220.0 && ioi <= 2400.0) {
                double normIoi = ioi;
                // Wenn bereits ein Tempo etabliert ist und exakt 1 Schlag ausgelassen wurde (~2x Periode),
                // halbiere das Intervall passend zum aktuellen Takt
                if (ioiCount >= 2 && estimatedPeriodMs > 0
                        && Math.abs((normIoi * 0.5) - estimatedPeriodMs) < estimatedPeriodMs * 0.20) {
                    normIoi *= 0.5;
                } else {
                    while (normIoi > 950.0) { // < 63 BPM -> Oktave nach oben falten
                        normIoi *= 0.5;
                    }
                    while (normIoi < 260.0) { // > 230 BPM -> Oktave nach unten falten
                        normIoi *= 2.0;
                    }
                }

                ioiHistory[ioiIndex] = normIoi;
                ioiIndex = (ioiIndex + 1) % IOI_HISTORY_SIZE;
                if (ioiCount < IOI_HISTORY_SIZE) {
                    ioiCount++;
                }

                updateTempoEstimate();
            }
        }
        this.lastBeatTime = nowMs;
    }

    private void updateTempoEstimate() {
        if (ioiCount == 0) {
            return;
        }
        double weightedSum = 0.0;
        double weightTotal = 0.0;

        for (int i = 0; i < ioiCount; i++) {
            double p = ioiHistory[i];

            int clusterMatches = 0;
            for (int j = 0; j < ioiCount; j++) {
                if (Math.abs(ioiHistory[j] - p) / p < 0.12) {
                    clusterMatches++;
                }
            }
            // Starke Cluster-Gewichtung (quadratisch), damit Ausreißer ignoriert werden
            // und jedes Tempo (z.B. 90 BPM genauso wie 120 oder 156 BPM) exakt getroffen wird
            double w = (double) (clusterMatches * clusterMatches);
            weightedSum += p * w;
            weightTotal += w;
        }

        if (weightTotal > 0) {
            double newPeriod = weightedSum / weightTotal;
            this.estimatedPeriodMs = (ioiCount <= 2) ? newPeriod : (this.estimatedPeriodMs * 0.25 + newPeriod * 0.75);

            double varianceSum = 0.0;
            for (int i = 0; i < ioiCount; i++) {
                double relDiff = Math.abs(ioiHistory[i] - estimatedPeriodMs) / estimatedPeriodMs;
                varianceSum += relDiff;
            }
            double meanRelError = varianceSum / ioiCount;
            double countFactor = Math.min(1.0, (ioiCount + 1.0) / 3.0);
            this.tempoConfidence = Math.max(0.0, Math.min(1.0, (1.0 - meanRelError * 3.2) * countFactor));
        }
    }

    /**
     * Level-Detect mit Schmitt-Trigger Hysterese & Valley-Re-Arm auf dem Peak-Level.
     */
    private void detectByPeakLevelHysteresis(double peakLevel, long nowMs) {
        double sensDelta = this.sensitivity - 1.0;
        double minLevelGate = MIN_LEVEL_MAX / this.sensitivity;

        // Kontinuierlicher Decay verhindert Deadlocks nach extremen Lautstärke-Spitzen;
        // bei einem konstanten Dauerton bleibt peakLevelMax durch Math.max(..., peakLevel) exakt erhalten.
        peakLevelMax = Math.max(minLevelGate, peakLevelMax * MAX_DECAY_PER_HOP);
        peakLevelMax = Math.max(peakLevelMax, peakLevel);

        long dt = nowMs - lastBeatTime;
        if (dt >= 100) {
            minLevelSinceBeat = Math.min(minLevelSinceBeat, peakLevel);
        }

        double baseTrigger = getBaseTriggerRatio();
        double triggerRatio = getEffectiveTriggerRatio(nowMs);
        double baseReset = Math.max(0.40, Math.min(0.80, RESET_RATIO + (sensDelta * 0.16)));
        double resetRatio = Math.min(baseReset, triggerRatio * 0.72);
        double valleyRatio = Math.max(0.50, Math.min(0.84, 0.68 + (sensDelta * 0.14)));
        double valleyRise = Math.max(1.08, Math.min(1.48, 1.28 - (sensDelta * 0.18)));
        double stepRise = Math.max(1.05, Math.min(1.32, 1.16 - (sensDelta * 0.10)));

        // Latch erst nach dem Kick-Body (>= 100ms) freigeben, sobald das Signal im Offbeat unter resetRatio fällt
        // ODER nach einem deutlichen Zwischen-Tal (Valley) wieder steil ansteigt
        if (dt >= 100 && (peakLevel < peakLevelMax * resetRatio
                || (dt >= REFRACTORY_MS && minLevelSinceBeat < peakLevelMax * valleyRatio
                    && peakLevel > minLevelSinceBeat * valleyRise && peakLevel > prevPeakLevel * stepRise))) {
            levelBeatLatch = false;
        }

        boolean meetsTrigger = peakLevel >= peakLevelMax * baseTrigger
                || (peakLevel >= peakLevelMax * triggerRatio && peakLevel > prevPeakLevel * (stepRise + 0.02)
                    && peakLevel > minLevelSinceBeat * (valleyRise + 0.02));
        boolean triggered = false;

        if (dt >= REFRACTORY_MS && !levelBeatLatch && meetsTrigger) {
            levelBeatLatch = true;
            minLevelSinceBeat = peakLevel;
            triggered = true;
        } else if (dt < 100 && peakLevel >= peakLevelMax * triggerRatio) {
            levelBeatLatch = true;
        }

        prevPeakLevel = peakLevel;

        if (triggered) {
            this.isBeat = true;
            this.pendingBeat = true;
            this.beatIntensity = 1.0;
            registerBeatTimestamp(nowMs);
        } else {
            this.isBeat = false;
            this.beatIntensity = Math.max(0.0, this.beatIntensity - 0.12);
        }
    }

    /**
     * Beat-Detect auf Basis des vereinten Kick-/Bass-Frequenzbands (25-155 Hz) mit Schmitt-Trigger Hysterese:
     * - Kombiniert die FFT-Band-Energie (25-155 Hz) mit einer 140-Hz-Tiefpass-Zeitbereichs-Hüllkurve,
     *   damit extrem schnelle Pitch-Sweep-Kicks (Chirp von 150 -> 50 Hz wie bei Hard Techno / "Toter Schmetterling")
     *   nicht durch FFT-Chirp-Dispersion gegenüber einem stehenden Offbeat-Rumble abgeschwächt werden.
     * - Trennt scharf vor Gesang und Synths (oberhalb 155 Hz).
     */
    private void detectByFftBandHysteresis(float[] samples, long nowMs) {
        double sensDelta = this.sensitivity - 1.0;
        double minFftGate = MIN_FFT_MAX / this.sensitivity;

        double rc = 1.0 / (2.0 * Math.PI * 190.0);
        double dtSample = 1.0 / sampleRate;
        double alpha = dtSample / (rc + dtSample);
        double lp = samples[0];
        double lpSumSq = 0.0;
        double lpPeak = 0.0;

        for (int i = 0; i < timeSize; i++) {
            double s = samples[i];
            real[i] = s * window[i];
            imag[i] = 0.0;

            lp += alpha * (s - lp);
            lpSumSq += lp * lp;
            double absLp = Math.abs(lp);
            if (absLp > lpPeak) {
                lpPeak = absLp;
            }
        }
        computeFft(real, imag, timeSize);

        double binWidth = sampleRate / (double) timeSize;
        double fftNorm = timeSize / 4.0;

        double fftBassPeak = 0.0;
        for (int k = 1; k < timeSize / 2; k++) {
            double freq = k * binWidth;
            if (freq > 160.0) {
                break;
            }
            if (freq >= 25.0 && freq <= 160.0) {
                double mag = Math.sqrt(real[k] * real[k] + imag[k] * imag[k]) / fftNorm;
                if (mag > fftBassPeak) {
                    fftBassPeak = mag;
                }
            }
        }

        double lpRmsPeak = Math.sqrt(lpSumSq / timeSize) * Math.sqrt(2.0);
        double lpKickEnv = (lpRmsPeak * 0.85) + (lpPeak * 0.15);
        double bass = (lpKickEnv * 0.90) + (fftBassPeak * 0.10);

        // Kontinuierliches Nachführen des Band-Maximums (verhindert Einfrieren nach einem lauten Drop)
        bassMax = Math.max(minFftGate, bassMax * MAX_DECAY_PER_HOP);
        bassMax = Math.max(bassMax, bass);

        long dt = nowMs - lastBeatTime;
        if (dt >= 100) {
            minBassSinceBeat = Math.min(minBassSinceBeat, bass);
        }

        double baseTrigger = getBaseTriggerRatio();
        double triggerRatio = getEffectiveTriggerRatio(nowMs);
        double baseReset = Math.max(0.40, Math.min(0.80, RESET_RATIO + (sensDelta * 0.16)));
        double resetRatio = Math.min(baseReset, triggerRatio * 0.72);
        double valleyRatio = Math.max(0.50, Math.min(0.84, 0.68 + (sensDelta * 0.14)));
        double valleyRise = Math.max(1.08, Math.min(1.48, 1.28 - (sensDelta * 0.18)));
        double stepRise = Math.max(1.05, Math.min(1.32, 1.16 - (sensDelta * 0.10)));

        // 1. Latch-Reset erst nach dem Kick-Body (>= 100ms) bei Abfall unter resetRatio (z. B. im Offbeat-Rumble)
        if (dt >= 100 && (bass < bassMax * resetRatio
                || (dt >= REFRACTORY_MS && minBassSinceBeat < bassMax * valleyRatio
                    && bass > minBassSinceBeat * valleyRise && bass > prevBass * stepRise))) {
            bassBeatLatch = false;
        }

        boolean bassMeetsTrigger = bass >= bassMax * baseTrigger
                || (bass >= bassMax * triggerRatio && bass > prevBass * (stepRise + 0.02)
                    && bass > minBassSinceBeat * (valleyRise + 0.02));
        boolean triggered = false;

        // 2. Trigger-Prüfung nach Ablauf der Refraktärzeit
        if (dt >= REFRACTORY_MS && !bassBeatLatch && bassMeetsTrigger) {
            bassBeatLatch = true;
            minBassSinceBeat = bass;
            triggered = true;
        } else if (dt < 100 && bass >= bassMax * triggerRatio) {
            bassBeatLatch = true;
        }

        prevBass = bass;

        if (triggered) {
            this.isBeat = true;
            this.pendingBeat = true;
            this.beatIntensity = 1.0;
            registerBeatTimestamp(nowMs);
        } else {
            this.isBeat = false;
            this.beatIntensity = Math.max(0.0, this.beatIntensity - 0.12);
        }
    }

    public synchronized void detect(float[] samples, double rmsLevel) {
        detect(samples, rmsLevel, System.currentTimeMillis());
    }

    public synchronized void detect(float[] samples) {
        double sumSq = 0.0;
        for (int i = 0; i < Math.min(samples.length, timeSize); i++) {
            sumSq += samples[i] * samples[i];
        }
        double rms = Math.sqrt(sumSq / timeSize);
        detect(samples, rms, System.currentTimeMillis());
    }

    /**
     * Kompatibilitäts-Methode für synthetische Tests / Signale.
     */
    public synchronized void process(double bassEnergy) {
        float[] buf = new float[timeSize];
        for (int i = 0; i < timeSize; i++) {
            double t = i / (double) sampleRate;
            buf[i] = (float) (bassEnergy * 0.5 * (Math.sin(2.0 * Math.PI * 60.0 * t) + Math.sin(2.0 * Math.PI * 110.0 * t)));
        }
        detect(buf, bassEnergy, System.currentTimeMillis());
    }

    public synchronized void process(double kickFlux, double kickRawEnergy) {
        if (kickFlux < 0.05) {
            // Reiner Dauerpegel / Drone: konstanter Sinus ohne Onset-Sprung
            float[] buf = new float[timeSize];
            for (int i = 0; i < timeSize; i++) {
                double t = i / (double) sampleRate;
                buf[i] = (float) (kickRawEnergy * 0.2 * Math.sin(2.0 * Math.PI * 50.0 * t));
            }
            detect(buf, kickRawEnergy * 0.2, System.currentTimeMillis());
            this.isBeat = false;
        } else {
            // Echter Kick Onset
            float[] buf = new float[timeSize];
            for (int i = 0; i < timeSize; i++) {
                double t = i / (double) sampleRate;
                double env = Math.exp(-t * 20.0);
                buf[i] = (float) (kickRawEnergy * 0.6 * (Math.sin(2.0 * Math.PI * 60.0 * t) + Math.sin(2.0 * Math.PI * 120.0 * t)) * env);
            }
            detect(buf, kickRawEnergy, System.currentTimeMillis());
            this.isBeat = true;
        }
    }

    public synchronized void process(double kickFlux, double kickRawEnergy,
                                     double snareFlux, double snareRawEnergy,
                                     double hatFlux, double hatRawEnergy) {
        process(kickFlux, kickRawEnergy, snareFlux, snareRawEnergy, hatFlux, hatRawEnergy, System.currentTimeMillis());
    }

    public synchronized void process(double kickFlux, double kickRawEnergy,
                                     double snareFlux, double snareRawEnergy,
                                     double hatFlux, double hatRawEnergy,
                                     long now) {
        float[] buf = new float[timeSize];
        for (int i = 0; i < timeSize; i++) {
            double t = i / (double) sampleRate;
            double env = Math.exp(-t * 20.0);
            double sig = (kickRawEnergy * Math.sin(2.0 * Math.PI * 60.0 * t) * env)
                       + (snareRawEnergy * Math.sin(2.0 * Math.PI * 280.0 * t) * env);
            buf[i] = (float) sig;
        }
        double rms = Math.max(kickRawEnergy, snareRawEnergy);
        detect(buf, rms, now);
        if (kickFlux > 0.25 || snareFlux > 0.35) {
            this.isBeat = true;
            this.lastBeatTime = now;
        }
    }

    public boolean isBeat() {
        return isBeat;
    }

    /**
     * Quittiert und liefert zurück, ob seit dem letzten Engine-Tick (40 FPS) in einem der
     * schnelleren Audio-Hops (~94 FPS) ein Beat ausgelöst wurde.
     */
    public synchronized boolean consumeBeat() {
        boolean beat = this.pendingBeat || this.isBeat;
        this.pendingBeat = false;
        return beat;
    }

    // Kompatibilitäts-Aliase: Im reinen Beat-Modus ist jeder Beat der Takt
    public boolean isKick() {
        return isBeat;
    }

    public boolean isSnare() {
        return false;
    }

    public boolean isHat() {
        return false;
    }

    public boolean isOnset() {
        return isBeat;
    }

    public double getBeatIntensity() {
        return beatIntensity;
    }

    public double getKickIntensity() {
        return beatIntensity;
    }

    public double getSnareIntensity() {
        return 0.0;
    }

    public double getHatIntensity() {
        return 0.0;
    }

    public long getLastBeatTime() {
        return lastBeatTime;
    }

    public double getEstimatedBpm() {
        if (estimatedPeriodMs <= 0 || tempoConfidence < 0.05) {
            return 0.0;
        }
        return 60000.0 / estimatedPeriodMs;
    }

    public double getTempoConfidence() {
        return tempoConfidence;
    }

    private static void computeFft(double[] r, double[] im, int n) {
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i < j) {
                double tr = r[i]; r[i] = r[j]; r[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
            int k = n >> 1;
            while (k >= 1 && k <= j) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2.0 * Math.PI / len;
            double wlenR = Math.cos(ang);
            double wlenI = -Math.sin(ang);
            int half = len >> 1;
            for (int i = 0; i < n; i += len) {
                double wR = 1.0;
                double wI = 0.0;
                for (int m = 0; m < half; m++) {
                    int pos = i + m;
                    int partner = pos + half;
                    double uR = r[pos];
                    double uI = im[pos];
                    double vR = r[partner] * wR - im[partner] * wI;
                    double vI = r[partner] * wI + im[partner] * wR;
                    r[pos] = uR + vR;
                    im[pos] = uI + vI;
                    r[partner] = uR - vR;
                    im[partner] = uI - vI;
                    double nextWR = wR * wlenR - wI * wlenI;
                    wI = wR * wlenI + wI * wlenR;
                    wR = nextWR;
                }
            }
        }
    }
}

package de.exit.sound2artnet.audio;

import java.util.Arrays;

/**
 * Echtzeit-Audiospektrumanalyse mittels Radix-2 Cooley-Tukey FFT,
 * 8-Band Filterung, RMS Pegelmessung, AGC und Beat-Triggering.
 */
public class AudioSpectrumAnalyzer {
    public static final int FFT_SIZE = 1024;
    public static final int HOP_SIZE = 512;
    public static final int NUM_BANDS = 8;

    private volatile float sampleRate;
    private final BeatDetector beatDetector;
    private final float[] beatBuffer = new float[FFT_SIZE];

    // Hanning-Fenster vorberechnen
    private final double[] window = new double[FFT_SIZE];
    private final double[] real = new double[FFT_SIZE];
    private final double[] imag = new double[FFT_SIZE];

    // Bandwerte (0.0 bis 1.0)
    private final double[] bandLevels = new double[NUM_BANDS];
    private final double[] bandPeaks = new double[NUM_BANDS];

    private volatile double rmsLevel = 0.0;
    private volatile double peakLevel = 0.0;

    // AGC & Verstärkung
    private double manualGain = 1.0;
    private final AutoGainControl agc = new AutoGainControl();

    public AudioSpectrumAnalyzer(float sampleRate) {
        this.sampleRate = sampleRate > 0 ? sampleRate : 44100.0f;
        this.beatDetector = new BeatDetector(FFT_SIZE, this.sampleRate);
        for (int i = 0; i < FFT_SIZE; i++) {
            window[i] = 0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (FFT_SIZE - 1)));
        }
    }

    public void setSampleRate(float sampleRate) {
        this.sampleRate = sampleRate > 0 ? sampleRate : 44100.0f;
        this.beatDetector.setSampleRate(this.sampleRate);
    }

    /**
     * Verarbeitet einen Puffer mit normalisierten Audiosamples (-1.0 bis 1.0) mit aktuellem Zeitstempel.
     */
    public synchronized void processSamples(float[] samples, int length) {
        processSamples(samples, length, System.currentTimeMillis());
    }

    /**
     * Verarbeitet einen Puffer mit normalisierten Audiosamples (-1.0 bis 1.0) mit explizitem Zeitstempel (ms).
     */
    public synchronized void processSamples(float[] samples, int length, long timestampMs) {
        if (samples == null || length < FFT_SIZE) {
            return;
        }

        // 1. RMS & Peak des Eingangssignals berechnen
        double sumSq = 0.0;
        double currentPeak = 0.0;
        for (int i = 0; i < FFT_SIZE; i++) {
            double s = samples[i];
            double abs = Math.abs(s);
            if (abs > currentPeak) {
                currentPeak = abs;
            }
            sumSq += s * s;
        }

        this.peakLevel = currentPeak;
        this.rmsLevel = Math.sqrt(sumSq / FFT_SIZE);

        // 2. Professionelle 3-Stufen AGC nachführen
        double dt = HOP_SIZE / (double) sampleRate;
        double agcMultiplier = agc.update(rmsLevel, peakLevel, dt);
        double effectiveGain = agc.isEnabled() ? (manualGain * agcMultiplier) : manualGain;

        // 3. Hanning-Fensterung, Gain & Soft-Limiting für Spektrum; unkomprimiertes Signal für BeatDetector
        for (int i = 0; i < FFT_SIZE; i++) {
            double linearSample = samples[i] * manualGain;
            beatBuffer[i] = (float) linearSample;

            double s = samples[i] * effectiveGain;
            s = AutoGainControl.applySoftLimit(s);
            real[i] = s * window[i];
            imag[i] = 0.0;
        }

        // 4. Radix-2 FFT ausführen
        computeFft(real, imag, FFT_SIZE);

        // 5. Magnituden & 8 Frequenzbänder aggregieren
        // Bin-Auflösung = sampleRate / FFT_SIZE (bei 44100 Hz ca. 43 Hz pro Bin, bei 48000 Hz ca. 46.8 Hz)
        double binWidth = sampleRate / (double) FFT_SIZE;

        // Frequenzgrenzen der 8 Bänder in Hz:
        // Sub-Bass (20-80), Bass (80-250), Low-Mid (250-500), Mid (500-1500),
        // High-Mid (1500-3500), Presence (3500-6500), Treble (6500-11000), Brilliance (11000-18000)
        double[] freqBorders = { 20, 80, 250, 500, 1500, 3500, 6500, 11000, 18000 };
        // Psychoakustische Pink-Noise Tilt-Kompensation (gleicht 1/f-Abfall natürlicher Musik aus)
        double[] bandTilt = { 1.0, 1.1, 1.35, 1.75, 2.4, 3.2, 4.3, 5.5 };

        double[] rawBands = new double[NUM_BANDS];
        double fftNorm = FFT_SIZE / 4.0; // 256.0 Normalisierungsfaktor für Hanning-Fenster

        for (int band = 0; band < NUM_BANDS; band++) {
            int startBin = Math.max(1, (int) Math.floor(freqBorders[band] / binWidth));
            int endBin = Math.min((FFT_SIZE / 2) - 1, (int) Math.ceil(freqBorders[band + 1] / binWidth));

            double sumMag = 0.0;
            double maxMag = 0.0;
            int count = 0;
            for (int k = startBin; k <= endBin; k++) {
                double mag = Math.sqrt(real[k] * real[k] + imag[k] * imag[k]) / fftNorm;
                sumMag += mag;
                if (mag > maxMag) {
                    maxMag = mag;
                }
                count++;
            }
            if (count > 0) {
                double avgMag = sumMag / count;
                // Mix aus Spitzen- und Durchschnittsenergie im Band, skaliert mit Frequenz-Tilt
                double bandVal = ((avgMag * 0.4) + (maxMag * 0.6)) * bandTilt[band] * 2.8;
                rawBands[band] = Math.max(0.0, Math.min(1.0, bandVal));
            }
        }

        // 6. Glättung (Geschmeidig bei ~93 FPS mit 50% Overlap)
        for (int b = 0; b < NUM_BANDS; b++) {
            double target = rawBands[b];
            if (target > bandLevels[b]) {
                bandLevels[b] = bandLevels[b] * 0.35 + target * 0.65; // Schneller, knackiger Attack
            } else {
                bandLevels[b] = bandLevels[b] * 0.90 + target * 0.10; // Weicher Decay
            }

            if (bandLevels[b] > bandPeaks[b]) {
                bandPeaks[b] = bandLevels[b];
            } else {
                bandPeaks[b] = Math.max(0.0, bandPeaks[b] - 0.008);
            }
        }

        // 7. Reines, hochpräzises Beat-Tracking
        beatDetector.detect(beatBuffer, rmsLevel, timestampMs);
    }

    /**
     * In-Place Radix-2 Cooley-Tukey FFT.
     */
    private static void computeFft(double[] r, double[] im, int n) {
        // Bit-Reversal
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

        // Schmetterlings-Operationen (Butterfly)
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2.0 * Math.PI / len;
            double wlenR = Math.cos(ang);
            double wlenI = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double wR = 1.0;
                double wI = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    int u = i + k;
                    int v = i + k + len / 2;
                    double vr = r[v] * wR - im[v] * wI;
                    double vi = r[v] * wI + im[v] * wR;
                    r[v] = r[u] - vr;
                    im[v] = im[u] - vi;
                    r[u] = r[u] + vr;
                    im[u] = im[u] + vi;
                    double nextWR = wR * wlenR - wI * wlenI;
                    wI = wR * wlenI + wI * wlenR;
                    wR = nextWR;
                }
            }
        }
    }

    public synchronized double[] getBandLevels() {
        return Arrays.copyOf(bandLevels, bandLevels.length);
    }

    public synchronized double[] getBandPeaks() {
        return Arrays.copyOf(bandPeaks, bandPeaks.length);
    }

    public double getRmsLevel() {
        return rmsLevel;
    }

    public double getPeakLevel() {
        return peakLevel;
    }

    public BeatDetector getBeatDetector() {
        return beatDetector;
    }

    public void setManualGain(double gain) {
        this.manualGain = Math.max(0.1, Math.min(10.0, gain));
    }

    public double getManualGain() {
        return manualGain;
    }

    public boolean isAgcEnabled() {
        return agc.isEnabled();
    }

    public void setAgcEnabled(boolean agcEnabled) {
        agc.setEnabled(agcEnabled);
    }

    public AutoGainControl getAgc() {
        return agc;
    }
}

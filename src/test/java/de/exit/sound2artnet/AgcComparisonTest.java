package de.exit.sound2artnet;

import de.exit.sound2artnet.audio.AudioSpectrumAnalyzer;
import de.exit.sound2artnet.audio.BeatDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AgcComparisonTest {
    public static void main(String[] args) throws Exception {
        File rawFile = new File("audio_mono_44k.raw");
        byte[] audioBytes = java.nio.file.Files.readAllBytes(rawFile.toPath());

        System.out.println("=== TEST 1: AGC AKTIVIERT (Standard) ===");
        runTest(audioBytes, true);

        System.out.println("\n=== TEST 2: AGC DEAKTIVIERT (Pures Audio-Signal) ===");
        runTest(audioBytes, false);
    }

    private static void runTest(byte[] audioBytes, boolean agcEnabled) {
        float sampleRate = 44100.0f;
        AudioSpectrumAnalyzer analyzer = new AudioSpectrumAnalyzer(sampleRate);
        analyzer.setAgcEnabled(agcEnabled);
        analyzer.setManualGain(1.0);

        int fftSize = AudioSpectrumAnalyzer.FFT_SIZE;
        int hopSize = AudioSpectrumAnalyzer.HOP_SIZE;
        float[] ringBuffer = new float[fftSize];
        int samplesCollected = 0;
        long totalSamples = 0;

        List<Double> kicks = new ArrayList<>();
        List<Double> snares = new ArrayList<>();
        List<Double> hats = new ArrayList<>();

        for (int i = 0; i + 1 < audioBytes.length; i += 2) {
            short val = (short) (((audioBytes[i + 1] & 0xFF) << 8) | (audioBytes[i] & 0xFF));
            ringBuffer[samplesCollected++] = val / 32768.0f;
            totalSamples++;

            if (samplesCollected == fftSize) {
                double currentSec = (totalSamples - fftSize) / (double) sampleRate;
                long currentMs = (long) (currentSec * 1000.0);

                analyzer.processSamples(ringBuffer, fftSize, currentMs);
                BeatDetector bd = analyzer.getBeatDetector();

                if (bd.isKick()) kicks.add(currentSec);
                if (bd.isSnare()) snares.add(currentSec);
                if (bd.isHat()) hats.add(currentSec);

                System.arraycopy(ringBuffer, hopSize, ringBuffer, 0, hopSize);
                samplesCollected = hopSize;
            }
        }

        long introK = kicks.stream().filter(t -> t >= 0.0 && t < 8.0).count();
        long electroK = kicks.stream().filter(t -> t >= 8.0 && t < 38.0).count();
        long electroS = snares.stream().filter(t -> t >= 8.0 && t < 38.0).count();
        long electroH = hats.stream().filter(t -> t >= 8.0 && t < 38.0).count();
        long dropK = kicks.stream().filter(t -> t >= 38.0 && t < 54.0).count();
        long fadeoutK = kicks.stream().filter(t -> t >= 303.0).count();

        // BPM Schätzung
        List<Double> secKicks = kicks.stream().filter(t -> t >= 8.0 && t < 38.0).toList();
        double sumInt = 0;
        int countInt = 0;
        for (int k = 1; k < secKicks.size(); k++) {
            double diff = secKicks.get(k) - secKicks.get(k - 1);
            if (diff >= 0.35 && diff <= 0.65) {
                sumInt += diff;
                countInt++;
            }
        }
        double bpm = countInt > 0 ? (60.0 / (sumInt / countInt)) : 0.0;

        System.out.printf("  1. Intro (0-8s)   : Kicks = %d (Soll: 0)\n", introK);
        System.out.printf("  2. Electro (8-38s): Kicks = %d (BPM: %.1f, Soll: ~60 Kicks), Snares = %d (Soll: ~30), Hats = %d\n",
                electroK, bpm, electroS, electroH);
        System.out.printf("  3. Drop (38-54s)  : Kicks = %d, Snares = %d, Hats = %d\n",
                dropK, snares.stream().filter(t -> t >= 38.0 && t < 54.0).count(), hats.stream().filter(t -> t >= 38.0 && t < 54.0).count());
        System.out.printf("  4. Fadeout (303s+): Kicks = %d (Soll: 0)\n", fadeoutK);
    }
}

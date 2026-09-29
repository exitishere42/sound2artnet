package de.exit.sound2artnet;

import de.exit.sound2artnet.audio.AudioSpectrumAnalyzer;
import de.exit.sound2artnet.audio.BeatDetector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AudioSpectrumAnalyzerTest {

    @Test
    public void testSineWaveFrequencyDetection() {
        float sampleRate = 44100.0f;
        AudioSpectrumAnalyzer analyzer = new AudioSpectrumAnalyzer(sampleRate);
        analyzer.setAgcEnabled(false);
        analyzer.setManualGain(1.0);

        // Erzeuge ein 100 Hz Sinussignal (sollte Band 1 [Bass/Kick, 80-250Hz] anregen)
        float[] samples = new float[AudioSpectrumAnalyzer.FFT_SIZE];
        double freq = 100.0;
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin((2.0 * Math.PI * freq * i) / sampleRate);
        }

        // Mehrere Puffer verarbeiten zur Glättung
        for (int k = 0; k < 5; k++) {
            analyzer.processSamples(samples, samples.length);
        }

        double[] bands = analyzer.getBandLevels();
        assertEquals(AudioSpectrumAnalyzer.NUM_BANDS, bands.length);

        // Band 1 (Bass) muss signifikant höher sein als die hohen Bänder
        assertTrue(bands[1] > 0.05, "Bass Band 1 sollte angeregt sein");
        assertTrue(bands[1] > bands[6], "Bass sollte deutlich stärker sein als Treble");
        assertTrue(analyzer.getRmsLevel() > 0.4, "RMS Pegel muss messbar sein");
    }

    @Test
    public void testBeatDetectorTrigger() {
        BeatDetector detector = new BeatDetector();

        // Zunächst Stille / leises Rauschen einspeisen
        for (int i = 0; i < 40; i++) {
            detector.process(0.05);
            assertFalse(detector.isBeat());
        }

        // Plötzlicher lauter Kick-Drum Impuls
        try {
            Thread.sleep(170); // Warte Mindestintervall ab
        } catch (InterruptedException ignored) {}

        detector.process(0.95);
        assertTrue(detector.isBeat(), "Starker Energieanstieg muss als Beat erkannt werden");
    }

    @Test
    public void testBeatDetectorWithHeavySubBassDrone() {
        BeatDetector detector = new BeatDetector();

        // Simuliere lauten, dauerhaften Sub-Bass / 808-Brummen (Pegel dauerhaft bei 0.85)
        for (int i = 0; i < 40; i++) {
            detector.process(0.01, 0.85); // Kaum Onset-Flux, aber massiver Dauerpegel
            assertFalse(detector.isBeat(), "Dauerhafter Sub-Bass darf keinen Dauer-Beat auslösen");
        }

        try {
            Thread.sleep(170); // Mindest-Abstand
        } catch (InterruptedException ignored) {}

        // Jetzt schlägt ein echter Kick ein (starker Transienten-Flux)
        detector.process(0.70, 0.98);
        assertTrue(detector.isBeat(), "Kick-Drum muss trotz lautem Dauer-Subbass erkannt werden!");
    }

    @Test
    public void testRealisticMusicLevelDoesNotSaturateBands() {
        float sampleRate = 48000.0f;
        AudioSpectrumAnalyzer analyzer = new AudioSpectrumAnalyzer(sampleRate);
        analyzer.setAgcEnabled(true);
        analyzer.setManualGain(1.0);

        // Erzeuge ein realistisches Musiksignal mit RMS ~0.07 und Peak ~0.19 (wie im User-Screenshot)
        float[] samples = new float[AudioSpectrumAnalyzer.FFT_SIZE];
        for (int i = 0; i < samples.length; i++) {
            // Bassline (100 Hz) + Melodie (440 Hz) + Hi-Hats (8000 Hz)
            double sig = 0.12 * Math.sin(2.0 * Math.PI * 100.0 * i / sampleRate)
                       + 0.05 * Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate)
                       + 0.02 * Math.sin(2.0 * Math.PI * 8000.0 * i / sampleRate);
            samples[i] = (float) sig;
        }

        for (int k = 0; k < 10; k++) {
            analyzer.processSamples(samples, samples.length);
        }

        double[] bands = analyzer.getBandLevels();
        // Kein Band darf auf 100% (1.0) flatlinen / saturieren!
        for (int b = 0; b < bands.length; b++) {
            assertTrue(bands[b] < 0.95, "Band " + b + " darf bei normaler Lautstärke nicht auf 100% übersteuern (aktuell: " + bands[b] + ")");
        }
    }

    @Test
    public void testRealKickAudioTriggersBeat() throws InterruptedException {
        float sampleRate = 48000.0f;
        AudioSpectrumAnalyzer analyzer = new AudioSpectrumAnalyzer(sampleRate);
        analyzer.setAgcEnabled(true);
        analyzer.setManualGain(1.0);

        // 1. Hintergrund-Track (leise Hi-Hat / Melodie, kein Kick)
        float[] quietSamples = new float[AudioSpectrumAnalyzer.FFT_SIZE];
        for (int i = 0; i < quietSamples.length; i++) {
            quietSamples[i] = (float) (0.02 * Math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate));
        }

        for (int k = 0; k < 20; k++) {
            analyzer.processSamples(quietSamples, quietSamples.length);
        }
        assertFalse(analyzer.getBeatDetector().isBeat(), "Kein Beat ohne Kick");

        Thread.sleep(170);

        // 2. Ein harter Kick schlägt ein (100 Hz, Amplitude 0.35)
        float[] kickSamples = new float[AudioSpectrumAnalyzer.FFT_SIZE];
        for (int i = 0; i < kickSamples.length; i++) {
            // Kick Transient: 100 Hz Sinus mit abklingender Hüllkurve
            double env = Math.exp(-3.0 * i / (double) AudioSpectrumAnalyzer.FFT_SIZE);
            kickSamples[i] = (float) (0.45 * Math.sin(2.0 * Math.PI * 90.0 * i / sampleRate) * env);
        }

        analyzer.processSamples(kickSamples, kickSamples.length);
        assertTrue(analyzer.getBeatDetector().isBeat(), "Echter Audio-Kick muss vom BeatDetector erkannt werden!");
    }

    @Test
    public void testBeatmaker95BpmDetectsAllBeats() {
        // 95 BPM entspricht exakt 631.5 ms Intervall zwischen den Kicks
        BeatDetector detector = new BeatDetector();

        int detectedBeats = 0;
        long now = 1000L;

        // 4 Taktschläge / Kicks simulieren
        for (int beat = 0; beat < 4; beat++) {
            detector.process(0.40, 0.55);
            if (detector.isBeat()) {
                detectedBeats++;
            }

            // Zwischen den Kicks: leises Hintergrundniveau
            for (int pause = 0; pause < 30; pause++) {
                detector.process(0.002, 0.005);
            }

            try {
                Thread.sleep(170);
            } catch (InterruptedException ignored) {}
        }

        assertEquals(4, detectedBeats, "Ein 95 BPM Beatmaker mit 4 Kicks muss exakt 4 von 4 Beats (100%) erkennen!");
    }

    @Test
    public void testVariousTemposDetectCleanly() {
        double[] bpms = { 95.0, 120.0, 140.0, 175.0 };
        float sampleRate = 44100.0f;

        for (BeatDetector.DetectionMode mode : BeatDetector.DetectionMode.values()) {
            if (mode == BeatDetector.DetectionMode.MANUAL) continue;
            for (double bpm : bpms) {
                BeatDetector detector = new BeatDetector(1024, sampleRate);
                detector.setDetectionMode(mode);
                int samplesPerBeat = (int) (sampleRate * 60.0 / bpm);
                int totalBeats = 8;
                int totalSamples = samplesPerBeat * totalBeats;
                float[] audio = new float[totalSamples];

                int kickLen = (int) (sampleRate * Math.min(0.25, 30.0 / bpm));
                for (int b = 0; b < totalBeats; b++) {
                    int start = b * samplesPerBeat;
                    for (int i = 0; i < kickLen && (start + i) < totalSamples; i++) {
                        double t = i / (double) sampleRate;
                        double phase = 2.0 * Math.PI * (45.0 * t - (85.0 / 30.0) * Math.exp(-t * 30.0));
                        double env = Math.exp(-t * 18.0);
                        audio[start + i] = (float) (0.8 * Math.sin(phase) * env);
                    }
                }

                int detectedCount = 0;
                float[] buffer = new float[1024];
                int totalHops = (totalSamples - 1024) / 512;

                for (int h = 0; h < totalHops; h++) {
                    int offset = h * 512;
                    System.arraycopy(audio, offset, buffer, 0, 1024);
                    long timeMs = (long) ((offset / (double) sampleRate) * 1000.0);

                    double sumSq = 0;
                    for (int i = 0; i < 1024; i++) sumSq += buffer[i] * buffer[i];
                    double rms = Math.sqrt(sumSq / 1024.0);

                    detector.detect(buffer, rms, timeMs);
                    if (detector.isBeat()) {
                        detectedCount++;
                    }
                }

                assertEquals(totalBeats, detectedCount, "Modus " + mode + " bei Tempo " + bpm + " BPM muss exakt " + totalBeats + " Beats erkennen!");
                assertEquals(bpm, detector.getEstimatedBpm(), 6.0, "Geschätzte BPM muss nahe " + bpm + " liegen!");
            }
        }
    }

    @Test
    public void testBTrackExpectancyCatchesWeakBeatsInBar() {
        // Simuliert einen 95-BPM-Beatmaker, bei dem der 3. Schlag im Takt deutlich schwächer/leiser ist
        double bpm = 95.0;
        float sampleRate = 44100.0f;
        int samplesPerBeat = (int) (sampleRate * 60.0 / bpm);
        int totalBeats = 8;
        int totalSamples = samplesPerBeat * totalBeats;
        float[] audio = new float[totalSamples];

        int kickLen = (int) (sampleRate * 0.22);
        for (int b = 0; b < totalBeats; b++) {
            int start = b * samplesPerBeat;
            // Jeder 3. Schlag im 4/4-Takt (Index 2, 6) ist deutlich schwächer (Amplitude 0.42 statt 0.85)
            double amp = (b % 4 == 2) ? 0.42 : 0.85;
            for (int i = 0; i < kickLen && (start + i) < totalSamples; i++) {
                double t = i / (double) sampleRate;
                double phase = 2.0 * Math.PI * (45.0 * t - (85.0 / 30.0) * Math.exp(-t * 30.0));
                double env = Math.exp(-t * 18.0);
                audio[start + i] = (float) (amp * Math.sin(phase) * env);
            }
        }

        for (BeatDetector.DetectionMode mode : BeatDetector.DetectionMode.values()) {
            if (mode == BeatDetector.DetectionMode.MANUAL) continue;
            BeatDetector detector = new BeatDetector(1024, sampleRate);
            detector.setDetectionMode(mode);

            int detectedCount = 0;
            float[] buffer = new float[1024];
            int totalHops = (totalSamples - 1024) / 512;

            for (int h = 0; h < totalHops; h++) {
                int offset = h * 512;
                System.arraycopy(audio, offset, buffer, 0, 1024);
                long timeMs = (long) ((offset / (double) sampleRate) * 1000.0);

                double sumSq = 0;
                for (int i = 0; i < 1024; i++) sumSq += buffer[i] * buffer[i];
                double rms = Math.sqrt(sumSq / 1024.0);

                detector.detect(buffer, rms, timeMs);
                if (detector.isBeat()) {
                    detectedCount++;
                }
            }

            assertEquals(totalBeats, detectedCount, "BTrack-Erwartungsfenster (" + mode + ") muss trotz schwachem 3. Beat alle 8 von 8 Schlägen erkennen!");
            assertEquals(95.0, detector.getEstimatedBpm(), 5.0, "BPM-Schätzer muss ~95 BPM erkennen!");
        }
    }

    @Test
    public void testSilenceAndBackgroundNoiseNeverTriggerBeats() {
        BeatDetector detector = new BeatDetector();

        // 100 Blöcke Stille / leises Raumrauschen (RMS ~0.005)
        float[] quiet = new float[1024];
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < quiet.length; i++) {
            quiet[i] = (float) (0.005 * (rnd.nextDouble() * 2.0 - 1.0));
        }

        for (int k = 0; k < 100; k++) {
            detector.detect(quiet, 0.005, k * 20L);
            assertFalse(detector.isBeat(), "Stille und Raumrauschen dürfen unter keinen Umständen Beats auslösen!");
        }
    }

    @Test
    public void testSchmittTriggerHysteresisPreventsSustainedBassRetrigger() {
        // Testet das Schmitt-Trigger-Prinzip (Max Mitchell): Ein durchgehender Sub-Bass-Ton (80 Hz)
        // darf beim Einsetzen genau 1x auslösen, aber solange er nicht unter 35% des Maximums fällt,
        // nicht erneut triggern (selbst nach Ablauf des Cooldowns).
        BeatDetector detector = new BeatDetector(1024, 44100.0f);
        detector.setDetectionMode(BeatDetector.DetectionMode.BEAT_DETECT);

        float[] sustainedBass = new float[1024];
        for (int i = 0; i < 1024; i++) {
            sustainedBass[i] = (float) (0.8 * Math.sin(2.0 * Math.PI * 55.0 * i / 44100.0));
        }

        int beatsDuringSustained = 0;
        // 50 Frames (1 Sekunde lang durchgehender 55-Hz-Sub-Bass ohne Abfall unter 35%)
        for (int k = 0; k < 50; k++) {
            detector.detect(sustainedBass, 0.56, k * 20L);
            if (detector.isBeat()) {
                beatsDuringSustained++;
            }
        }
        assertEquals(1, beatsDuringSustained, "Durchgehender Bass ohne Pegelabfall unter 35% darf durch den Schmitt-Trigger-Latch nur genau 1x auslösen!");

        // Jetzt fällt das Signal für einige Frames unter 35% (Reset des Latches)
        float[] quietDrop = new float[1024];
        for (int k = 50; k < 55; k++) {
            detector.detect(quietDrop, 0.0, k * 20L);
        }

        // Nächster Bass-Schlag nach dem Abfall muss sofort wieder auslösen
        detector.detect(sustainedBass, 0.56, 1150L);
        assertTrue(detector.isBeat(), "Nach Abfall unter 35% (Latch-Reset) muss der nächste Bass-Schlag sofort wieder auslösen!");
    }

    @Test
    public void testHardTechnoFastKicksWithRumbleBassAndVocals() {
        // Simuliert einen 156-BPM Hard-Techno-Track (wie "Toter Schmetterling"):
        // - Schnelle 156 BPM Kicks (alle ~384.6 ms)
        // - Durchgehender Offbeat-Rumble-Bass (48 Hz & 92 Hz), sodass der Bass zwischen den Kicks NIE unter 45% fällt
        // - Durchgehende Acid-Synth/Vocal-Spur (210 Hz & 340 Hz)
        // - Erster Kick extrem laut (Drop 0.95), folgende Kicks variieren zwischen 0.72 und 0.86
        double bpm = 156.0;
        float sampleRate = 48000.0f;
        int samplesPerBeat = (int) Math.round(sampleRate * 60.0 / bpm);
        int totalBeats = 12;
        int totalSamples = samplesPerBeat * totalBeats;
        float[] audio = new float[totalSamples];

        // 1. Durchgehender Rumble-Bass + Acid/Vocal-Hintergrund aufbauen (liegt zwischen den Kicks bei ~45-50% von Max,
        // fällt also NIE unter die alte 35%-Schwelle!)
        for (int i = 0; i < totalSamples; i++) {
            double t = i / (double) sampleRate;
            double rumble = 0.22 * Math.sin(2.0 * Math.PI * 48.0 * t)
                          + 0.16 * Math.sin(2.0 * Math.PI * 92.0 * t);
            double vocalSynth = 0.14 * Math.sin(2.0 * Math.PI * 215.0 * t);
            audio[i] = (float) (rumble + vocalSynth);
        }

        // 2. 12 Hard-Techno-Kicks mit realistischer Sidechain-Hüllkurve darüberlegen
        double[] kickAmps = { 0.95, 0.78, 0.82, 0.75, 0.85, 0.74, 0.80, 0.76, 0.84, 0.75, 0.81, 0.77 };
        for (int b = 0; b < totalBeats; b++) {
            int start = b * samplesPerBeat;
            double amp = kickAmps[b];
            for (int i = 0; i < samplesPerBeat && (start + i) < totalSamples; i++) {
                double t = i / (double) sampleRate;
                double phase = 2.0 * Math.PI * (54.0 * t - (95.0 / 35.0) * Math.exp(-t * 35.0));
                double kickEnv = Math.exp(-t * 18.0);
                double kickSample = amp * Math.sin(phase) * kickEnv;
                // Weiche Sidechain-Ducking-Kurve während des Kicks, die zum Offbeat sanft auf 1.0 aufgeht
                double sidechain = 1.0 - 0.65 * Math.exp(-t * 14.0);
                audio[start + i] = (float) Math.max(-1.0, Math.min(1.0, audio[start + i] * sidechain + kickSample));
            }
        }

        for (BeatDetector.DetectionMode mode : BeatDetector.DetectionMode.values()) {
            if (mode == BeatDetector.DetectionMode.MANUAL) continue;
            AudioSpectrumAnalyzer analyzer = new AudioSpectrumAnalyzer(sampleRate);
            analyzer.getBeatDetector().setDetectionMode(mode);

            int consumedBy40FpsEngine = 0;
            float[] buffer = new float[1024];
            int totalHops = (totalSamples - 1024) / 512;
            long lastEngineTickMs = -25;

            for (int h = 0; h < totalHops; h++) {
                int offset = h * 512;
                System.arraycopy(audio, offset, buffer, 0, 1024);
                long timeMs = Math.round((offset / (double) sampleRate) * 1000.0);

                analyzer.processSamples(buffer, 1024, timeMs);

                // Simuliere den asynchronen 40-FPS-Engine-Tick (alle 25 ms), der consumeBeat() abfragt
                if (timeMs - lastEngineTickMs >= 25) {
                    lastEngineTickMs = timeMs;
                    if (analyzer.getBeatDetector().consumeBeat()) {
                        consumedBy40FpsEngine++;
                    }
                }
            }
            if (analyzer.getBeatDetector().consumeBeat()) {
                consumedBy40FpsEngine++;
            }

            assertEquals(totalBeats, consumedBy40FpsEngine,
                "Hard-Techno 156 BPM (" + mode + ") muss alle " + totalBeats + " Kicks trotz Rumble-Bass, AGC und 40-FPS-Polling erkennen!");
            assertEquals(bpm, analyzer.getBeatDetector().getEstimatedBpm(), 6.0,
                "Geschätztes Tempo bei Hard-Techno (" + mode + ") muss nahe 156 BPM liegen!");
        }
    }

    @Test
    public void testBeatSensitivitySliderAffectsTriggerThreshold() {
        float sampleRate = 44100.0f;
        float[] loudKick = new float[1024];
        float[] mediumKick = new float[1024];
        float[] quietBetween = new float[1024];

        for (int i = 0; i < 1024; i++) {
            double t = i / (double) sampleRate;
            double env = Math.exp(-t * 20.0);
            loudKick[i] = (float) (0.90 * Math.sin(2.0 * Math.PI * 60.0 * t) * env);
            mediumKick[i] = (float) (0.58 * Math.sin(2.0 * Math.PI * 60.0 * t) * env);
            quietBetween[i] = (float) (0.05 * Math.sin(2.0 * Math.PI * 60.0 * t));
        }

        // 1. Niedrige Empfindlichkeit (0.4 = 40%): Nur der laute Kick (0.90) löst aus, der mittelstarke (0.58) wird ignoriert
        BeatDetector strictDetector = new BeatDetector(1024, sampleRate);
        strictDetector.setDetectionMode(BeatDetector.DetectionMode.BEAT_DETECT);
        strictDetector.setSensitivity(0.4);

        strictDetector.detect(loudKick, 0.65, 0L);
        assertTrue(strictDetector.isBeat(), "Erster lauter Kick muss auslösen");
        strictDetector.detect(quietBetween, 0.03, 250L);
        strictDetector.detect(mediumKick, 0.40, 500L);
        assertFalse(strictDetector.isBeat(), "Bei niedriger Empfindlichkeit (40%) darf ein schwächerer Kick (58%) nicht auslösen");

        // 2. Hohe Empfindlichkeit (1.8 = 180%): Auch der schwächere Kick (0.58) löst nach dem lauten Kick sicher aus
        BeatDetector sensitiveDetector = new BeatDetector(1024, sampleRate);
        sensitiveDetector.setDetectionMode(BeatDetector.DetectionMode.BEAT_DETECT);
        sensitiveDetector.setSensitivity(1.8);

        sensitiveDetector.detect(loudKick, 0.65, 0L);
        assertTrue(sensitiveDetector.isBeat(), "Erster lauter Kick muss auslösen");
        sensitiveDetector.detect(quietBetween, 0.03, 250L);
        sensitiveDetector.detect(mediumKick, 0.40, 500L);
        assertTrue(sensitiveDetector.isBeat(), "Bei hoher Empfindlichkeit (180%) muss auch der schwächere Kick (58%) zuverlässig auslösen");
    }
}

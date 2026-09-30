package de.exit.sound2artnet;

import de.exit.sound2artnet.audio.BeatDetector;
import de.exit.sound2artnet.midi.MidiInputService;
import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class MidiAndManualBeatTest {

    @Test
    public void testManualDetectionModeIgnoresAudioAndUsesTapTempo() {
        BeatDetector bd = new BeatDetector(1024, 44100.0f);
        bd.setDetectionMode(BeatDetector.DetectionMode.MANUAL);

        // Lautes Audio-Signal darf im manuellen Modus keinen Beat auslösen
        float[] loudSamples = new float[1024];
        for (int i = 0; i < 1024; i++) {
            loudSamples[i] = (float) Math.sin(2.0 * Math.PI * 60.0 * (i / 44100.0));
        }
        bd.detect(loudSamples, 0.8, 1.0, 1000L);
        assertFalse(bd.consumeBeat(), "Audio darf im Modus MANUAL keinen Beat auslösen");

        // Manuelle Taps bei 120 BPM (alle 500 ms)
        bd.triggerManualBeat(2000L);
        assertTrue(bd.consumeBeat(), "Erster Tap muss sofort einen Beat auslösen");

        bd.triggerManualBeat(2500L);
        assertTrue(bd.consumeBeat(), "Zweiter Tap muss sofort einen Beat auslösen");
        assertEquals(120.0, bd.getEstimatedBpm(), 0.5, "Nach 2 Taps im 500ms-Abstand muss BPM = 120 sein");

        bd.triggerManualBeat(3000L);
        bd.triggerManualBeat(3500L);
        assertEquals(120.0, bd.getEstimatedBpm(), 0.1);

        bd.resetManualTempo();
        assertEquals(0.0, bd.getEstimatedBpm(), 0.01);
    }

    @Test
    public void testMidiLearnAndNoteFiltering() {
        MidiInputService midi = new MidiInputService();
        AtomicInteger beatCount = new AtomicInteger(0);
        midi.setOnBeatTrigger(beatCount::incrementAndGet);

        // Standardmäßig ("ANY") löst jede Note-On mit Velocity > 0 aus
        midi.handleShortMessage(ShortMessage.NOTE_ON, 0, 60, 100);
        assertEquals(1, beatCount.get());

        // Note-On mit Velocity 0 (= Note-Off) darf nicht auslösen
        midi.handleShortMessage(ShortMessage.NOTE_ON, 0, 60, 0);
        assertEquals(1, beatCount.get());

        // MIDI Learn aktivieren und Taste Note 36 (C2) auf Kanal 2 (Index 1) drücken
        midi.setLearning(true);
        assertTrue(midi.isLearning());
        midi.handleShortMessage(ShortMessage.NOTE_ON, 1, 36, 127);

        assertFalse(midi.isLearning(), "MIDI Learn muss nach Tastendruck beendet sein");
        assertEquals("NOTE", midi.getBoundType());
        assertEquals(1, midi.getBoundChannel());
        assertEquals(36, midi.getBoundData1());
        assertEquals(2, beatCount.get(), "Angelernte Taste löst gleichzeitig den Beat aus");

        // Andere Taste (Note 60) darf jetzt NICHT mehr auslösen
        midi.handleShortMessage(ShortMessage.NOTE_ON, 1, 60, 100);
        assertEquals(2, beatCount.get());

        // Angelernte Taste (Note 36, Ch 1) löst wieder aus
        midi.handleShortMessage(ShortMessage.NOTE_ON, 1, 36, 90);
        assertEquals(3, beatCount.get());

        // Reset auf Alle Tasten
        midi.clearBinding();
        midi.handleShortMessage(ShortMessage.NOTE_ON, 0, 60, 100);
        assertEquals(4, beatCount.get());
    }

    @Test
    public void testLinuxAlsaSequencerAndRawMidiParsing() {
        MidiInputService midi = new MidiInputService();
        AtomicInteger beatCount = new AtomicInteger(0);
        midi.setOnBeatTrigger(beatCount::incrementAndGet);

        // 1. Linux ALSA Sequencer (`aseqdump -p <port>`) Note-On & Control-Change Zeilen
        midi.setLearning(true);
        midi.parseAseqdumpLine(" 20:0   Note on                 0, note 48, velocity 110");
        assertFalse(midi.isLearning());
        assertEquals("NOTE", midi.getBoundType());
        assertEquals(0, midi.getBoundChannel());
        assertEquals(48, midi.getBoundData1());
        assertEquals(1, beatCount.get());

        // Andere Note über aseqdump wird ignoriert
        midi.parseAseqdumpLine(" 20:0   Note on                 0, note 60, velocity 100");
        assertEquals(1, beatCount.get());

        // Gebundene Note 48 über aseqdump löst aus
        midi.parseAseqdumpLine(" 20:0   Note on                 0, note 48, velocity 95");
        assertEquals(2, beatCount.get());

        // 2. Linux ALSA RawMIDI (`amidi -p hw:1,0,0 -d`) Hex-Bytes ("90 30 7F" = Note On Ch 0, Note 48, Vel 127)
        midi.parseAmidiHexLine("90 30 7F");
        assertEquals(3, beatCount.get());

        // 3. CC-Pedal (Control Change 64) über aseqdump anlernen
        midi.setLearning(true);
        midi.parseAseqdumpLine(" 20:0   Control change          0, controller 64, value 127");
        assertEquals("CC", midi.getBoundType());
        assertEquals(64, midi.getBoundData1());
        assertEquals(4, beatCount.get());

        // Pedal loslassen (< 64) und erneut drücken (>= 64)
        midi.parseAseqdumpLine(" 20:0   Control change          0, controller 64, value 0");
        midi.parseAseqdumpLine(" 20:0   Control change          0, controller 64, value 127");
        assertEquals(5, beatCount.get());
    }

    @Test
    public void testMidiButtonHoldSustainsBeatForExactPressDuration() {
        de.exit.sound2artnet.audio.AudioCaptureService audio = new de.exit.sound2artnet.audio.AudioCaptureService();
        de.exit.sound2artnet.artnet.ArtNetSender sender = new de.exit.sound2artnet.artnet.ArtNetSender();
        de.exit.sound2artnet.engine.Sound2LightEngine engine = new de.exit.sound2artnet.engine.Sound2LightEngine(audio, sender);
        engine.setDimmerMode(de.exit.sound2artnet.engine.Sound2LightEngine.DimmerMode.BEAT_PULSE);

        var profile = de.exit.sound2artnet.fixture.FixtureLibrary.getDefaultProfiles().get(0);
        var patch = new de.exit.sound2artnet.fixture.FixturePatch("Test MH", 1, profile);
        engine.setPatchedFixtures(java.util.List.of(patch));

        BeatDetector bd = audio.getAnalyzer().getBeatDetector();
        bd.setDetectionMode(BeatDetector.DetectionMode.MANUAL);

        MidiInputService midi = new MidiInputService();
        midi.setOnBeatTrigger(bd::triggerManualBeat);
        midi.setOnBeatHoldChange(bd::setManualBeatHeld);

        // MIDI-Taste drücken und gedrückt halten (Note On, Ch 0, Note 60, Vel 127)
        midi.handleShortMessage(ShortMessage.NOTE_ON, 0, 60, 127);
        assertTrue(midi.isBeatHeld(), "MIDI-Taste muss als gehalten erkannt werden");
        assertTrue(bd.isManualBeatHeld(), "BeatDetector muss gehaltenen Beat melden");

        // Über 40 Ticks (~1 Sekunde Haltedauer) muss der Beat durchgehend aktiv bleiben (kein Abklingen!)
        for (int i = 0; i < 40; i++) {
            engine.tick();
            assertTrue(engine.isLastTickBeat(), "Beat muss während der gesamten Haltedauer aktiv bleiben (Tick " + i + ")");
        }

        // MIDI-Taste loslassen (Note Off bzw. Note On Velocity 0)
        midi.handleShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0);
        assertFalse(midi.isBeatHeld(), "Nach Note-Off darf die MIDI-Taste nicht mehr als gehalten gelten");
        assertFalse(bd.isManualBeatHeld(), "Nach Loslassen muss der gehaltene Beat sofort enden");

        engine.tick();
        assertFalse(engine.isLastTickBeat(), "Sobald die MIDI-Taste losgelassen wird, muss der Beat sofort enden");
    }

    @Test
    public void testMidiBlackoutHoldAndMomentaryAction() {
        MidiInputService midi = new MidiInputService();
        java.util.concurrent.atomic.AtomicBoolean blackoutState = new java.util.concurrent.atomic.AtomicBoolean(false);
        midi.setOnBlackoutHoldChange(blackoutState::set);

        // Blackout auf Note 40 (E2) Ch 0 anlernen (Learn-Druck löst noch nicht aus)
        midi.setLearningBlackout(true);
        assertTrue(midi.isLearningBlackout());
        midi.handleShortMessage(ShortMessage.NOTE_ON, 0, 40, 100);
        assertFalse(midi.isLearningBlackout());
        assertEquals("NOTE", midi.getBlackoutBoundType());
        assertEquals(40, midi.getBlackoutBoundData1());

        // Tastendruck nach dem Anlernen
        midi.handleShortMessage(ShortMessage.NOTE_ON, 0, 40, 100);
        assertTrue(blackoutState.get(), "Beim Drücken muss Blackout sofort aktiv werden");

        // Loslassen
        midi.handleShortMessage(ShortMessage.NOTE_OFF, 0, 40, 0);
        assertFalse(blackoutState.get(), "Beim Loslassen muss Blackout sofort deaktiviert werden");
    }

    @Test
    public void testMidiStrobeLearnAndEngineStrobeActivation() {
        de.exit.sound2artnet.audio.AudioCaptureService audio = new de.exit.sound2artnet.audio.AudioCaptureService();
        de.exit.sound2artnet.artnet.ArtNetSender sender = new de.exit.sound2artnet.artnet.ArtNetSender();
        de.exit.sound2artnet.engine.Sound2LightEngine engine = new de.exit.sound2artnet.engine.Sound2LightEngine(audio, sender);

        var profile = de.exit.sound2artnet.fixture.FixtureLibrary.getDefaultProfiles().get(0); // Dimmer, Strobe, Pan, Tilt, etc.
        var patch = new de.exit.sound2artnet.fixture.FixturePatch("MH 1", 1, profile);
        engine.setPatchedFixtures(java.util.List.of(patch));

        MidiInputService midi = new MidiInputService();
        midi.setOnStrobeHoldChange(engine::setManualStrobe);

        // Strobe auf Note 42 Ch 0 anlernen (Learn-Druck löst noch nicht aus)
        midi.setLearningStrobe(true);
        assertTrue(midi.isLearningStrobe());
        midi.handleShortMessage(ShortMessage.NOTE_ON, 0, 42, 127);
        assertFalse(midi.isLearningStrobe());
        assertEquals(42, midi.getStrobeBoundData1());

        // Tastendruck nach dem Anlernen
        midi.handleShortMessage(ShortMessage.NOTE_ON, 0, 42, 127);
        assertTrue(engine.isManualStrobe(), "Beim Drücken muss Manual Strobe in der Engine aktiv sein");

        // Tick ausführen: Strobe Kanal (Index 5 bei 9ch Spot) muss DMX 255 haben, Dimmer (Index 6) 255
        engine.tick();
        byte[] frame = engine.getCurrentDmxFrame();
        assertEquals((byte) 255, frame[6], "Dimmer muss bei manuellem Strobe auf 255 voll offen sein");
        assertEquals((byte) 255, frame[5], "Hardware-Strobe-Kanal muss bei manuellem Strobe auf 255 stehen");

        // Taste loslassen
        midi.handleShortMessage(ShortMessage.NOTE_OFF, 0, 42, 0);
        assertFalse(engine.isManualStrobe(), "Nach Loslassen muss Manual Strobe deaktiviert sein");
    }
}


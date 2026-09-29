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
}

package de.exit.sound2artnet;

import de.exit.sound2artnet.audio.AutoGainControl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AutoGainControlTest {

    @Test
    public void testLoudSignalFastAttack() {
        AutoGainControl agc = new AutoGainControl();
        double dt = 0.023; // ~23ms Block

        // Extrem lauter Block (z. B. DJ Drop)
        double initialGain = agc.getCurrentGain();
        assertEquals(1.0, initialGain, 0.001);

        // Nach wenigen Blöcken muss der Gain deutlich abgesenkt worden sein
        for (int i = 0; i < 5; i++) {
            agc.update(0.85, 0.98, dt);
        }

        assertTrue(agc.getCurrentGain() < 0.6, "Lautes Signal muss den Gain schnell absenken (Fast Attack)");
    }

    @Test
    public void testQuietSignalBoost() {
        AutoGainControl agc = new AutoGainControl();
        double dt = 0.023;

        // Leises Signal, aber über dem Noise Gate (z. B. RMS 0.05)
        for (int i = 0; i < 60; i++) {
            agc.update(0.05, 0.12, dt);
        }

        assertTrue(agc.getCurrentGain() > 1.5, "Leises Musiksignal muss sanft angehoben werden");
        assertTrue(agc.getCurrentGain() <= 6.0, "Gain darf Maximalgrenze von 6.0x nicht überschreiten");
    }

    @Test
    public void testNoiseGateFreezeOnSilence() {
        AutoGainControl agc = new AutoGainControl();
        double dt = 0.023;

        // Zunächst normale Musik einstellen
        for (int i = 0; i < 30; i++) {
            agc.update(0.25, 0.50, dt);
        }
        double musicGain = agc.getCurrentGain();

        // Jetzt Stille / Raumrauschen (unterhalb des Noise Gates, z. B. RMS 0.003)
        for (int i = 0; i < 40; i++) {
            agc.update(0.003, 0.005, dt);
        }

        // Der Gain darf NICHT nach oben geschossen sein (Gain Freeze)
        assertEquals(musicGain, agc.getCurrentGain(), 0.05, "Noise Gate muss Gain bei Stille einfrieren (Anti-Pumping)");
    }

    @Test
    public void testSoftLimitSaturation() {
        // Werte unter 0.95 bleiben linear
        assertEquals(0.5, AutoGainControl.applySoftLimit(0.5), 0.001);
        assertEquals(-0.5, AutoGainControl.applySoftLimit(-0.5), 0.001);

        // Werte über 1.0 werden sanft gesättigt und überschreiten 1.0 nicht
        double saturated = AutoGainControl.applySoftLimit(1.5);
        assertTrue(saturated < 1.0, "Soft Limit darf 1.0 nicht überschreiten");
        assertTrue(saturated > 0.95, "Soft Limit muss kontinuierlich oberhalb 0.95 bleiben");
    }
}

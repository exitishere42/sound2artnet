package de.exit.sound2artnet.engine;

import de.exit.sound2artnet.util.I18n;
import javafx.scene.paint.Color;

/**
 * Erzeugt beat-synchrone Farben, Stroboskop-Impulse und Farbrad-Indizes.
 */
public class ColorEngine {
    public enum Palette {
        CLUB_NEON("Club Neon", new Color[]{
            Color.web("#00FFFF"), Color.web("#FF007F"), Color.web("#00FF66"), Color.web("#FFFF00")
        }),
        CYBERPUNK("Cyberpunk", new Color[]{
            Color.web("#FF0055"), Color.web("#7928CA"), Color.web("#00DFD8"), Color.web("#FFBE0B")
        }),
        FIRE_AND_ICE("Fire & Ice", new Color[]{
            Color.web("#FF2200"), Color.web("#FF8800"), Color.web("#00D4FF"), Color.web("#E0FFFF")
        }),
        RAINBOW("Regenbogen", new Color[]{
            Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.CYAN, Color.BLUE, Color.MAGENTA
        }),
        MONOCHROME_TEAL("Material Teal", new Color[]{
            Color.web("#03DAC6"), Color.web("#00E5FF"), Color.web("#00FF9A"), Color.web("#80DEEA")
        });

        private final String defaultDisplayName;
        private final Color[] colors;

        Palette(String defaultDisplayName, Color[] colors) {
            this.defaultDisplayName = defaultDisplayName;
            this.colors = colors;
        }

        public String getDisplayName() {
            return I18n.get("palette." + name().toLowerCase());
        }

        public Color[] getColors() {
            return colors;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private Palette currentPalette = Palette.CLUB_NEON;
    private int currentColorIndex = 0;
    private Color currentColor = Palette.CLUB_NEON.colors[0];
    private Color targetColor = Palette.CLUB_NEON.colors[0];
    private int beatCounter = 0;
    private double strobeBurstTimer = 0.0;
    private boolean strobeShutterOn = false;
    private int strobeDmxValue = 0;

    public synchronized void update(boolean isBeat, double trebleEnergy, double deltaSeconds) {
        update(isBeat, trebleEnergy, 0.0, Sound2LightEngine.SpeedTier.MEDIUM, false, deltaSeconds);
    }

    public synchronized void update(boolean isBeat, double trebleEnergy, double bpm,
                                    Sound2LightEngine.SpeedTier tier, boolean strobeEnabled,
                                    double deltaSeconds) {
        update(isBeat, false, trebleEnergy, bpm, tier, strobeEnabled, deltaSeconds);
    }

    public synchronized void update(boolean isBeat, boolean isBeatHeld, double trebleEnergy, double bpm,
                                    Sound2LightEngine.SpeedTier tier, boolean strobeEnabled,
                                    double deltaSeconds) {
        if (tier == null) {
            tier = Sound2LightEngine.SpeedTier.MEDIUM;
        }

        // 1. Beat Trigger für Farbwechsel (abhängig von der Geschwindigkeits-Stufe)
        if (isBeat) {
            beatCounter++;
            Color[] colors = currentPalette.getColors();
            if (tier == Sound2LightEngine.SpeedTier.SLOW) {
                // Bei langsamer Musik (~90 BPM) nur jeden 2. Schlag sanft weiterblenden
                if (beatCounter % 2 == 1) {
                    currentColorIndex = (currentColorIndex + 1) % colors.length;
                    targetColor = colors[currentColorIndex];
                }
            } else if (tier == Sound2LightEngine.SpeedTier.RAVE) {
                // Bei schneller Musik (>= 138 BPM) auf jeden Beat wechseln
                int step = (beatCounter % 4 == 0 && colors.length > 2) ? 2 : 1;
                currentColorIndex = (currentColorIndex + step) % colors.length;
                targetColor = colors[currentColorIndex];
            } else {
                currentColorIndex = (currentColorIndex + 1) % colors.length;
                targetColor = colors[currentColorIndex];
            }
        }

        // 2. Überblend-Geschwindigkeit passend zur BPM-Stufe
        double fadeRate = switch (tier) {
            case IDLE -> 2.0;
            case SLOW -> 3.2;     // Weiche, langsame Übergänge bei ~90 BPM
            case MEDIUM -> 7.5;   // Ausgewogene Übergänge
            case FAST -> 14.0;    // Schnelle, flüssige Wechsel bei ~120 BPM
            case RAVE -> 20.0;    // Dynamische, aber weiche Überblendung bei 170 BPM (~5 Frames statt 1-Frame-Cut)
        };

        double stepFactor = Math.min(1.0, deltaSeconds * fadeRate);
        double r = currentColor.getRed() + (targetColor.getRed() - currentColor.getRed()) * stepFactor;
        double g = currentColor.getGreen() + (targetColor.getGreen() - currentColor.getGreen()) * stepFactor;
        double b = currentColor.getBlue() + (targetColor.getBlue() - currentColor.getBlue()) * stepFactor;
        currentColor = Color.color(Math.max(0, Math.min(1, r)), Math.max(0, Math.min(1, g)), Math.max(0, Math.min(1, b)));

        // 3. Strobo-Effekt in schnellen Stufen (FAST ab ~116/120 BPM und RAVE ab 138 BPM)
        if (!strobeEnabled || !tier.isFastEffectTier()) {
            strobeBurstTimer = 0.0;
            strobeShutterOn = false;
            strobeDmxValue = 0;
        } else {
            if (isBeat) {
                // Bei schnellen Beats zündet ein rhythmischer Strobo-Burst
                strobeBurstTimer = (tier == Sound2LightEngine.SpeedTier.RAVE) ? 0.25 : 0.20;
                strobeDmxValue = (tier == Sound2LightEngine.SpeedTier.RAVE) ? 240 : 215;
                strobeShutterOn = true;
            } else if (isBeatHeld) {
                // Solange der MIDI-/Beat-Knopf gehalten wird, läuft der Strobo-Impuls kontinuierlich weiter
                strobeBurstTimer = 0.05;
                strobeDmxValue = (tier == Sound2LightEngine.SpeedTier.RAVE) ? 240 : 215;
                strobeShutterOn = !strobeShutterOn;
            } else if (strobeBurstTimer > 0.0) {
                strobeBurstTimer = Math.max(0.0, strobeBurstTimer - deltaSeconds);
                strobeShutterOn = !strobeShutterOn;
                if (strobeBurstTimer <= 0.0) {
                    strobeShutterOn = false;
                    strobeDmxValue = 0;
                }
            } else {
                strobeShutterOn = false;
                strobeDmxValue = 0;
            }
        }
    }

    public synchronized void stopStrobeBurst() {
        strobeBurstTimer = 0.0;
        strobeShutterOn = false;
        strobeDmxValue = 0;
    }

    public synchronized Color getCurrentColor() {
        return currentColor;
    }

    public synchronized boolean isStrobeActive() {
        return strobeBurstTimer > 0.0;
    }

    public synchronized boolean isStrobeShutterOn() {
        return strobeShutterOn;
    }

    public synchronized int getStrobeDmxValue() {
        return strobeBurstTimer > 0.0 ? strobeDmxValue : 0;
    }

    public synchronized void setPalette(Palette palette) {
        if (palette != null) {
            this.currentPalette = palette;
            this.currentColorIndex = 0;
            this.targetColor = palette.getColors()[0];
        }
    }

    public Palette getCurrentPalette() {
        return currentPalette;
    }

    /**
     * Ermittelt für Farbräder von Spot-Moving-Heads den am besten passenden Slot-Index.
     */
    public int getColorWheelIndex(Color c) {
        // Typische Farbrad-Einteilung: 0=Weiß, 1=Rot, 2=Blau, 3=Grün, 4=Gelb, 5=Magenta, 6=Orange, 7=Cyan
        double r = c.getRed();
        double g = c.getGreen();
        double b = c.getBlue();

        if (r > 0.6 && g < 0.3 && b < 0.3) return 20;  // Rot
        if (r < 0.3 && g < 0.3 && b > 0.6) return 50;  // Blau
        if (r < 0.3 && g > 0.6 && b < 0.3) return 80;  // Grün
        if (r > 0.6 && g > 0.6 && b < 0.3) return 110; // Gelb
        if (r > 0.6 && g < 0.3 && b > 0.6) return 140; // Magenta
        if (r < 0.3 && g > 0.6 && b > 0.6) return 170; // Cyan
        if (r > 0.7 && g > 0.4 && b < 0.2) return 200; // Orange
        return 0; // Offen / Weiß
    }
}

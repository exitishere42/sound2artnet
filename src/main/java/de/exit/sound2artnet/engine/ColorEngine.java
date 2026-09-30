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
        }),
        TRIPPIN("TRIPPIN", new Color[]{
            Color.web("#FF5500"), Color.web("#FF006E"), Color.web("#8338EC"),
            Color.web("#FFB703"), Color.web("#3A86FF"), Color.web("#FB5607")
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

    /**
     * Gobo-Steuermodi für Spot-/Hybrid-Moving-Heads (optimiert u. a. für ROBE MegaPointe:
     * Static Gobo Wheel 1..10, Rotating Gobo Wheel 1..9 inkl. Rotation, Gobo Shake & Beam Reducer).
     */
    public enum GoboMode {
        AUTO_BEAT("Auto-Beat"),
        STATIC_CYCLE("Statisch-Wechsel"),
        ROTATING_CYCLE("Rotierend-Wechsel"),
        GOBO_SHAKE("Gobo-Shake"),
        BEAM_REDUCER("Beam-Reducer"),
        GOBO_1("Gobo 1"),
        GOBO_2("Gobo 2"),
        GOBO_3("Gobo 3"),
        GOBO_4("Gobo 4"),
        GOBO_5("Gobo 5"),
        GOBO_6("Gobo 6"),
        GOBO_7("Gobo 7"),
        GOBO_8("Gobo 8"),
        GOBO_9("Gobo 9"),
        GOBO_10("Gobo 10");

        private final String defaultDisplayName;

        GoboMode(String defaultDisplayName) {
            this.defaultDisplayName = defaultDisplayName;
        }

        public String getDisplayName() {
            return I18n.get("gobo." + name().toLowerCase());
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    // ROBE MegaPointe / Standard Spot DMX-Stützwerte
    // Static Gobo Wheel: Gobo 1..10 (4..63), Beam Reducer 1..4 (64..87), Gobo 1..10 Shake (88..167)
    private static final int[] STATIC_GOBO_DMX = {6, 12, 18, 24, 30, 36, 42, 48, 54, 60};
    private static final int[] BEAM_REDUCER_DMX = {66, 72, 78, 84};
    private static final int[] STATIC_SHAKE_DMX = {92, 100, 108, 116, 124, 132, 140, 148, 156, 164};
    // Rotating Gobo Wheel: Gobo 1..9 im Rotations-Modus (32..59)
    private static final int[] ROTATING_GOBO_DMX = {33, 36, 39, 42, 45, 48, 51, 54, 57};

    private Palette currentPalette = Palette.CLUB_NEON;
    private GoboMode goboMode = GoboMode.AUTO_BEAT;
    private int currentColorIndex = 0;
    private Color currentColor = Palette.CLUB_NEON.colors[0];
    private Color targetColor = Palette.CLUB_NEON.colors[0];
    private int beatCounter = 0;
    private int goboStepIndex = 0;
    private Sound2LightEngine.SpeedTier lastTier = Sound2LightEngine.SpeedTier.MEDIUM;
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
        this.lastTier = tier;

        // 1. Beat Trigger für Farbwechsel & Gobo-Wechsel (abhängig von der Geschwindigkeits-Stufe)
        if (isBeat) {
            beatCounter++;
            int beatsPerGoboStep = tier.isFastEffectTier() ? 4 : 2;
            if (beatCounter == 1 || beatCounter % beatsPerGoboStep == 0) {
                goboStepIndex++;
            }
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
                strobeDmxValue = (tier == Sound2LightEngine.SpeedTier.RAVE) ? 255 : 215;
                strobeShutterOn = true;
            } else if (isBeatHeld) {
                // Solange der MIDI-/Beat-Knopf gehalten wird, läuft der Strobo-Impuls kontinuierlich weiter
                strobeBurstTimer = 0.05;
                strobeDmxValue = (tier == Sound2LightEngine.SpeedTier.RAVE) ? 255 : 215;
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

    public synchronized void setGoboMode(GoboMode goboMode) {
        if (goboMode != null) {
            this.goboMode = goboMode;
        }
    }

    public synchronized GoboMode getGoboMode() {
        return goboMode;
    }

    /**
     * Berechnet den DMX-Wert für das 1. Goborad (Static Gobo Wheel beim ROBE MegaPointe
     * bzw. einziges Goborad bei Standard-Spot-Moving-Heads).
     */
    public synchronized int getGoboWheel1Dmx(int totalGoboWheels) {
        return switch (goboMode) {
            case STATIC_CYCLE -> STATIC_GOBO_DMX[goboStepIndex % STATIC_GOBO_DMX.length];
            case ROTATING_CYCLE -> (totalGoboWheels >= 2) ? 0 : ROTATING_GOBO_DMX[goboStepIndex % ROTATING_GOBO_DMX.length];
            case AUTO_BEAT -> {
                if (totalGoboWheels >= 2 && (goboStepIndex / 2) % 2 == 1) {
                    // Während das 2. Goborad (Rotating Gobo) aktiv ist, bleibt das 1. Goborad offen (0),
                    // damit sich beide Goboräder beim ROBE MegaPointe nicht gegenseitig verdecken!
                    yield 0;
                }
                yield STATIC_GOBO_DMX[goboStepIndex % STATIC_GOBO_DMX.length];
            }
            case GOBO_SHAKE -> STATIC_SHAKE_DMX[goboStepIndex % STATIC_SHAKE_DMX.length];
            case BEAM_REDUCER -> BEAM_REDUCER_DMX[goboStepIndex % BEAM_REDUCER_DMX.length];
            case GOBO_1 -> STATIC_GOBO_DMX[0];
            case GOBO_2 -> STATIC_GOBO_DMX[1];
            case GOBO_3 -> STATIC_GOBO_DMX[2];
            case GOBO_4 -> STATIC_GOBO_DMX[3];
            case GOBO_5 -> STATIC_GOBO_DMX[4];
            case GOBO_6 -> STATIC_GOBO_DMX[5];
            case GOBO_7 -> STATIC_GOBO_DMX[6];
            case GOBO_8 -> STATIC_GOBO_DMX[7];
            case GOBO_9 -> STATIC_GOBO_DMX[8];
            case GOBO_10 -> STATIC_GOBO_DMX[9];
        };
    }

    /**
     * Berechnet den DMX-Wert für das 2. Goborad (Rotating Gobo Wheel beim ROBE MegaPointe).
     */
    public synchronized int getGoboWheel2Dmx() {
        return switch (goboMode) {
            case ROTATING_CYCLE -> ROTATING_GOBO_DMX[goboStepIndex % ROTATING_GOBO_DMX.length];
            case AUTO_BEAT -> ((goboStepIndex / 2) % 2 == 1)
                    ? ROTATING_GOBO_DMX[goboStepIndex % ROTATING_GOBO_DMX.length]
                    : 0;
            default -> 0;
        };
    }

    /**
     * Berechnet den DMX-Wert für den Gobo-Rotationskanal (z. B. Kanal 20 "Rot. gobo indexing and rotation"
     * beim ROBE MegaPointe: 1..127 Vorwärts-Rotation schnell->langsam, 128 Stop, 129..255 Rückwärts-Rotation langsam->schnell).
     */
    public synchronized int getGoboRotationDmx() {
        boolean rotatingActive = (goboMode == GoboMode.ROTATING_CYCLE) ||
                (goboMode == GoboMode.AUTO_BEAT && (goboStepIndex / 2) % 2 == 1);
        if (!rotatingActive) {
            return 128; // Keine Rotation (Default)
        }
        boolean forward = ((beatCounter / 8) % 2 == 0);
        return switch (lastTier) {
            case IDLE, SLOW -> forward ? 102 : 154;
            case MEDIUM -> forward ? 84 : 172;
            case FAST -> forward ? 62 : 194;
            case RAVE -> forward ? 38 : 218;
        };
    }

    /**
     * Ermittelt für Farbräder von Spot-Moving-Heads den am besten passenden Slot-Index.
     */
    public int getColorWheelIndex(Color c) {
        // Typische Farbrad-Einteilung: 0=Weiß, 1=Rot, 2=Blau, 3=Grün, 4=Gelb, 5=Magenta, 6=Orange, 7=Cyan
        double r = c.getRed();
        double g = c.getGreen();
        double b = c.getBlue();

        if (r > 0.7 && g >= 0.25 && g <= 0.55 && b < 0.2) return 200; // Orange
        if (r > 0.6 && g < 0.3 && b < 0.3) return 20;  // Rot
        if (r < 0.3 && g < 0.3 && b > 0.6) return 50;  // Blau
        if (r < 0.3 && g > 0.6 && b < 0.3) return 80;  // Grün
        if (r > 0.6 && g > 0.6 && b < 0.3) return 110; // Gelb / Amber
        if (r > 0.45 && g < 0.35 && b > 0.4) return 140; // Magenta / Violett
        if (r < 0.35 && g > 0.45 && b > 0.6) return 170; // Cyan / Electric Blue
        return 0; // Offen / Weiß
    }
}

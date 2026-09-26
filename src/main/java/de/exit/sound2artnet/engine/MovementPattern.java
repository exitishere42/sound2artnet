package de.exit.sound2artnet.engine;

import de.exit.sound2artnet.util.I18n;

/**
 * Mathematische Bewegungsmuster für Moving Heads.
 */
public enum MovementPattern {
    AUTO_BPM("Auto-BPM", "Wechselt Bewegungsmuster automatisch passend zur BPM-Stufe"),
    CIRCLE("Kreis", "Flüssige harmonische Kreisbewegung"),
    FIGURE_8("Acht", "Lemniskaten-Achterbahn"),
    BALLYHOO("Ballyhoo", "Dynamische, organische Lissajous-Kurve"),
    WAVE("Welle", "Phasenverschobene Wellenbewegung"),
    PAN_SWEEP("Pan-Sweep", "Breite horizontale Fächerung"),
    TILT_SWING("Tilt-Swing", "Vertikales Auf- und Abschwingen"),
    BEAT_BOUNCE("Beat-Bounce", "Rhythmischer Positionssprung bei jedem Kick");

    private final String defaultDisplayName;
    private final String description;

    MovementPattern(String defaultDisplayName, String description) {
        this.defaultDisplayName = defaultDisplayName;
        this.description = description;
    }

    public String getDisplayName() {
        return I18n.get("pattern." + name().toLowerCase());
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}

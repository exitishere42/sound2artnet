package de.exit.sound2artnet.engine;

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

    private final String displayName;
    private final String description;

    MovementPattern(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

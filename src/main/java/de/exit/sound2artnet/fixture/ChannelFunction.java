package de.exit.sound2artnet.fixture;

import de.exit.sound2artnet.util.I18n;

/**
 * Semantische Kanalfunktionen für Fixtures (Moving Heads, PARs, Bars etc.).
 */
public enum ChannelFunction {
    PAN("Pan", "Horizontale Drehachse"),
    PAN_FINE("Pan Fine", "Pan Feineinstellung (16-Bit)"),
    TILT("Tilt", "Vertikale Neigeachse"),
    TILT_FINE("Tilt Fine", "Tilt Feineinstellung (16-Bit)"),
    PAN_TILT_SPEED("PT Speed", "Geschwindigkeit / Beschleunigung von Pan/Tilt"),
    DIMMER("Master Dimmer", "Gesamthelligkeit des Scheinwerfers (0-255)"),
    STROBE("Strobe", "Stroboskop / Shutter"),
    RED("Rot", "Roter Farbkanal"),
    GREEN("Grün", "Grüner Farbkanal"),
    BLUE("Blau", "Blauer Farbkanal"),
    WHITE("Weiß", "Weißer Farbkanal"),
    AMBER("Amber", "Bernsteinfarbener Farbkanal"),
    UV("UV", "Ultravioletter Farbkanal"),
    COLOR_WHEEL("Farbrad", "Indiziertes Farbrad für Spot-Moving-Heads"),
    GOBO_WHEEL("Goborad", "Muster-/Goborad für Spot-Moving-Heads"),
    PRISM("Prisma", "Prisma-Effekt"),
    FOCUS("Fokus", "Elektronischer Fokus"),
    CONSTANT("Fester Wert", "Kanal mit fixiertem DMX-Wert"),
    UNUSED("Nicht belegt", "Kanal wird ignoriert");

    private final String defaultDisplayName;
    private final String description;

    ChannelFunction(String defaultDisplayName, String description) {
        this.defaultDisplayName = defaultDisplayName;
        this.description = description;
    }

    public String getDisplayName() {
        return I18n.get("function." + name().toLowerCase());
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}

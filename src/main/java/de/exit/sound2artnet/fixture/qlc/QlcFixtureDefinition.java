package de.exit.sound2artnet.fixture.qlc;

import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.fixture.ChannelMapping;
import de.exit.sound2artnet.fixture.FixtureProfile;

import java.util.*;

/**
 * Repräsentiert eine aus einer QLC+ .qxf Datei importierte Gerätedefinition.
 */
public class QlcFixtureDefinition {
    private String manufacturer = "Unbekannt";
    private String model = "QLC+ Fixture";
    private String type = "Moving Head";
    private int panMax = 0;
    private int tiltMax = 0;

    private final Map<String, QlcChannel> channels = new LinkedHashMap<>();
    private final List<QlcMode> modes = new ArrayList<>();

    public static class QlcChannel {
        private final String name;
        private final String preset;
        private final String group;
        private final int byteIndex;
        private final int defaultValue;
        private ChannelFunction resolvedFunction;

        public QlcChannel(String name, String preset, String group, int byteIndex, int defaultValue) {
            this.name = name;
            this.preset = preset != null ? preset : "";
            this.group = group != null ? group : "";
            this.byteIndex = byteIndex;
            this.resolvedFunction = determineFunction();
            String n = this.name.toLowerCase();
            String p = this.preset.toLowerCase();

            if ((this.resolvedFunction == ChannelFunction.PAN || this.resolvedFunction == ChannelFunction.TILT) && defaultValue == 0) {
                this.defaultValue = 128;
            } else if ((this.resolvedFunction == ChannelFunction.WHITE ||
                        this.resolvedFunction == ChannelFunction.AMBER ||
                        this.resolvedFunction == ChannelFunction.UV) && defaultValue == 255) {
                // Bei RGBW/RGBAL Fixtures führt White/Amber/Lime=255 dazu, dass Farben ausgewaschen werden
                this.defaultValue = 0;
            } else if (this.resolvedFunction == ChannelFunction.CONSTANT &&
                       (byteIndex == 1 || p.contains("fine") || n.contains("fine") || n.contains("fein")) &&
                       !n.contains("frequency") && !n.contains("frequenz")) {
                // 16-Bit Fine-Kanäle (Red Fine, Green Fine, Dimmer Fine etc.) auf 0 halten
                this.defaultValue = 0;
            } else if (this.resolvedFunction == ChannelFunction.CONSTANT &&
                       n.contains("background") && n.contains("dimmer") && !n.contains("fine") && defaultValue == 0) {
                // Background Dimmer (z. B. ROBE PATT 2017 / pixelPATT) muss auf 255 stehen, damit Background-RGB sichtbar ist
                this.defaultValue = 255;
            } else if (this.resolvedFunction == ChannelFunction.LASER_SIZE && defaultValue == 0) {
                this.defaultValue = 180;
            } else if (this.resolvedFunction == ChannelFunction.LASER_PATTERN && defaultValue == 0) {
                this.defaultValue = 64;
            } else if (this.resolvedFunction == ChannelFunction.LASER_AMPLITUDE && defaultValue == 0) {
                this.defaultValue = 128;
            } else if (this.resolvedFunction == ChannelFunction.LASER_SPEED && defaultValue == 0) {
                this.defaultValue = 100;
            } else if (this.resolvedFunction == ChannelFunction.LASER_ROTATION && defaultValue == 0) {
                this.defaultValue = 128;
            } else if (this.resolvedFunction == ChannelFunction.LASER_PERSISTENCE && defaultValue == 0) {
                this.defaultValue = 200;
            } else if (this.resolvedFunction == ChannelFunction.FOCUS && defaultValue == 0 && n.equals("focus")) {
                this.defaultValue = 180;
            } else {
                this.defaultValue = defaultValue;
            }
        }

        private ChannelFunction determineFunction() {
            String p = preset.toLowerCase();
            String g = group.toLowerCase();
            String n = name.toLowerCase();

            // 1. Pan Fine & Tilt Fine (vor allgemeinem Fine-Filter prüfen!)
            if ((p.contains("positionpanfine") || n.contains("pan fine") || n.contains("pan-fine") || n.contains("panfine") ||
                 (g.equals("pan") && byteIndex == 1)) &&
                !n.contains("speed") && !n.contains("time") && !n.contains("control")) {
                return ChannelFunction.PAN_FINE;
            }
            if ((p.contains("positiontiltfine") || n.contains("tilt fine") || n.contains("tilt-fine") || n.contains("tiltfine") ||
                 (g.equals("tilt") && byteIndex == 1)) &&
                !n.contains("speed") && !n.contains("time") && !n.contains("control")) {
                return ChannelFunction.TILT_FINE;
            }

            // 2. Alle anderen Fine-Kanäle (Red Fine, Green Fine, Blue Fine, White Fine, Dimmer Fine, Zoom Fine etc.)
            // sound2artnet arbeitet mit 8-Bit Farbwerten; LSB/Fine-Kanäle müssen unverändert als CONSTANT gehalten werden!
            if (p.contains("fine") || n.contains("fine") || n.contains("fein") || byteIndex == 1) {
                return ChannelFunction.CONSTANT;
            }

            // 2b. Laser-spezifische Kanäle (Pattern, Size, Amplitude, Speed, Rotation, Persistence)
            // WICHTIG: Vor Pan/Tilt Speed und generellen Rotations-/Macro-Filtern prüfen!
            if (n.equals("pattern") || (n.contains("pattern") && (n.contains("laser") || p.contains("laser")))) {
                return ChannelFunction.LASER_PATTERN;
            }
            if (n.equals("size") || (n.contains("size") && (n.contains("laser") || p.contains("laser")))) {
                return ChannelFunction.LASER_SIZE;
            }
            if (n.equals("amplitude") || (n.contains("amplitude") && (n.contains("laser") || p.contains("laser")))) {
                return ChannelFunction.LASER_AMPLITUDE;
            }
            if (n.equals("speed") && !p.contains("speedpantilt") && !n.contains("pan") && !n.contains("tilt")) {
                return ChannelFunction.LASER_SPEED;
            }
            if (n.equals("rotation") && (p.contains("beamfocus") || p.contains("laser") || n.contains("laser"))) {
                return ChannelFunction.LASER_ROTATION;
            }
            if (n.equals("persistence") || n.contains("persistence") || n.contains("nachleucht")) {
                return ChannelFunction.LASER_PERSISTENCE;
            }

            // 3. Speed & Time Kanäle (Pan/Tilt Speed vs. Effekt-/Farb-Zeiten)
            // WICHTIG: Muss zwingend VOR Pan/Tilt geprüft werden, damit "Pan/Tilt speed / time" oder "Tilt speed / time"
            // nicht fälschlicherweise als PAN oder TILT eingestuft wird!
            if (p.contains("speedpantilt") || n.contains("p/t") ||
                ((g.equals("speed") || n.contains("speed") || n.contains("geschwindigkeit") || n.contains("time") || n.contains("zeit")) &&
                 (n.contains("pan") || n.contains("tilt") || n.equals("speed") || n.equals("geschwindigkeit")))) {
                return ChannelFunction.PAN_TILT_SPEED;
            }
            if (g.equals("speed") || n.contains("speed") || n.contains("geschwindigkeit") || n.contains("time") || n.contains("zeit")) {
                return ChannelFunction.CONSTANT;
            }

            // 4. Motoren, Rotationen, Steuerkanäle, Blenden, Frost & Frequenzfilter ausschließen (VOR Pan/Tilt/Shutter!)
            if (n.contains("rotation") || n.contains("rotate") || n.contains("indexing") || n.contains("virtual") ||
                n.contains("macro") || n.contains("control") || n.contains("mode") || n.contains("select") ||
                n.contains("reset") || n.contains("function") || n.contains("frequency") || n.contains("frequenz") ||
                n.contains("frost") || n.contains("iris") || p.contains("iris") || n.contains("autofocus") ||
                n.contains("cri") || n.contains("calibration") || n.contains("emulation") || n.contains("screenpix") ||
                n.contains("blade") || n.contains("framing") || n.contains("pattern") || n.contains("positional") ||
                g.equals("maintenance") || g.equals("nothing")) {
                return ChannelFunction.CONSTANT;
            }

            // 5. Pan & Tilt (8-Bit / Coarse)
            if (p.contains("positionpan") || g.equals("pan") || n.contains("pan")) {
                return ChannelFunction.PAN;
            }
            if (p.contains("positiontilt") || g.equals("tilt") || n.contains("tilt")) {
                return ChannelFunction.TILT;
            }

            // 6. Farbtemperatur & Korrektur (CTC, CTO, CTB) -> CONSTANT mit Original-Defaultwert
            if (p.contains("colorctomixer") || p.contains("colorctcmixer") || p.contains("colorctbmixer") ||
                p.contains("cto") || p.contains("ctc") || p.contains("ctb") ||
                n.contains("ctc") || n.contains("cto") || n.contains("ctb") || n.contains("temperature") ||
                n.contains("farbtemperatur") || n.contains("correction")) {
                return ChannelFunction.CONSTANT;
            }

            // 7. Zoom & Focus
            if (p.contains("beamzoom") || n.contains("zoom") || n.contains("focus") || n.contains("fokus")) {
                return ChannelFunction.FOCUS;
            }

            // 8. Dimmer
            if (p.contains("intensitymasterdimmer") || p.contains("intensitydimmer") ||
                (g.equals("intensity") && (n.contains("dimmer") || n.contains("dimming") || n.contains("helligkeit") || n.contains("master"))) ||
                n.contains("dimmer") || n.contains("dimming") || n.contains("helligkeit") ||
                (n.contains("master") && !n.contains("shutter") && !n.contains("strobe"))) {
                // Sub-Dimmer wie Background, Flower, White Strobe LEDs oder White Beam Dimmer:
                if (n.contains("background") || n.contains("flower") || n.contains("white beam") || n.contains("strobe")) {
                    return ChannelFunction.CONSTANT;
                }
                return ChannelFunction.DIMMER;
            }

            // 9. Shutter & Strobe
            // Flash duration, flash effects / special effects dürfen NIEMALS als Strobe getriggert werden!
            if (n.contains("duration") || n.contains("dauer") || n.contains("effect") || n.contains("effekt") || n.contains("special") || n.contains("spezial")) {
                return ChannelFunction.CONSTANT;
            }
            if (p.contains("shutter") || p.contains("strobe") || g.equals("shutter") || n.contains("strobe") || n.contains("shutter") || n.contains("blitz") || n.contains("flash")) {
                return ChannelFunction.STROBE;
            }

            // 10. Farben: CMY (Cyan, Magenta, Yellow) vs. RGBW / Amber / Lime / UV
            if (p.contains("intensitycyan") || n.equals("cyan") || n.startsWith("cyan")) {
                return ChannelFunction.CYAN;
            }
            if (p.contains("intensitymagenta") || n.equals("magenta") || n.startsWith("magenta")) {
                return ChannelFunction.MAGENTA;
            }
            if (p.contains("intensityyellow") || n.equals("yellow") || n.startsWith("yellow")) {
                return ChannelFunction.YELLOW;
            }

            if (p.contains("intensityred") || (g.equals("intensity") && (n.contains("red") || n.matches(".*\\b(rot|red)\\b.*"))) || n.contains("red") || n.matches(".*\\b(rot|red)\\b.*")) {
                return ChannelFunction.RED;
            }
            if (p.contains("intensitygreen") || (g.equals("intensity") && (n.contains("green") || n.matches(".*\\b(grün|gruen|green)\\b.*"))) || n.contains("green") || n.matches(".*\\b(grün|gruen|green)\\b.*")) {
                return ChannelFunction.GREEN;
            }
            if (p.contains("intensityblue") || (g.equals("intensity") && (n.contains("blue") || n.matches(".*\\b(blau|blue)\\b.*"))) || n.contains("blue") || n.matches(".*\\b(blau|blue)\\b.*")) {
                return ChannelFunction.BLUE;
            }
            if (p.contains("intensitywhite") || p.contains("intensitylime") ||
                (g.equals("intensity") && (n.contains("white") || n.contains("lime") || n.matches(".*\\b(weiß|weiss|white|lime)\\b.*"))) ||
                n.contains("white") || n.contains("lime") || n.matches(".*\\b(weiß|weiss|white|lime)\\b.*")) {
                return ChannelFunction.WHITE;
            }
            if (p.contains("intensityamber") || n.contains("amber")) return ChannelFunction.AMBER;
            if (p.contains("intensityuv") || n.contains("uv")) return ChannelFunction.UV;

            // 11. Gobo, Prisma, Farbrad
            if (p.contains("gobo") || g.equals("gobo") || n.contains("gobo")) return ChannelFunction.GOBO_WHEEL;
            if (p.contains("prism") || g.equals("prism") || n.contains("prism")) return ChannelFunction.PRISM;
            if (p.contains("colorwheel") || p.contains("colourwheel") || n.contains("color wheel") || n.contains("colour wheel") || n.contains("farbrad")) {
                return ChannelFunction.COLOR_WHEEL;
            }

            // 12. Übrige Beam-, Colour-, Intensity- oder Effect-Kanäle mit ihrem Defaultwert konstant halten
            if (g.equals("effect") || g.equals("colour") || g.equals("beam") || g.equals("intensity") || p.contains("beam")) {
                return ChannelFunction.CONSTANT;
            }

            return ChannelFunction.UNUSED;
        }

        public String getName() { return name; }
        public String getPreset() { return preset; }
        public String getGroup() { return group; }
        public int getByteIndex() { return byteIndex; }
        public int getDefaultValue() { return defaultValue; }
        public ChannelFunction getResolvedFunction() { return resolvedFunction; }
        public void setResolvedFunction(ChannelFunction f) { this.resolvedFunction = f; }
    }

    public static class QlcMode {
        private final String name;
        private final List<String> channelNames = new ArrayList<>();

        public QlcMode(String name) {
            this.name = name;
        }

        public String getName() { return name; }
        public List<String> getChannelNames() { return channelNames; }
        public int getChannelCount() { return channelNames.size(); }

        @Override
        public String toString() {
            return name;
        }
    }

    public FixtureProfile toFixtureProfile(String modeName) {
        QlcMode selectedMode = null;
        for (QlcMode m : modes) {
            if (m.getName().equalsIgnoreCase(modeName)) {
                selectedMode = m;
                break;
            }
        }
        if (selectedMode == null && !modes.isEmpty()) {
            selectedMode = modes.get(0);
        }

        List<ChannelMapping> mappings = new ArrayList<>();
        if (selectedMode != null) {
            for (int i = 0; i < selectedMode.getChannelNames().size(); i++) {
                String chName = selectedMode.getChannelNames().get(i);
                QlcChannel ch = channels.get(chName);
                if (ch != null) {
                    mappings.add(new ChannelMapping(i, ch.getResolvedFunction(), ch.getDefaultValue()));
                } else {
                    mappings.add(new ChannelMapping(i, ChannelFunction.UNUSED, 0));
                }
            }
        }

        String profileName = (manufacturer != null && !manufacturer.isBlank() ? manufacturer + " " : "") + model;
        if (selectedMode != null) {
            profileName += " (" + selectedMode.getName() + ")";
        }

        String id = "qlc-" + (manufacturer + "-" + model + "-" + (selectedMode != null ? selectedMode.getName() : "default"))
                .toLowerCase().replaceAll("[^a-z0-9_-]", "-");

        return new FixtureProfile(id, profileName, mappings.size(), mappings);
    }

    // Getter & Setter
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getPanMax() { return panMax; }
    public void setPanMax(int panMax) { this.panMax = panMax; }

    public int getTiltMax() { return tiltMax; }
    public void setTiltMax(int tiltMax) { this.tiltMax = tiltMax; }

    public Map<String, QlcChannel> getChannels() { return channels; }
    public List<QlcMode> getModes() { return modes; }

    @Override
    public String toString() {
        return manufacturer + " " + model + " [" + type + "] (" + modes.size() + " Modi)";
    }
}

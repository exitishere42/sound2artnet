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
    private int panMax = 540;
    private int tiltMax = 270;

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
            this.defaultValue = defaultValue;
            this.resolvedFunction = determineFunction();
        }

        private ChannelFunction determineFunction() {
            String p = preset.toLowerCase();
            String g = group.toLowerCase();
            String n = name.toLowerCase();

            // 1. Auswertung des QLC+ Preset-Attributs (sehr präzise ab QLC+ 4.12+)
            if (p.contains("positionpanfine")) return ChannelFunction.PAN_FINE;
            if (p.contains("positionpan")) return ChannelFunction.PAN;
            if (p.contains("positiontiltfine")) return ChannelFunction.TILT_FINE;
            if (p.contains("positiontilt")) return ChannelFunction.TILT;
            if (p.contains("speedpantilt")) return ChannelFunction.PAN_TILT_SPEED;
            if (p.contains("intensitymasterdimmer")) return ChannelFunction.DIMMER;
            if (p.contains("shutter") || p.contains("strobe")) return ChannelFunction.STROBE;
            if (p.contains("intensityred")) return ChannelFunction.RED;
            if (p.contains("intensitygreen")) return ChannelFunction.GREEN;
            if (p.contains("intensityblue")) return ChannelFunction.BLUE;
            if (p.contains("intensitywhite")) return ChannelFunction.WHITE;
            if (p.contains("intensityamber")) return ChannelFunction.AMBER;
            if (p.contains("intensityuv")) return ChannelFunction.UV;
            if (p.contains("gobo")) return ChannelFunction.GOBO_WHEEL;
            if (p.contains("color") || p.contains("colour")) return ChannelFunction.COLOR_WHEEL;
            if (p.contains("prism")) return ChannelFunction.PRISM;
            if (p.contains("focus")) return ChannelFunction.FOCUS;

            // 2. Auswertung der QLC+ Channel-Gruppe
            if (g.equals("pan")) {
                return (byteIndex == 1) ? ChannelFunction.PAN_FINE : ChannelFunction.PAN;
            }
            if (g.equals("tilt")) {
                return (byteIndex == 1) ? ChannelFunction.TILT_FINE : ChannelFunction.TILT;
            }
            if (g.equals("speed")) return ChannelFunction.PAN_TILT_SPEED;
            if (g.equals("shutter")) return ChannelFunction.STROBE;
            if (g.equals("gobo")) return ChannelFunction.GOBO_WHEEL;
            if (g.equals("colour") || g.equals("color")) return ChannelFunction.COLOR_WHEEL;
            if (g.equals("prism")) return ChannelFunction.PRISM;
            if (g.equals("beam") && (n.contains("focus") || n.contains("zoom"))) return ChannelFunction.FOCUS;

            if (g.equals("intensity")) {
                if (n.contains("red") || n.contains("rot")) return ChannelFunction.RED;
                if (n.contains("green") || n.contains("grün")) return ChannelFunction.GREEN;
                if (n.contains("blue") || n.contains("blau")) return ChannelFunction.BLUE;
                if (n.contains("white") || n.contains("weiß")) return ChannelFunction.WHITE;
                if (n.contains("amber")) return ChannelFunction.AMBER;
                if (n.contains("uv")) return ChannelFunction.UV;
                return ChannelFunction.DIMMER;
            }

            // 3. Heuristische Analyse des Kanalnamens
            if (n.contains("pan fine") || n.contains("pan-fine") || n.contains("panfine")) return ChannelFunction.PAN_FINE;
            if (n.contains("pan")) return ChannelFunction.PAN;
            if (n.contains("tilt fine") || n.contains("tilt-fine") || n.contains("tiltfine")) return ChannelFunction.TILT_FINE;
            if (n.contains("tilt")) return ChannelFunction.TILT;
            if (n.contains("speed") || n.contains("geschwindigkeit") || n.contains("p/t")) return ChannelFunction.PAN_TILT_SPEED;
            if (n.contains("dimmer") || n.contains("dimming") || n.contains("master") || n.contains("helligkeit")) return ChannelFunction.DIMMER;
            if (n.contains("strobe") || n.contains("shutter") || n.contains("blitz")) return ChannelFunction.STROBE;
            if (n.contains("red") || n.contains("rot")) return ChannelFunction.RED;
            if (n.contains("green") || n.contains("grün")) return ChannelFunction.GREEN;
            if (n.contains("blue") || n.contains("blau")) return ChannelFunction.BLUE;
            if (n.contains("white") || n.contains("weiß")) return ChannelFunction.WHITE;
            if (n.contains("amber")) return ChannelFunction.AMBER;
            if (n.contains("uv")) return ChannelFunction.UV;
            if (n.contains("color") || n.contains("colour") || n.contains("farb")) return ChannelFunction.COLOR_WHEEL;
            if (n.contains("gobo")) return ChannelFunction.GOBO_WHEEL;
            if (n.contains("prism")) return ChannelFunction.PRISM;
            if (n.contains("focus")) return ChannelFunction.FOCUS;
            if (n.contains("reset") || n.contains("mode") || n.contains("macro") || n.contains("function")) return ChannelFunction.CONSTANT;

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

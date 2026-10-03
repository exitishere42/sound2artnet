package de.exit.sound2artnet.fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * Standardbibliothek mit bewährten Fixture-Profilen für Moving Heads und PAR-Scheinwerfer.
 */
public final class FixtureLibrary {
    private FixtureLibrary() {}

    public static List<FixtureProfile> getDefaultProfiles() {
        List<FixtureProfile> list = new ArrayList<>();
        list.add(createGeneric9chSpot());
        list.add(createGeneric11chSpot());
        list.add(createGeneric9chWash());
        list.add(createGeneric14chWash());
        list.add(createGeneric4chRgbwPar());
        list.add(createGeneric7chRgbwPar());
        list.add(createMinecraftTheatricalLaser());
        list.add(createMinecraftTheatricalLaserMirror());
        return list;
    }

    public static FixtureProfile createGeneric9chSpot() {
        List<ChannelMapping> channels = new ArrayList<>();
        channels.add(new ChannelMapping(0, ChannelFunction.PAN, 128));
        channels.add(new ChannelMapping(1, ChannelFunction.PAN_FINE, 0));
        channels.add(new ChannelMapping(2, ChannelFunction.TILT, 128));
        channels.add(new ChannelMapping(3, ChannelFunction.TILT_FINE, 0));
        channels.add(new ChannelMapping(4, ChannelFunction.COLOR_WHEEL, 0));
        channels.add(new ChannelMapping(5, ChannelFunction.STROBE, 0));
        channels.add(new ChannelMapping(6, ChannelFunction.DIMMER, 255));
        channels.add(new ChannelMapping(7, ChannelFunction.GOBO_WHEEL, 0));
        channels.add(new ChannelMapping(8, ChannelFunction.PAN_TILT_SPEED, 0));
        return new FixtureProfile("spot-9ch", "Generic 9-Kanal Spot Moving Head", 9, channels);
    }

    public static FixtureProfile createGeneric11chSpot() {
        List<ChannelMapping> channels = new ArrayList<>();
        channels.add(new ChannelMapping(0, ChannelFunction.PAN, 128));
        channels.add(new ChannelMapping(1, ChannelFunction.PAN_FINE, 0));
        channels.add(new ChannelMapping(2, ChannelFunction.TILT, 128));
        channels.add(new ChannelMapping(3, ChannelFunction.TILT_FINE, 0));
        channels.add(new ChannelMapping(4, ChannelFunction.PAN_TILT_SPEED, 0));
        channels.add(new ChannelMapping(5, ChannelFunction.DIMMER, 255));
        channels.add(new ChannelMapping(6, ChannelFunction.STROBE, 0));
        channels.add(new ChannelMapping(7, ChannelFunction.COLOR_WHEEL, 0));
        channels.add(new ChannelMapping(8, ChannelFunction.GOBO_WHEEL, 0));
        channels.add(new ChannelMapping(9, ChannelFunction.PRISM, 0));
        channels.add(new ChannelMapping(10, ChannelFunction.FOCUS, 128));
        return new FixtureProfile("spot-11ch", "Generic 11-Kanal Spot Moving Head", 11, channels);
    }

    public static FixtureProfile createGeneric9chWash() {
        List<ChannelMapping> channels = new ArrayList<>();
        channels.add(new ChannelMapping(0, ChannelFunction.PAN, 128));
        channels.add(new ChannelMapping(1, ChannelFunction.TILT, 128));
        channels.add(new ChannelMapping(2, ChannelFunction.PAN_TILT_SPEED, 0));
        channels.add(new ChannelMapping(3, ChannelFunction.DIMMER, 255));
        channels.add(new ChannelMapping(4, ChannelFunction.STROBE, 0));
        channels.add(new ChannelMapping(5, ChannelFunction.RED, 255));
        channels.add(new ChannelMapping(6, ChannelFunction.GREEN, 0));
        channels.add(new ChannelMapping(7, ChannelFunction.BLUE, 255));
        channels.add(new ChannelMapping(8, ChannelFunction.WHITE, 0));
        return new FixtureProfile("wash-9ch", "Generic 9-Kanal Wash RGBW Moving Head", 9, channels);
    }

    public static FixtureProfile createGeneric14chWash() {
        List<ChannelMapping> channels = new ArrayList<>();
        channels.add(new ChannelMapping(0, ChannelFunction.PAN, 128));
        channels.add(new ChannelMapping(1, ChannelFunction.PAN_FINE, 0));
        channels.add(new ChannelMapping(2, ChannelFunction.TILT, 128));
        channels.add(new ChannelMapping(3, ChannelFunction.TILT_FINE, 0));
        channels.add(new ChannelMapping(4, ChannelFunction.PAN_TILT_SPEED, 0));
        channels.add(new ChannelMapping(5, ChannelFunction.DIMMER, 255));
        channels.add(new ChannelMapping(6, ChannelFunction.STROBE, 0));
        channels.add(new ChannelMapping(7, ChannelFunction.RED, 255));
        channels.add(new ChannelMapping(8, ChannelFunction.GREEN, 255));
        channels.add(new ChannelMapping(9, ChannelFunction.BLUE, 255));
        channels.add(new ChannelMapping(10, ChannelFunction.WHITE, 0));
        channels.add(new ChannelMapping(11, ChannelFunction.UNUSED, 0));
        channels.add(new ChannelMapping(12, ChannelFunction.UNUSED, 0));
        channels.add(new ChannelMapping(13, ChannelFunction.UNUSED, 0));
        return new FixtureProfile("wash-14ch", "Generic 14-Kanal Wash RGBW Moving Head", 14, channels);
    }

    public static FixtureProfile createGeneric4chRgbwPar() {
        List<ChannelMapping> channels = new ArrayList<>();
        channels.add(new ChannelMapping(0, ChannelFunction.RED, 255));
        channels.add(new ChannelMapping(1, ChannelFunction.GREEN, 255));
        channels.add(new ChannelMapping(2, ChannelFunction.BLUE, 255));
        channels.add(new ChannelMapping(3, ChannelFunction.WHITE, 0));
        return new FixtureProfile("par-4ch", "Generic 4-Kanal RGBW PAR", 4, channels);
    }

    public static FixtureProfile createGeneric7chRgbwPar() {
        List<ChannelMapping> channels = new ArrayList<>();
        channels.add(new ChannelMapping(0, ChannelFunction.DIMMER, 255));
        channels.add(new ChannelMapping(1, ChannelFunction.STROBE, 0));
        channels.add(new ChannelMapping(2, ChannelFunction.RED, 255));
        channels.add(new ChannelMapping(3, ChannelFunction.GREEN, 255));
        channels.add(new ChannelMapping(4, ChannelFunction.BLUE, 255));
        channels.add(new ChannelMapping(5, ChannelFunction.WHITE, 0));
        channels.add(new ChannelMapping(6, ChannelFunction.UNUSED, 0));
        return new FixtureProfile("par-7ch", "Generic 7-Kanal RGBW PAR", 7, channels);
    }

    public static FixtureProfile createMinecraftTheatricalLaser() {
        List<ChannelMapping> channels = new ArrayList<>();
        channels.add(new ChannelMapping(0, ChannelFunction.DIMMER, 255));
        channels.add(new ChannelMapping(1, ChannelFunction.RED, 255));
        channels.add(new ChannelMapping(2, ChannelFunction.GREEN, 0));
        channels.add(new ChannelMapping(3, ChannelFunction.BLUE, 255));
        channels.add(new ChannelMapping(4, ChannelFunction.RED, 0));
        channels.add(new ChannelMapping(5, ChannelFunction.GREEN, 255));
        channels.add(new ChannelMapping(6, ChannelFunction.BLUE, 255));
        channels.add(new ChannelMapping(7, ChannelFunction.RED, 255));
        channels.add(new ChannelMapping(8, ChannelFunction.GREEN, 255));
        channels.add(new ChannelMapping(9, ChannelFunction.BLUE, 0));
        channels.add(new ChannelMapping(10, ChannelFunction.LASER_PATTERN, 64));
        channels.add(new ChannelMapping(11, ChannelFunction.LASER_SIZE, 180));
        channels.add(new ChannelMapping(12, ChannelFunction.LASER_AMPLITUDE, 128));
        channels.add(new ChannelMapping(13, ChannelFunction.LASER_SPEED, 100));
        channels.add(new ChannelMapping(14, ChannelFunction.LASER_ROTATION, 128));
        channels.add(new ChannelMapping(15, ChannelFunction.PAN, 128));
        channels.add(new ChannelMapping(16, ChannelFunction.TILT, 128));
        channels.add(new ChannelMapping(17, ChannelFunction.FOCUS, 180));
        channels.add(new ChannelMapping(18, ChannelFunction.LASER_PERSISTENCE, 200));
        return new FixtureProfile("mc-theatrical-laser-19ch", "Minecraft Theatrical Laser (19-Kanal)", 19, channels);
    }

    public static FixtureProfile createMinecraftTheatricalLaserMirror() {
        List<ChannelMapping> channels = new ArrayList<>();
        channels.add(new ChannelMapping(0, ChannelFunction.DIMMER, 255));
        channels.add(new ChannelMapping(1, ChannelFunction.RED, 255));
        channels.add(new ChannelMapping(2, ChannelFunction.GREEN, 255));
        channels.add(new ChannelMapping(3, ChannelFunction.BLUE, 255));
        channels.add(new ChannelMapping(4, ChannelFunction.FOCUS, 180));
        return new FixtureProfile("mc-theatrical-laser-mirror-5ch", "Minecraft Theatrical Laser-Mirror (5-Kanal)", 5, channels);
    }
}

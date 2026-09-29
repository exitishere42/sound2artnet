package de.exit.sound2artnet;

import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.fixture.ChannelMapping;
import de.exit.sound2artnet.fixture.FixtureProfile;
import de.exit.sound2artnet.fixture.qlc.QlcFixtureDefinition;
import de.exit.sound2artnet.fixture.qlc.QlcFixtureParser;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

public class TestAllQxf {

    @Test
    public void testAllQxfFiles() throws Exception {
        File dir = new File("C:\\Users\\timo\\Documents\\QLC+ Saves\\qxf");
        if (!dir.exists()) {
            System.err.println("Directory does not exist: " + dir);
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".qxf"));
        if (files == null || files.length == 0) {
            System.err.println("No QXF files found in " + dir);
            return;
        }

        Arrays.sort(files, Comparator.comparing(File::getName));
        System.out.printf("Gefundene QXF-Dateien: %d%n%n", files.length);

        int successCount = 0;
        int errorCount = 0;
        List<String> warnings = new ArrayList<>();

        for (File f : files) {
            try {
                QlcFixtureDefinition def = QlcFixtureParser.parse(f);
                successCount++;

                int modeCount = def.getModes().size();
                if (modeCount == 0) {
                    warnings.add(f.getName() + ": Keine Modi definiert!");
                    continue;
                }

                for (QlcFixtureDefinition.QlcMode mode : def.getModes()) {
                    FixtureProfile profile = def.toFixtureProfile(mode.getName());
                    int chCount = profile.getChannelCount();
                    if (chCount == 0) {
                        warnings.add(f.getName() + " [" + mode.getName() + "]: 0 Kanäle erzeugt!");
                    }

                    int unusedCount = 0;
                    int dimmerCount = 0;
                    int panCount = 0;
                    int tiltCount = 0;
                    List<String> unrecognizedNames = new ArrayList<>();

                    List<String> panNames = new ArrayList<>();
                    List<String> tiltNames = new ArrayList<>();
                    List<String> dimmerNames = new ArrayList<>();

                    int rgbCount = 0;
                    int colorWheelCount = 0;
                    int whiteCount = 0;
                    List<String> strobeInfo = new ArrayList<>();

                    for (int i = 0; i < profile.getChannels().size(); i++) {
                        ChannelMapping cm = profile.getChannels().get(i);
                        String chName = (i < mode.getChannelNames().size()) ? mode.getChannelNames().get(i) : "?";
                        if (cm.getFunction() == ChannelFunction.UNUSED) {
                            unusedCount++;
                            unrecognizedNames.add(chName);
                        } else if (cm.getFunction() == ChannelFunction.DIMMER) {
                            dimmerCount++;
                            dimmerNames.add(chName);
                        } else if (cm.getFunction() == ChannelFunction.PAN) {
                            panCount++;
                            panNames.add(chName);
                        } else if (cm.getFunction() == ChannelFunction.TILT) {
                            tiltCount++;
                            tiltNames.add(chName);
                        } else if (cm.getFunction() == ChannelFunction.RED || cm.getFunction() == ChannelFunction.GREEN || cm.getFunction() == ChannelFunction.BLUE
                                || cm.getFunction() == ChannelFunction.CYAN || cm.getFunction() == ChannelFunction.MAGENTA || cm.getFunction() == ChannelFunction.YELLOW) {
                            rgbCount++;
                        } else if (cm.getFunction() == ChannelFunction.COLOR_WHEEL) {
                            colorWheelCount++;
                        } else if (cm.getFunction() == ChannelFunction.WHITE || cm.getFunction() == ChannelFunction.AMBER || cm.getFunction() == ChannelFunction.UV) {
                            whiteCount++;
                        } else if (cm.getFunction() == ChannelFunction.STROBE) {
                            strobeInfo.add(chName + "(def=" + cm.getDefaultValue() + ")");
                        }
                    }

                    if (!unrecognizedNames.isEmpty()) {
                        warnings.add(f.getName() + " [" + mode.getName() + "]: UNUSED Kanäle (" + unusedCount + "): " + unrecognizedNames);
                    }
                    if (dimmerCount == 0 && rgbCount == 0 && colorWheelCount == 0 && whiteCount == 0) {
                        warnings.add(f.getName() + " [" + mode.getName() + "]: KEIN Licht-/Dimmer-/Farbkanal erkannt!");
                    }
                    long nonNumberedDimmers = dimmerNames.stream().filter(n -> !n.matches(".*\\s+\\d+$")).count();
                    if (nonNumberedDimmers > 2) {
                        warnings.add(f.getName() + " [" + mode.getName() + "]: Mehr als 2 Haupt-Dimmer erkannt (" + dimmerCount + "): " + dimmerNames);
                    }
                    long nonNumberedPans = panNames.stream().filter(n -> !n.matches(".*\\s+\\d+$")).count();
                    if (nonNumberedPans > 1) {
                        warnings.add(f.getName() + " [" + mode.getName() + "]: Mehr als 1 Haupt-Pan erkannt (" + panCount + "): " + panNames);
                    }
                    long nonNumberedTilts = tiltNames.stream().filter(n -> !n.matches(".*\\s+\\d+$")).count();
                    if (nonNumberedTilts > 1) {
                        warnings.add(f.getName() + " [" + mode.getName() + "]: Mehr als 1 Haupt-Tilt erkannt (" + tiltCount + "): " + tiltNames);
                    }
                }

                System.out.printf("OK: %-45s -> %-12s %-20s | Modi: %2d | Kanäle: %d%n",
                        f.getName(), def.getManufacturer(), def.getModel(), modeCount, def.getChannels().size());

            } catch (Exception e) {
                errorCount++;
                System.err.printf("FEHLER in %s: %s%n", f.getName(), e.getMessage());
                e.printStackTrace(System.err);
            }
        }

        System.out.println("\n----------------- ZUSAMMENFASSUNG -----------------");
        System.out.printf("Erfolgreich geparst: %d / %d%n", successCount, files.length);
        System.out.printf("Fehler beim Parsen:  %d%n", errorCount);
        System.out.printf("Warnungen:           %d%n", warnings.size());
        for (String w : warnings) {
            System.out.println("  [WARNUNG] " + w);
        }

        org.junit.jupiter.api.Assertions.assertEquals(0, errorCount, "Es dürfen keine Parse-Fehler auftreten");
        org.junit.jupiter.api.Assertions.assertTrue(warnings.isEmpty(), "Es dürfen keine Warnungen auftreten: " + warnings);
    }
}

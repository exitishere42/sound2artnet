package de.exit.sound2artnet.fixture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Konkrete Instanz eines gepatchten Fixtures im DMX-Universum.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixturePatch {
    private String id;
    private String name;
    private int universe = 0; // 0-15
    private int startAddress; // 1-512
    private FixtureProfile profile;

    // Moving-Head Schutzgrenzen & Invertierung
    private boolean invertPan = false;
    private boolean invertTilt = false;
    private int panMin = 0;
    private int panMax = 255;
    private int tiltMin = 0;
    private int tiltMax = 255;
    private double phaseOffset = 0.0; // Phasenversatz für Wellenfahrten (0.0 bis 2.0 PI)
    private boolean enabled = true;

    public FixturePatch() {
        this.id = UUID.randomUUID().toString();
    }

    @JsonCreator
    public FixturePatch(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("universe") Integer universe,
            @JsonProperty("startAddress") int startAddress,
            @JsonProperty("profile") FixtureProfile profile,
            @JsonProperty("invertPan") boolean invertPan,
            @JsonProperty("invertTilt") boolean invertTilt,
            @JsonProperty("panMin") int panMin,
            @JsonProperty("panMax") int panMax,
            @JsonProperty("tiltMin") int tiltMin,
            @JsonProperty("tiltMax") int tiltMax,
            @JsonProperty("phaseOffset") double phaseOffset,
            @JsonProperty("enabled") boolean enabled) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name != null ? name : "Fixture";
        this.universe = universe != null ? Math.max(0, Math.min(15, universe)) : 0;
        this.startAddress = Math.max(1, Math.min(512, startAddress));
        this.profile = profile != null ? profile : new FixtureProfile();
        this.invertPan = invertPan;
        this.invertTilt = invertTilt;
        this.panMin = Math.max(0, Math.min(255, panMin));
        this.panMax = Math.max(0, Math.min(255, panMax));
        this.tiltMin = Math.max(0, Math.min(255, tiltMin));
        this.tiltMax = Math.max(0, Math.min(255, tiltMax));
        this.phaseOffset = phaseOffset;
        this.enabled = enabled;
    }

    public FixturePatch(
            String id, String name, int startAddress, FixtureProfile profile,
            boolean invertPan, boolean invertTilt, int panMin, int panMax,
            int tiltMin, int tiltMax, double phaseOffset, boolean enabled) {
        this(id, name, 0, startAddress, profile, invertPan, invertTilt, panMin, panMax, tiltMin, tiltMax, phaseOffset, enabled);
    }

    public FixturePatch(String name, int universe, int startAddress, FixtureProfile profile) {
        this(UUID.randomUUID().toString(), name, universe, startAddress, profile, false, false, 0, 255, 0, 255, 0.0, true);
    }

    public FixturePatch(String name, int startAddress, FixtureProfile profile) {
        this(UUID.randomUUID().toString(), name, 0, startAddress, profile, false, false, 0, 255, 0, 255, 0.0, true);
    }

    @JsonIgnore
    public int getEndAddress() {
        if (profile == null) return startAddress;
        return Math.min(512, startAddress + profile.getChannelCount() - 1);
    }

    /**
     * Rechnet einen normalisierten Pan-Wert (0.0 bis 1.0) in den DMX-Wert um,
     * unter Berücksichtigung von Invertierung und benutzerdefinierten Limits.
     */
    public int computePanDmx(double normalizedVal) {
        double v = Math.max(0.0, Math.min(1.0, normalizedVal));
        if (invertPan) {
            v = 1.0 - v;
        }
        int min = Math.min(panMin, panMax);
        int max = Math.max(panMin, panMax);
        return (int) Math.round(min + v * (max - min));
    }

    /**
     * Berechnet das 16-Bit Fine-Byte (LSB 0..255) für Pan_Fine für butterweiche Fahrten.
     */
    public int computePanFineDmx(double normalizedVal) {
        double v = Math.max(0.0, Math.min(1.0, normalizedVal));
        if (invertPan) {
            v = 1.0 - v;
        }
        int min = Math.min(panMin, panMax);
        int max = Math.max(panMin, panMax);
        double exact = min + v * (max - min);
        double frac = exact - Math.floor(exact);
        return (int) Math.round(frac * 255.0);
    }

    /**
     * Rechnet einen normalisierten Tilt-Wert (0.0 bis 1.0) in den DMX-Wert um,
     * unter Berücksichtigung von Invertierung und benutzerdefinierten Limits.
     */
    public int computeTiltDmx(double normalizedVal) {
        double v = Math.max(0.0, Math.min(1.0, normalizedVal));
        if (invertTilt) {
            v = 1.0 - v;
        }
        int min = Math.min(tiltMin, tiltMax);
        int max = Math.max(tiltMin, tiltMax);
        return (int) Math.round(min + v * (max - min));
    }

    /**
     * Berechnet das 16-Bit Fine-Byte (LSB 0..255) für Tilt_Fine für butterweiche Fahrten.
     */
    public int computeTiltFineDmx(double normalizedVal) {
        double v = Math.max(0.0, Math.min(1.0, normalizedVal));
        if (invertTilt) {
            v = 1.0 - v;
        }
        int min = Math.min(tiltMin, tiltMax);
        int max = Math.max(tiltMin, tiltMax);
        double exact = min + v * (max - min);
        double frac = exact - Math.floor(exact);
        return (int) Math.round(frac * 255.0);
    }

    // Getter & Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getUniverse() { return universe; }
    public void setUniverse(int universe) { this.universe = Math.max(0, Math.min(15, universe)); }

    public int getStartAddress() { return startAddress; }
    public void setStartAddress(int startAddress) { this.startAddress = Math.max(1, Math.min(512, startAddress)); }

    public FixtureProfile getProfile() { return profile; }
    public void setProfile(FixtureProfile profile) { this.profile = profile; }

    public boolean isInvertPan() { return invertPan; }
    public void setInvertPan(boolean invertPan) { this.invertPan = invertPan; }

    public boolean isInvertTilt() { return invertTilt; }
    public void setInvertTilt(boolean invertTilt) { this.invertTilt = invertTilt; }

    public int getPanMin() { return panMin; }
    public void setPanMin(int panMin) { this.panMin = panMin; }

    public int getPanMax() { return panMax; }
    public void setPanMax(int panMax) { this.panMax = panMax; }

    public int getTiltMin() { return tiltMin; }
    public void setTiltMin(int tiltMin) { this.tiltMin = tiltMin; }

    public int getTiltMax() { return tiltMax; }
    public void setTiltMax(int tiltMax) { this.tiltMax = tiltMax; }

    public double getPhaseOffset() { return phaseOffset; }
    public void setPhaseOffset(double phaseOffset) { this.phaseOffset = phaseOffset; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public FixturePatch copy() {
        return new FixturePatch(
                this.id,
                this.name,
                this.universe,
                this.startAddress,
                this.profile,
                this.invertPan,
                this.invertTilt,
                this.panMin,
                this.panMax,
                this.tiltMin,
                this.tiltMax,
                this.phaseOffset,
                this.enabled
        );
    }

    @Override
    public String toString() {
        return name + " (Uni " + universe + " | DMX " + startAddress + "-" + getEndAddress() + ")";
    }
}


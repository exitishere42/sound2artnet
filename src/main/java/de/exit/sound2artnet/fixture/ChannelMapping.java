package de.exit.sound2artnet.fixture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Zuweisung eines relativen Kanal-Offsets (z. B. +0, +1) zu einer semantischen Funktion.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChannelMapping {
    private int offset;
    private ChannelFunction function;
    private int defaultValue; // Default-Wert (z. B. 255 bei Shutter/Strobe für "dauerhaft offen")

    public ChannelMapping() {
        this(0, ChannelFunction.UNUSED, 0);
    }

    @JsonCreator
    public ChannelMapping(
            @JsonProperty("offset") int offset,
            @JsonProperty("function") ChannelFunction function,
            @JsonProperty("defaultValue") int defaultValue) {
        this.offset = offset;
        this.function = function != null ? function : ChannelFunction.UNUSED;
        this.defaultValue = Math.max(0, Math.min(255, defaultValue));
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public ChannelFunction getFunction() {
        return function;
    }

    public void setFunction(ChannelFunction function) {
        this.function = function != null ? function : ChannelFunction.UNUSED;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(int defaultValue) {
        this.defaultValue = Math.max(0, Math.min(255, defaultValue));
    }

    @Override
    public String toString() {
        return "+" + offset + ": " + function + (defaultValue > 0 ? " (Def: " + defaultValue + ")" : "");
    }
}

package de.exit.sound2artnet.fixture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Geräteprofil (z. B. Generic 9ch Spot, Generic 14ch Wash etc.).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureProfile {
    private String id;
    private String name;
    private int channelCount;
    private List<ChannelMapping> channels = new ArrayList<>();

    public FixtureProfile() {}

    @JsonCreator
    public FixtureProfile(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("channelCount") int channelCount,
            @JsonProperty("channels") List<ChannelMapping> channels) {
        this.id = id;
        this.name = name;
        this.channelCount = channelCount;
        if (channels != null) {
            this.channels = new ArrayList<>(channels);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }

    public List<ChannelMapping> getChannels() {
        return channels;
    }

    public void setChannels(List<ChannelMapping> channels) {
        this.channels = channels != null ? channels : new ArrayList<>();
    }

    /**
     * Sucht den ersten relativen Kanal-Offset für eine bestimmte Funktion.
     * @return Offset (0..channelCount-1) oder -1 falls nicht vorhanden.
     */
    public int findChannelOffset(ChannelFunction function) {
        for (ChannelMapping m : channels) {
            if (m.getFunction() == function) {
                return m.getOffset();
            }
        }
        return -1;
    }

    public FixtureProfile copy() {
        List<ChannelMapping> copiedChannels = new ArrayList<>();
        for (ChannelMapping cm : channels) {
            copiedChannels.add(new ChannelMapping(cm.getOffset(), cm.getFunction(), cm.getDefaultValue()));
        }
        return new FixtureProfile(id, name, channelCount, copiedChannels);
    }

    @Override
    public String toString() {
        return name + " (" + channelCount + " Kanäle)";
    }
}

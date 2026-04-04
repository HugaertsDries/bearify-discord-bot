package com.bearify.discord.testing;

import com.bearify.discord.api.interaction.Option;
import com.bearify.discord.api.interaction.AutocompleteInteraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MockAutocompleteInteraction implements AutocompleteInteraction {

    private final String id;
    private final String value;
    private final String guildId;
    private final String textChannelId;
    private final String voiceChannelId;
    private final List<List<Option>> replies = new ArrayList<>();

    private MockAutocompleteInteraction(String id, String value, String guildId, String textChannelId, String voiceChannelId) {
        this.id = id;
        this.value = value;
        this.guildId = guildId;
        this.textChannelId = textChannelId;
        this.voiceChannelId = voiceChannelId;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void reply(List<Option> choices) {
        replies.add(List.copyOf(choices));
    }

    @Override
    public Optional<String> getGuildId() {
        return Optional.ofNullable(guildId);
    }

    @Override
    public Optional<String> getVoiceChannelId() {
        return Optional.ofNullable(voiceChannelId);
    }

    @Override
    public Optional<String> getTextChannelId() {
        return Optional.ofNullable(textChannelId);
    }

    public List<List<Option>> getReplies() {
        return List.copyOf(replies);
    }

    public static Builder forAutocomplete(String id, String value) {
        return new Builder(id, value);
    }

    public static class Builder {
        private final String id;
        private final String value;
        private String guildId;
        private String textChannelId;
        private String voiceChannelId;

        private Builder(String id, String value) {
            this.id = id;
            this.value = value;
        }

        public Builder guildId(String guildId) {
            this.guildId = guildId;
            return this;
        }

        public Builder textChannelId(String textChannelId) {
            this.textChannelId = textChannelId;
            return this;
        }

        public Builder voiceChannelId(String voiceChannelId) {
            this.voiceChannelId = voiceChannelId;
            return this;
        }

        public MockAutocompleteInteraction build() {
            return new MockAutocompleteInteraction(id, value, guildId, textChannelId, voiceChannelId);
        }
    }
}

package com.bearify.discord.testing;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.api.interaction.ReplyBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MockCommandInteraction implements CommandInteraction {

    private final String name;
    private final Map<String, String> options;
    private final String subcommandName;
    private final String voiceChannelId;
    private final String guildId;
    private final String textChannelId;
    private MockEditableMessage deferredMessage;
    private boolean deferredEphemeral = false;
    private final List<MockReplyBuilder> replies = new ArrayList<>();

    private MockCommandInteraction(String name, Map<String, String> options, String subcommandName,
                                   String voiceChannelId, String guildId, String textChannelId) {
        this.name = name;
        this.options = Map.copyOf(options);
        this.subcommandName = subcommandName;
        this.voiceChannelId = voiceChannelId;
        this.guildId = guildId;
        this.textChannelId = textChannelId;
    }

    @Override
    public EditableMessage defer(boolean ephemeral) {
        this.deferredMessage = new MockEditableMessage();
        this.deferredEphemeral = ephemeral;
        return deferredMessage;
    }

    @Override
    public ReplyBuilder reply(String message) {
        MockReplyBuilder reply = new MockReplyBuilder(message);
        replies.add(reply);
        return reply;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Optional<String> getOption(String name) {
        return Optional.ofNullable(options.get(name));
    }

    @Override
    public Optional<String> getSubcommandName() {
        return Optional.ofNullable(subcommandName);
    }

    @Override
    public Optional<String> getVoiceChannelId() {
        return Optional.ofNullable(voiceChannelId);
    }

    @Override
    public Optional<String> getGuildId() {
        return Optional.ofNullable(guildId);
    }

    @Override
    public Optional<String> getTextChannelId() {
        return Optional.ofNullable(textChannelId);
    }

    public Optional<MockEditableMessage> getDeferredMessage() {
        return Optional.ofNullable(deferredMessage);
    }

    public boolean isDeferredEphemeral() {
        return deferredEphemeral;
    }

    public List<MockReplyBuilder> getReplies() {
        return List.copyOf(replies);
    }

    public static Builder forCommand(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final Map<String, String> options = new HashMap<>();
        private String subcommandName;
        private String voiceChannelId;
        private String guildId;
        private String textChannelId;

        private Builder(String name) {
            this.name = name;
        }

        public Builder option(String key, String value) {
            options.put(key, value);
            return this;
        }

        public Builder subcommand(String subcommandName) {
            this.subcommandName = subcommandName;
            return this;
        }

        public Builder voiceChannelId(String voiceChannelId) {
            this.voiceChannelId = voiceChannelId;
            return this;
        }

        public Builder guildId(String guildId) {
            this.guildId = guildId;
            return this;
        }

        public Builder textChannelId(String textChannelId) {
            this.textChannelId = textChannelId;
            return this;
        }

        public MockCommandInteraction build() {
            return new MockCommandInteraction(name, options, subcommandName, voiceChannelId, guildId, textChannelId);
        }
    }
}

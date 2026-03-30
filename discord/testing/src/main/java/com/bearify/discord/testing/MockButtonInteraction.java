package com.bearify.discord.testing;

import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.ReplyBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MockButtonInteraction implements ButtonInteraction {

    private final String customId;
    private final String guildId;
    private final String textChannelId;
    private final String voiceChannelId;
    private final String userMention;
    private final List<MockReplyBuilder> replies = new ArrayList<>();
    private boolean acknowledged;
    private MockEditableMessage deferredMessage;
    private boolean deferredEphemeral;

    private MockButtonInteraction(String customId, String guildId, String textChannelId, String voiceChannelId, String userMention) {
        this.customId = customId;
        this.guildId = guildId;
        this.textChannelId = textChannelId;
        this.voiceChannelId = voiceChannelId;
        this.userMention = userMention;
    }

    @Override
    public String getCustomId() {
        return customId;
    }

    @Override
    public ReplyBuilder reply(String message) {
        MockReplyBuilder reply = new MockReplyBuilder(message);
        replies.add(reply);
        return reply;
    }

    @Override
    public void acknowledge() {
        this.acknowledged = true;
    }

    @Override
    public Optional<String> getGuildId() {
        return Optional.ofNullable(guildId);
    }

    @Override
    public Optional<String> getTextChannelId() {
        return Optional.ofNullable(textChannelId);
    }

    @Override
    public Optional<String> getVoiceChannelId() {
        return Optional.ofNullable(voiceChannelId);
    }

    @Override
    public String getUserMention() {
        return userMention;
    }

    public List<MockReplyBuilder> getReplies() {
        return List.copyOf(replies);
    }

    public Optional<MockEditableMessage> getDeferredMessage() {
        return Optional.ofNullable(deferredMessage);
    }

    public boolean isDeferredEphemeral() {
        return deferredEphemeral;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public static Builder forButton(String customId) {
        return new Builder(customId);
    }

    public static class Builder {
        private final String customId;
        private String guildId;
        private String textChannelId;
        private String voiceChannelId;
        private String userMention = "@button-user";

        private Builder(String customId) {
            this.customId = customId;
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

        public Builder userMention(String userMention) {
            this.userMention = userMention;
            return this;
        }

        public MockButtonInteraction build() {
            return new MockButtonInteraction(customId, guildId, textChannelId, voiceChannelId, userMention);
        }
    }
}

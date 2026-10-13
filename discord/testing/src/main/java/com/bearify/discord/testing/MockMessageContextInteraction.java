package com.bearify.discord.testing;

import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.api.interaction.MessageContextInteraction;
import com.bearify.discord.api.interaction.ReplyBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MockMessageContextInteraction implements MessageContextInteraction {

    private final String name;
    private final String targetMessageContent;
    private final String targetAuthorMention;
    private final String voiceChannelId;
    private final String guildId;
    private final String textChannelId;
    private MockEditableMessage deferredMessage;
    private boolean deferredEphemeral = false;
    private final List<MockReplyBuilder> replies = new ArrayList<>();

    private MockMessageContextInteraction(String name, String targetMessageContent, String targetAuthorMention,
                                          String voiceChannelId, String guildId, String textChannelId) {
        this.name = name;
        this.targetMessageContent = targetMessageContent;
        this.targetAuthorMention = targetAuthorMention;
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
    public String getTargetMessageContent() {
        return targetMessageContent;
    }

    @Override
    public String getTargetAuthorMention() {
        return targetAuthorMention;
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

    public static Builder forMessageCommand(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String targetMessageContent = "";
        private String targetAuthorMention = "<@author>";
        private String voiceChannelId;
        private String guildId;
        private String textChannelId;

        private Builder(String name) {
            this.name = name;
        }

        public Builder targetMessage(String content) {
            this.targetMessageContent = content;
            return this;
        }

        public Builder targetAuthorMention(String mention) {
            this.targetAuthorMention = mention;
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

        public MockMessageContextInteraction build() {
            return new MockMessageContextInteraction(name, targetMessageContent, targetAuthorMention, voiceChannelId, guildId, textChannelId);
        }
    }
}

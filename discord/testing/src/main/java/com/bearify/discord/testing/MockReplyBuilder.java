package com.bearify.discord.testing;

import com.bearify.discord.api.interaction.ReplyBuilder;
import com.bearify.discord.api.message.ComponentMessage;

import java.util.Optional;

public class MockReplyBuilder implements ReplyBuilder {

    private final String content;
    private final Optional<ComponentMessage> componentMessage;
    private boolean ephemeral = false;
    private boolean kept = false;
    private boolean sent = false;

    MockReplyBuilder(String content) {
        this.content = content;
        this.componentMessage = Optional.empty();
    }

    MockReplyBuilder(ComponentMessage componentMessage) {
        this.content = "";
        this.componentMessage = Optional.of(componentMessage);
    }

    @Override
    public ReplyBuilder ephemeral() {
        this.ephemeral = true;
        return this;
    }

    @Override
    public ReplyBuilder keep() {
        this.kept = true;
        return this;
    }

    @Override
    public void send() {
        this.sent = true;
    }

    /** The text of a plain reply; empty for a component message reply. */
    public String getContent() {
        return content;
    }

    public Optional<ComponentMessage> getComponentMessage() {
        return componentMessage;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    public boolean isKept() {
        return kept;
    }

    public boolean isSent() {
        return sent;
    }
}

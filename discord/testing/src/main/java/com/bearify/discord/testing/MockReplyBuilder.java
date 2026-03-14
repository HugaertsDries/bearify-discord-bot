package com.bearify.discord.testing;

import com.bearify.discord.api.interaction.ReplyBuilder;

public class MockReplyBuilder implements ReplyBuilder {

    private final String content;
    private boolean ephemeral = false;
    private boolean sent = false;

    MockReplyBuilder(String content) {
        this.content = content;
    }

    @Override
    public ReplyBuilder ephemeral() {
        this.ephemeral = true;
        return this;
    }

    @Override
    public void send() {
        this.sent = true;
    }

    public String getContent() {
        return content;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    public boolean isSent() {
        return sent;
    }
}

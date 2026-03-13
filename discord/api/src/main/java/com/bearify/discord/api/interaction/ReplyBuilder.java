package com.bearify.discord.api.interaction;

public interface ReplyBuilder {

    ReplyBuilder ephemeral();

    void send();
}

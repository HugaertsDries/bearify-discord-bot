package com.bearify.discord.api.interaction;

public interface ButtonInteraction extends Interaction {

    String getCustomId();

    void acknowledge();

    ReplyBuilder reply(String message);
}

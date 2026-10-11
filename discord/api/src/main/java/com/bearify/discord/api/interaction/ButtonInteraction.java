package com.bearify.discord.api.interaction;

import com.bearify.discord.api.message.ComponentMessage;

public interface ButtonInteraction extends Interaction {

    String getCustomId();

    void acknowledge();

    ReplyBuilder reply(String message);

    ReplyBuilder reply(ComponentMessage message);
}

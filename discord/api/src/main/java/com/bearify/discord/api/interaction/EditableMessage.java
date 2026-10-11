package com.bearify.discord.api.interaction;

import com.bearify.discord.api.message.ComponentMessage;

import java.time.Instant;

public interface EditableMessage {

    Instant getCreatedAt();

    void edit(String message);

    void edit(ComponentMessage message);
}

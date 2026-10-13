package com.bearify.discord.api.interaction;

import com.bearify.discord.api.message.ComponentMessage;

import java.time.Instant;

public interface EditableMessage {

    Instant getCreatedAt();

    /** Leaves an ephemeral reply until the user dismisses it, instead of letting it clean itself up. */
    EditableMessage keep();

    void edit(String message);

    void edit(ComponentMessage message);
}

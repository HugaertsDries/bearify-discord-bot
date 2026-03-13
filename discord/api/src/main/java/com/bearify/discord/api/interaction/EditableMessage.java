package com.bearify.discord.api.interaction;

import java.time.Instant;

public interface EditableMessage {

    Instant getCreatedAt();

    void edit(String message);
}

package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.EditableMessage;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.time.Instant;

class JdaEditableMessage implements EditableMessage {

    private final InteractionHook hook;
    private final Instant createdAt;

    JdaEditableMessage(InteractionHook hook, Instant createdAt) {
        this.hook = hook;
        this.createdAt = createdAt;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public void edit(String message) {
        hook.editOriginal(message).complete();
    }
}

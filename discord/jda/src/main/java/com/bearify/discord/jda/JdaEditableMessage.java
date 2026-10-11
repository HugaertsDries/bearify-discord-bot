package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.api.message.ComponentMessage;
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

    @Override
    public void edit(ComponentMessage message) {
        ComponentMessageMapper mapper = new ComponentMessageMapper();
        hook.editOriginalComponents(mapper.toJdaComponents(message)).useComponentsV2().setFiles(mapper.filesFor(message)).complete();
    }
}

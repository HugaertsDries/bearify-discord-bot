package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.ReplyBuilder;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

class JdaReplyBuilder implements ReplyBuilder {

    private final IReplyCallback event;
    private final String message;
    private boolean ephemeral = false;

    JdaReplyBuilder(IReplyCallback event, String message) {
        this.event = event;
        this.message = message;
    }

    @Override
    public ReplyBuilder ephemeral() {
        this.ephemeral = true;
        return this;
    }

    @Override
    public void send() {
        event.reply(message).setEphemeral(ephemeral).queue();
    }
}

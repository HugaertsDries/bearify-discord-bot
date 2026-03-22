package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * JDA-backed implementation of {@link TextChannel}.
 */
class JdaTextChannel implements TextChannel {

    private final MessageChannel channel;

    JdaTextChannel(MessageChannel channel) {
        this.channel = channel;
    }

    @Override
    public void send(String message) {
        channel.sendMessage(message).queue();
    }
}

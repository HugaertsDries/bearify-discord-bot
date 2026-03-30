package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

class JdaTextChannel implements TextChannel {

    private final MessageChannel channel;
    private final ComponentMessageMapper componentMessageMapper = new ComponentMessageMapper();

    JdaTextChannel(MessageChannel channel) {
        this.channel = channel;
    }

    @Override
    public void send(String message) {
        channel.sendMessage(message).queue();
    }

    @Override
    public SentMessage send(ComponentMessage componentMessage) {
        Message message = channel.sendMessageComponents(componentMessageMapper.toJdaComponents(componentMessage))
                .useComponentsV2()
                .complete();
        return new SentMessage() {
            @Override
            public void delete() {
                message.delete().queue();
            }

            @Override
            public void update(ComponentMessage updated) {
                message.editMessageComponents(componentMessageMapper.toJdaComponents(updated))
                        .useComponentsV2()
                        .complete();
            }
        };
    }
}

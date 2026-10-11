package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.List;

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
        MessageCreateAction send = channel.sendMessageComponents(componentMessageMapper.toJdaComponents(componentMessage))
                .useComponentsV2();
        List<FileUpload> files = componentMessageMapper.filesFor(componentMessage);
        Message message = (files.isEmpty() ? send : send.addFiles(files)).complete();
        return new SentMessage() {
            @Override
            public void delete() {
                message.delete().queue();
            }

            @Override
            public void update(ComponentMessage updated) {
                // replaces the attachments instead of adding to them, so repeated edits don't pile up spacer images
                message.editMessageComponents(componentMessageMapper.toJdaComponents(updated))
                        .useComponentsV2()
                        .setFiles(componentMessageMapper.filesFor(updated))
                        .complete();
            }
        };
    }
}

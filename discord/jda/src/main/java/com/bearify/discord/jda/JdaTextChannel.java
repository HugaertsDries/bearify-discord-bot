package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class JdaTextChannel implements TextChannel {

    private static final Logger LOG = LoggerFactory.getLogger(JdaTextChannel.class);

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
            public String id() {
                return message.getId();
            }

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

    @Override
    public void delete(String messageId) {
        channel.deleteMessageById(messageId).queue(
                ignored -> {},
                new ErrorHandler(failure -> LOG.warn("Could not delete message {} in channel {}", messageId, channel.getId(), failure))
                        // someone already deleted it by hand, which is the outcome we wanted anyway
                        .handle(ErrorResponse.UNKNOWN_MESSAGE,
                                gone -> LOG.debug("Message {} in channel {} was already deleted", messageId, channel.getId())));
    }
}

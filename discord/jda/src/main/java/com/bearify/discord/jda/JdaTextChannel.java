package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.EmbedMessage;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.List;

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

    @Override
    public SentMessage send(EmbedMessage embed) {
        Message message = channel.sendMessageEmbeds(toJdaEmbed(embed).build())
                .addFiles(toUploads(embed))
                .complete();
        return new SentMessage() {
            @Override
            public void delete() {
                message.delete().queue();
            }

            @Override
            public void update(EmbedMessage updated) {
                message.editMessageEmbeds(toJdaEmbed(updated).build())
                        .setFiles(toUploads(updated))
                        .complete();
            }
        };
    }

    private static EmbedBuilder toJdaEmbed(EmbedMessage embed) {
        var builder = new EmbedBuilder()
                .setColor(embed.color())
                .setTitle(embed.title(), embed.titleUrl())
                .setFooter(embed.footer());
        embed.authorText().ifPresent(builder::setAuthor);
        embed.description().ifPresent(builder::setDescription);
        embed.imageFilename().ifPresent(f -> builder.setImage("attachment://" + f));
        embed.thumbnailFilename().ifPresent(f -> builder.setThumbnail("attachment://" + f));
        embed.fields().forEach(f -> builder.addField(f.name(), f.value(), f.inline()));
        return builder;
    }

    private static List<FileUpload> toUploads(EmbedMessage embed) {
        return embed.attachments().stream()
                .map(a -> FileUpload.fromData(a.data(), a.filename()))
                .toList();
    }
}

package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;

@Command(value = "player", description = "Player commands")
public class MusicPlayerCommand {

    private static final String NOT_IN_VOICE_CHANNEL_MESSAGE = BearifyEmoji.SPEAKER + " Climb into a voice channel first, then call the bear.";
    private static final String NOT_IN_GUILD_MESSAGE = BearifyEmoji.BEAR + " I only lumber around inside a server.";
    private static final String JOINING_MESSAGE = BearifyEmoji.BEAR + " Bearify is padding over to your voice channel.";
    private static final String READY_MESSAGE = BearifyEmoji.MUSIC + " Bearify is in the channel and ready to play.";
    private static final String UNAVAILABLE_MESSAGE = BearifyEmoji.BEAR + " No music bears are free right now. Try again in a moment.";

    private final MusicPlayerPool pool;

    public MusicPlayerCommand(MusicPlayerPool pool) {
        this.pool = pool;
    }

    @SuppressWarnings("unused")
    @Interaction(value = "join", description = "Lure a bear to you. At your own risk.")
    public void join(CommandInteraction interaction) {
        if (interaction.getVoiceChannelId().isEmpty()) {
            interaction.reply(NOT_IN_VOICE_CHANNEL_MESSAGE).ephemeral().send();
            return;
        }

        if (interaction.getGuildId().isEmpty()) {
            interaction.reply(NOT_IN_GUILD_MESSAGE).ephemeral().send();
            return;
        }

        String voiceChannelId = interaction.getVoiceChannelId().orElseThrow();
        String guildId = interaction.getGuildId().orElseThrow();
        pool.acquire(guildId, voiceChannelId).ifPresentOrElse(player -> {
            EditableMessage message = interaction.defer();
            message.edit(JOINING_MESSAGE);
            player.join().thenAccept(event -> message.edit(READY_MESSAGE));
        }, () -> interaction.defer().edit(UNAVAILABLE_MESSAGE));
    }
}

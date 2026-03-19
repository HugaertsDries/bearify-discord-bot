package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerJoinResultHandler;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;

import java.util.Optional;

@Command(value = "player", description = "Player commands")
public class MusicPlayerCommand {

    private static final String NOT_IN_VOICE_CHANNEL_MESSAGE = BearifyEmoji.SPEAKER + " Climb into a voice channel first, then call the bear.";
    private static final String NOT_IN_GUILD_MESSAGE = BearifyEmoji.BEAR + " I only lumber around inside a server.";
    private static final String JOINING_MESSAGE = BearifyEmoji.BEAR + " Bearify is padding over to your voice channel.";
    private static final String READY_MESSAGE = BearifyEmoji.MUSIC + " Bearify is in the channel and ready to play.";
    private static final String UNAVAILABLE_MESSAGE = BearifyEmoji.BEAR + " No music bears are free right now. Try again in a moment.";
    private static final String CONNECT_FAILED_MESSAGE = BearifyEmoji.BEAR + " The bear couldn't reach your channel. Try again in a moment.";
    private static final String LEAVING_MESSAGE = BearifyEmoji.BEAR + " Bearify is shuffling out of the channel.";
    private static final String NO_PLAYER_IN_CHANNEL_MESSAGE = BearifyEmoji.SPEAKER + " No bear is playing in your channel right now.";

    private final MusicPlayerPool pool;

    public MusicPlayerCommand(MusicPlayerPool pool) {
        this.pool = pool;
    }

    @SuppressWarnings("unused")
    @Interaction(value = "join", description = "Lure a bear to you. At your own risk.")
    public void join(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx -> {
            pool.acquire(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(player -> {
                EditableMessage message = interaction.defer();
                message.edit(JOINING_MESSAGE);
                player.join(new MusicPlayerJoinResultHandler() {
                    public void onReady() { message.edit(READY_MESSAGE); }
                    public void onFailed(String reason) { message.edit(CONNECT_FAILED_MESSAGE); }
                });
            }, () -> interaction.defer().edit(UNAVAILABLE_MESSAGE));
        });
    }

    @SuppressWarnings("unused")
    @Interaction(value = "leave", description = "Send the bear home.")
    public void leave(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx -> {
            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(player -> {
                player.stop();
                interaction.reply(LEAVING_MESSAGE).send();
            }, () -> interaction.reply(NO_PLAYER_IN_CHANNEL_MESSAGE).ephemeral().send());
        });
    }

    private Optional<ChannelContext> requireVoiceSession(CommandInteraction interaction) {
        if (interaction.getVoiceChannelId().isEmpty()) {
            interaction.reply(NOT_IN_VOICE_CHANNEL_MESSAGE).ephemeral().send();
            return Optional.empty();
        }
        if (interaction.getGuildId().isEmpty()) {
            interaction.reply(NOT_IN_GUILD_MESSAGE).ephemeral().send();
            return Optional.empty();
        }
        return Optional.of(new ChannelContext(
                interaction.getGuildId().orElseThrow(),
                interaction.getVoiceChannelId().orElseThrow()));
    }

    private record ChannelContext(String guildId, String voiceChannelId) {}
}

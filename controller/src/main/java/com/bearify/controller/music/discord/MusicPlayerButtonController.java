package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;

import java.time.Duration;
import java.util.Optional;

@DiscordController
public class MusicPlayerButtonController {

    private static final String NOT_IN_VOICE_CHANNEL_MESSAGE = BearifyEmoji.BLOCKED + " | Looks like you're not in a voice channel! Jump in one and try again.";
    private static final String NOT_IN_GUILD_MESSAGE = BearifyEmoji.BLOCKED + " | I only lumber around inside a server.";
    private static final String WRONG_VOICE_CHANNEL_MESSAGE = BearifyEmoji.BLOCKED + " | You need to be in the same voice channel as the active player to use these controls.";
    private static final Duration DEFAULT_SEEK = Duration.ofSeconds(30);

    private final MusicPlayerPool pool;

    public MusicPlayerButtonController(MusicPlayerPool pool) {
        this.pool = pool;
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:pause-play")
    public void pausePlay(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.togglePause(interaction.getUserMention(), new MusicPlayerEventListener() {});
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:previous")
    public void previous(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.previous(interaction.getUserMention(), new MusicPlayerEventListener() {});
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:rewind")
    public void rewind(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.rewind(DEFAULT_SEEK, interaction.getUserMention());
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:forward")
    public void forward(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.forward(DEFAULT_SEEK, interaction.getUserMention(), new MusicPlayerEventListener() {});
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:next")
    public void next(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.next(interaction.getUserMention(), new MusicPlayerEventListener() {});
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:clear")
    public void clear(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.clear(interaction.getUserMention());
        });
    }

    private Optional<MusicPlayer> requirePlayer(ButtonInteraction interaction) {
        if (interaction.getVoiceChannelId().isEmpty()) {
            interaction.reply(NOT_IN_VOICE_CHANNEL_MESSAGE).ephemeral().send();
            return Optional.empty();
        }
        if (interaction.getGuildId().isEmpty()) {
            interaction.reply(NOT_IN_GUILD_MESSAGE).ephemeral().send();
            return Optional.empty();
        }
        return pool.find(interaction.getGuildId().orElseThrow(), interaction.getVoiceChannelId().orElseThrow())
                .or(() -> {
                    interaction.reply(WRONG_VOICE_CHANNEL_MESSAGE).ephemeral().send();
                    return Optional.empty();
                });
    }
}

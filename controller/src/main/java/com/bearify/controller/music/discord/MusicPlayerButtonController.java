package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.api.interaction.ReplyBuilder;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;

import java.util.Optional;

@DiscordController
public class MusicPlayerButtonController {

    private static final String NOT_IN_VOICE_CHANNEL_MESSAGE = BearifyEmoji.BLOCKED + " | Looks like you're not in a voice channel! Jump in one and try again.";
    private static final String NOT_IN_GUILD_MESSAGE = BearifyEmoji.BLOCKED + " | I only lumber around inside a server.";
    private static final String WRONG_VOICE_CHANNEL_MESSAGE = BearifyEmoji.BLOCKED + " | You need to be in the same voice channel as the active player to use these controls.";

    private final MusicPlayerPool pool;

    public MusicPlayerButtonController(MusicPlayerPool pool) {
        this.pool = pool;
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:pause-play")
    public void pausePlay(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.togglePause(interaction.getUserMention(), answerFailuresPrivately(interaction));
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:previous")
    public void previous(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.previous(interaction.getUserMention(), answerFailuresPrivately(interaction));
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:next")
    public void next(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.next(interaction.getUserMention(), answerFailuresPrivately(interaction));
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:clear")
    public void clear(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.clear(interaction.getUserMention(), answerFailuresPrivately(interaction));
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:loop")
    public void loop(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            player.cycleLoop(interaction.getUserMention(), answerFailuresPrivately(interaction));
        });
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.BUTTON, value = "player:queue")
    public void queue(ButtonInteraction interaction) {
        requirePlayer(interaction).ifPresent(player -> {
            interaction.acknowledge();
            ReplyBuilder reply = switch (QueueCard.beyondPlayer(player.queue())) {
                case QueueCard.Text text -> interaction.reply(text.text());
                case QueueCard.Card card -> interaction.reply(card.message());
            };
            reply.ephemeral().send();
        });
    }

    // success already shows as a label on the player message, so only failures get an answer
    private static MusicPlayerEventListener answerFailuresPrivately(ButtonInteraction interaction) {
        return new AnsweringListener(text -> interaction.reply(text).ephemeral().send());
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

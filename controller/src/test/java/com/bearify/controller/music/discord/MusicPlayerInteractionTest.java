package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEvent;
import com.bearify.controller.music.domain.MusicPlayerEventHandler;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.testing.MockCommandInteraction;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerInteractionTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String PLAYER_ID = "player-1";

    // --- HAPPY PATH ---

    @Test
    void delegatesSummonToUseCase() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer(PLAYER_ID);
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.of(musicPlayer));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(pool).join(interaction);

        assertThat(pool.guildId).isEqualTo(GUILD_ID);
        assertThat(pool.voiceChannelId).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(musicPlayer.joined).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Bearify is padding over to your voice channel");
    }

    @Test
    void rejectsWhenUserNotInVoiceChannel() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer(PLAYER_ID)))).join(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("voice channel");
    }

    @Test
    void rejectsWhenNotInGuild() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .build();

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer(PLAYER_ID)))).join(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("server");
    }

    @Test
    void showsUnavailableMessageWhenNoPlayerCanBeSummoned() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.empty())).join(interaction);

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("No music bears are free right now")
                .contains("Try again in a moment");
    }

    @Test
    void updatesDeferredMessageWhenPlayerBecomesReady() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer(PLAYER_ID);
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).join(interaction);
        musicPlayer.fireReadyEvent();

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Bearify is in the channel and ready to play");
    }

    // --- VALIDATION ---

    private static final class RecordingMusicPlayerPool extends MusicPlayerPool {
        private final Optional<MusicPlayer> player;
        private String guildId;
        private String voiceChannelId;

        private RecordingMusicPlayerPool(Optional<MusicPlayer> player) {
            super(null, null, interaction -> {});
            this.player = player;
        }

        public Optional<MusicPlayer> acquire(String guildId, String voiceChannelId) {
            this.guildId = guildId;
            this.voiceChannelId = voiceChannelId;
            return player;
        }
    }

    private static final class RecordingMusicPlayer extends MusicPlayer {
        private final String playerId;
        private boolean joined;
        private MusicPlayerEventHandler eventHandler;

        private RecordingMusicPlayer(String playerId) {
            super(playerId, null, null, null, interaction -> {});
            this.playerId = playerId;
        }

        private void fireReadyEvent() {
            eventHandler.onReady(new MusicPlayerEvent.Ready(playerId));
        }

        public void join(MusicPlayerEventHandler eventHandler) {
            joined = true;
            this.eventHandler = eventHandler;
        }
    }
}

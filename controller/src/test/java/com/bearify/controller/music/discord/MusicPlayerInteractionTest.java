package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerJoinResultHandler;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.testing.MockCommandInteraction;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerInteractionTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String PLAYER_ID = "player-1";

    // --- JOIN: HAPPY PATH ---

    @Test
    void delegatesSummonToUseCase() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.of(musicPlayer));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(pool).join(interaction);

        assertThat(pool.acquireGuildId).isEqualTo(GUILD_ID);
        assertThat(pool.acquireVoiceChannelId).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(musicPlayer.joined).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Bearify is padding over to your voice channel");
    }

    @Test
    void updatesDeferredMessageWhenPlayerBecomesReady() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).join(interaction);
        musicPlayer.fireReady();

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Bearify is in the channel and ready to play");
    }

    @Test
    void showsConnectFailedMessageWhenJoinFails() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).join(interaction);
        musicPlayer.fireFailed("timed out");

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("The bear couldn't reach your channel");
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

    // --- JOIN: VALIDATION ---

    @Test
    void rejectsWhenUserNotInVoiceChannel() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer()))).join(interaction);

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

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer()))).join(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("server");
    }

    // --- LEAVE: HAPPY PATH ---

    @Test
    void delegatesLeaveToPlayer() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.of(musicPlayer));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(pool).leave(interaction);

        assertThat(pool.findGuildId).isEqualTo(GUILD_ID);
        assertThat(pool.findVoiceChannelId).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(musicPlayer.stopped).isTrue();
        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().getContent()).contains("shuffling out of the channel");
    }

    @Test
    void showsNoPlayerMessageWhenNoPlayerInChannel() {
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.empty());
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(pool).leave(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("No bear is playing");
    }

    // --- LEAVE: VALIDATION ---

    @Test
    void rejectsLeaveWhenUserNotInVoiceChannel() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.empty())).leave(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("voice channel");
    }

    @Test
    void rejectsLeaveWhenNotInGuild() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .build();

        new MusicPlayerCommand(new RecordingMusicPlayerPool(Optional.empty())).leave(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("server");
    }

    // --- STUBS ---

    private static final class RecordingMusicPlayerPool implements MusicPlayerPool {
        private final Optional<MusicPlayer> player;
        private String acquireGuildId;
        private String acquireVoiceChannelId;
        private String findGuildId;
        private String findVoiceChannelId;

        private RecordingMusicPlayerPool(Optional<MusicPlayer> player) {
            this.player = player;
        }

        @Override
        public Optional<MusicPlayer> acquire(String guildId, String voiceChannelId) {
            this.acquireGuildId = guildId;
            this.acquireVoiceChannelId = voiceChannelId;
            return player;
        }

        @Override
        public Optional<MusicPlayer> find(String guildId, String voiceChannelId) {
            this.findGuildId = guildId;
            this.findVoiceChannelId = voiceChannelId;
            return player;
        }
    }

    private static final class RecordingMusicPlayer implements MusicPlayer {
        private boolean joined;
        private boolean stopped;
        private Runnable onReady;
        private Consumer<String> onFailed;

        private void fireReady() {
            onReady.run();
        }

        private void fireFailed(String reason) {
            onFailed.accept(reason);
        }

        @Override
        public void join(MusicPlayerJoinResultHandler handler) {
            joined = true;
            onReady = handler::onReady;
            onFailed = handler::onFailed;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }
}

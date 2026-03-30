package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.music.player.bridge.model.TrackRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerInteractionTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String TEXT_CHANNEL_ID = "txt-789";

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

        new MusicPlayerCommandController(pool).join(interaction);

        assertThat(pool.acquireGuildId).isEqualTo(GUILD_ID);
        assertThat(pool.acquireVoiceChannelId).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(musicPlayer.joined).isTrue();
        assertThat(interaction.isDeferredEphemeral()).isFalse();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Sending a bear your way");
    }

    @Test
    void updatesDeferredMessageWhenPlayerBecomesReady() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).join(interaction);
        musicPlayer.fireReady();

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Joined <#" + VOICE_CHANNEL_ID + ">");
    }

    @Test
    void showsConnectFailedMessageWhenJoinFails() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).join(interaction);
        musicPlayer.fireFailed("timed out");

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("The bear couldn't reach your channel");
    }

    // --- JOIN: VALIDATION ---

    @Test
    void rejectsWhenUserNotInVoiceChannel() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer()))).join(interaction);

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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer()))).join(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("server");
    }

    // --- LEAVE ---

    @Test
    void delegatesLeaveToPlayer() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.of(musicPlayer));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommandController(pool).leave(interaction);

        assertThat(pool.findGuildId).isEqualTo(GUILD_ID);
        assertThat(pool.findVoiceChannelId).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(musicPlayer.stopped).isTrue();
        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isFalse();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("cleaning up after myself");
    }

    @Test
    void showsNoPlayerMessageWhenNoPlayerInChannel() {
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.empty());
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommandController(pool).leave(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("not even playing songs");
    }

    @Test
    void showsOtherChannelMessageWhenLeavingWithoutBearInVoiceChannel() {
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.empty(), true);
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommandController(pool).leave(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent())
                .contains("can't lumber out of your voice channel when I'm not with you");
    }

    // --- PLAY ---

    @Test
    void showsTrackNotFoundMessageWhenPlayCannotFindTrack() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("play")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .option("search", "missing")
                .build();

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).play(interaction, "missing");
        musicPlayer.fireTrackNotFound("missing");

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("I couldn't sniff out that track. Try a different search or link?");
    }

    @Test
    void showsTrackLoadFailedMessageWhenPlayCannotLoadTrack() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("play")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .option("search", "blocked")
                .build();

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).play(interaction, "blocked");
        musicPlayer.fireTrackLoadFailed("region blocked");

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Something went wrong loading this track");
    }

    // --- PAUSE ---

    @Test
    void showsPausedMessageAsEphemeralConfirmation() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("pause");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).pause(interaction);
        musicPlayer.firePaused();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Paused the current track for you.");
    }

    @Test
    void showsResumedMessageAsEphemeralConfirmation() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("pause");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).pause(interaction);
        musicPlayer.fireResumed();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Resumed the current track for you.");
    }

    // --- PREVIOUS ---

    @Test
    void showsPreviousMessageAsEphemeralConfirmation() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("previous");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).previous(interaction);

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Back on the trail to the previous track.");
    }

    @Test
    void showsTrailBeginsMessageWhenThereIsNothingToGoBackTo() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("previous");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).previous(interaction);
        musicPlayer.fireNothingToGoBack();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("This is where the trail begins. Nothing to go back to!");
    }

    // --- NEXT ---

    @Test
    void showsNextMessageAsEphemeralConfirmation() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("next");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).next(interaction);

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("On to the next track.");
    }

    @Test
    void showsTrailEndsMessageWhenQueueIsEmpty() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("next");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).next(interaction);
        musicPlayer.fireNextNothingToAdvance();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("This is where the trail ends. Try adding more using `/player play` command!");
    }

    // --- REWIND ---

    @Test
    void showsSarcasticMessageWhenRewindingByZeroSeconds() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("rewind");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).rewind(interaction, 0);

        assertThat(musicPlayer.rewindSeek).isEqualTo(Duration.ZERO);
        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent())
                .contains("Bold strategy. Rewinding by exactly 0 seconds.");
    }

    @Test
    void showsDurationMessageWhenRewindingByPositiveSeconds() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("rewind");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).rewind(interaction, 30);

        assertThat(musicPlayer.rewindSeek).isEqualTo(Duration.ofSeconds(30));
        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent())
                .contains("Rewound the current track by 30s for you.");
    }

    // --- FORWARD ---

    @Test
    void showsSarcasticMessageWhenForwardingByZeroSeconds() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("forward");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).forward(interaction, 0);

        assertThat(musicPlayer.forwardSeek).isEqualTo(Duration.ZERO);
        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Bold strategy. Forwarding by exactly 0 seconds.");
    }

    @Test
    void showsDurationMessageWhenForwardingByPositiveSeconds() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("forward");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).forward(interaction, 30);

        assertThat(musicPlayer.forwardSeek).isEqualTo(Duration.ofSeconds(30));
        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Forwarded the current track by 30s for you.");
    }

    @Test
    void showsTrailEndsMessageWhenForwardRunsOutOfQueue() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("forward");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).forward(interaction, 5);
        musicPlayer.fireForwardNothingToAdvance();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("This is where the trail ends. Try adding more using `/player play` command!");
    }

    // --- CLEAR ---

    @Test
    void showsClearMessageAsEphemeralConfirmation() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("clear");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer))).clear(interaction);

        assertThat(musicPlayer.cleared).isTrue();
        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("Cleared the playlist. Fresh paws.");
    }

    // --- LEAVE: VALIDATION ---

    @Test
    void rejectsLeaveWhenUserNotInVoiceChannel() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.empty())).leave(interaction);

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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.empty())).leave(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("server");
    }

    private MockCommandInteraction buildInteraction(String subcommand) {
        return MockCommandInteraction.forCommand("player")
                .subcommand(subcommand)
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .build();
    }

    private static final class RecordingMusicPlayerPool implements MusicPlayerPool {
        private final Optional<MusicPlayer> player;
        private final boolean activeSessionForGuild;
        private String acquireGuildId;
        private String acquireVoiceChannelId;
        private String findGuildId;
        private String findVoiceChannelId;

        private RecordingMusicPlayerPool(Optional<MusicPlayer> player) {
            this(player, false);
        }

        private RecordingMusicPlayerPool(Optional<MusicPlayer> player, boolean activeSessionForGuild) {
            this.player = player;
            this.activeSessionForGuild = activeSessionForGuild;
        }

        @Override
        public MusicPlayer acquire(String guildId, String voiceChannelId) {
            this.acquireGuildId = guildId;
            this.acquireVoiceChannelId = voiceChannelId;
            return player.orElseThrow(() -> new IllegalStateException("No player available"));
        }

        @Override
        public Optional<MusicPlayer> find(String guildId, String voiceChannelId) {
            this.findGuildId = guildId;
            this.findVoiceChannelId = voiceChannelId;
            return player;
        }

        @Override
        public boolean hasActiveSessionFor(String guildId) {
            return activeSessionForGuild;
        }
    }

    private static final class RecordingMusicPlayer implements MusicPlayer {
        private boolean joined;
        private boolean stopped;
        private boolean cleared;
        private Duration rewindSeek;
        private Duration forwardSeek;
        private MusicPlayerEventListener joinHandler;
        private MusicPlayerEventListener playHandler;
        private MusicPlayerEventListener togglePauseHandler;
        private MusicPlayerEventListener previousHandler;
        private MusicPlayerEventListener nextHandler;
        private MusicPlayerEventListener forwardHandler;

        private void fireReady() {
            joinHandler.onReady();
        }

        private void fireFailed(String reason) {
            joinHandler.onFailed(reason);
        }

        private void fireTrackNotFound(String query) {
            playHandler.onTrackNotFound(query);
        }

        private void fireTrackLoadFailed(String reason) {
            playHandler.onTrackLoadFailed(reason);
        }

        private void firePaused() {
            togglePauseHandler.onPaused();
        }

        private void fireResumed() {
            togglePauseHandler.onResumed();
        }

        private void fireNothingToGoBack() {
            previousHandler.onNothingToGoBack();
        }

        private void fireNextNothingToAdvance() {
            nextHandler.onNothingToAdvance();
        }

        private void fireForwardNothingToAdvance() {
            forwardHandler.onNothingToAdvance();
        }

        @Override
        public void join(MusicPlayerEventListener handler) {
            joined = true;
            joinHandler = handler;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public void play(TrackRequest request, MusicPlayerEventListener handler) {
            playHandler = handler;
        }

        @Override
        public void togglePause(String requesterTag, MusicPlayerEventListener handler) {
            togglePauseHandler = handler;
        }

        @Override
        public void previous(String requesterTag, MusicPlayerEventListener handler) {
            previousHandler = handler;
        }

        @Override
        public void next(String requesterTag, MusicPlayerEventListener handler) {
            nextHandler = handler;
        }

        @Override
        public void rewind(Duration seek, String requesterTag) {
            rewindSeek = seek;
        }

        @Override
        public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            forwardSeek = seek;
            forwardHandler = handler;
        }

        @Override
        public void clear(String requesterTag) {
            cleared = true;
        }
    }
}

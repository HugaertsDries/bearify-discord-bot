package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.spotify.SpotifyLinkResolver;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.model.TrackRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerInteractionTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String TEXT_CHANNEL_ID = "txt-789";
    private static final SpotifyLinkResolver NO_SPOTIFY = new SpotifyLinkResolver(uri -> Optional.empty());

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

        new MusicPlayerCommandController(pool, NO_SPOTIFY).join(interaction);

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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).join(interaction);
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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).join(interaction);
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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer())), NO_SPOTIFY).join(interaction);

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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer())), NO_SPOTIFY).join(interaction);

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

        new MusicPlayerCommandController(pool, NO_SPOTIFY).leave(interaction);
        musicPlayer.stopHandler.onStopped();

        assertThat(pool.findGuildId).isEqualTo(GUILD_ID);
        assertThat(pool.findVoiceChannelId).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(musicPlayer.stopped).isTrue();
        assertThat(interaction.isDeferredEphemeral()).isFalse();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow()).contains("cleaning up after myself");
    }

    @Test
    void waitsForTheBearBeforeSayingGoodbye() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("leave");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).leave(interaction);

        assertThat(interaction.getDeferredMessage().orElseThrow().hasEdits()).isFalse();
        assertThat(interaction.getReplies()).isEmpty();
    }

    @Test
    void showsTimeoutMessageWhenBearDoesNotConfirmLeaving() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("leave");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).leave(interaction);
        musicPlayer.stopHandler.onTimedOut();

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow()).contains("didn't answer in time");
    }

    @Test
    void showsNoPlayerMessageWhenNoPlayerInChannel() {
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.empty());
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommandController(pool, NO_SPOTIFY).leave(interaction);

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

        new MusicPlayerCommandController(pool, NO_SPOTIFY).leave(interaction);

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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).play(interaction, "missing");
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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).play(interaction, "blocked");
        musicPlayer.fireTrackLoadFailed("region blocked");

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Something went wrong loading this track");
    }

    @Test
    void showsQueuedTitleWhenTrackIsQueued() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("play");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).play(interaction, "one more time");
        musicPlayer.playHandler.onTrackQueued(TRACK, 1);

        assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(QUEUED_TRACK_MESSAGE);
    }

    @Test
    void showsQueuedCountWhenPlaylistIsQueued() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("play");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).play(interaction, "https://youtube.com/playlist?list=PLxyz");
        musicPlayer.playHandler.onTrackQueued(TRACK, 12);

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Queued **12** tracks from the playlist.");
    }

    @Test
    void defersBeforeReadingSpotifyLink() {
        MockCommandInteraction interaction = buildInteraction("play");
        AtomicBoolean deferredBeforeFetch = new AtomicBoolean();
        SpotifyLinkResolver spotify = new SpotifyLinkResolver(uri -> {
            deferredBeforeFetch.set(interaction.getDeferredMessage().isPresent());
            return Optional.empty();
        });

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer())), spotify)
                .play(interaction, "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT");

        assertThat(deferredBeforeFetch).isTrue();
        assertThat(interaction.isDeferredEphemeral()).isTrue();
    }

    @Test
    void showsTimeoutMessageWhenPlayIsNotAnswered() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("play");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).play(interaction, "one more time");
        musicPlayer.playHandler.onTimedOut();

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow()).contains("didn't answer in time");
    }

    @Test
    void replacesJoiningMessageWhenPlayAfterJoinFails() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MusicPlayerPool pool = new MusicPlayerPool() {
            @Override public MusicPlayer acquire(String guildId, String voiceChannelId) { return musicPlayer; }
            @Override public Optional<MusicPlayer> find(String guildId, String voiceChannelId) { return Optional.empty(); }
            @Override public boolean hasActiveSessionFor(String guildId) { return false; }
        };
        MockCommandInteraction interaction = buildInteraction("play");

        new MusicPlayerCommandController(pool, NO_SPOTIFY).play(interaction, "one more time");
        musicPlayer.fireReady();
        musicPlayer.playHandler.onFailed("Could not reach the music players");

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .doesNotContain("Sending a bear your way");
    }

    @Test
    void usesSelectedAutocompleteUriWithoutYtsearchPrefix() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        String uri = "https://youtube.com/watch?v=dQw4w9WgXcQ";
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("play")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .option("search", uri)
                .build();

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).play(interaction, uri);

        assertThat(musicPlayer.playRequest).isNotNull();
        assertThat(musicPlayer.playRequest.query()).isEqualTo(uri);
    }

    // --- PAUSE ---

    @Test
    void showsPausedMessageAsEphemeralConfirmation() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("pause");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).pause(interaction);
        musicPlayer.firePaused();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Paused the current track for you.");
    }

    @Test
    void showsResumedMessageAsEphemeralConfirmation() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("pause");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).pause(interaction);
        musicPlayer.fireResumed();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Resumed the current track for you.");
    }

    @Test
    void showsNothingPlayingMessageWhenPausingWithoutTrack() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("pause");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).pause(interaction);
        musicPlayer.togglePauseHandler.onNothingPlaying();

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Nothing's playing right now.");
    }

    // --- PREVIOUS ---

    @Test
    void showsPreviousMessageAsEphemeralConfirmation() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("previous");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).previous(interaction);
        musicPlayer.previousHandler.onWentBack();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Back on the trail to the previous track.");
    }

    @Test
    void showsRestartMessageWhenTrackRestarts() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("previous");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).previous(interaction);
        musicPlayer.previousHandler.onRestarted();

        assertThat(interaction.getDeferredMessage().orElseThrow().getEdits())
                .containsExactly(RESTARTED_MESSAGE);
    }

    @Test
    void showsTrailBeginsMessageWhenThereIsNothingToGoBackTo() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("previous");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).previous(interaction);
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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).next(interaction);
        musicPlayer.nextHandler.onSkipped();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("On to the next track.");
    }

    @Test
    void neverWritesSkipTextWhenThereIsNothingToSkipTo() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("next");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).next(interaction);
        musicPlayer.fireNextNothingToAdvance();

        assertThat(interaction.getDeferredMessage().orElseThrow().getEdits())
                .singleElement().asString().contains("This is where the trail ends.");
    }

    @Test
    void showsTrailEndsMessageWhenQueueIsEmpty() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("next");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).next(interaction);
        musicPlayer.fireNextNothingToAdvance();

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("This is where the trail ends. Try adding more using `/player play` command!");
    }

    // --- REWIND ---

    @Test
    void showsAppliedSecondsWhenRewindingByTheDefault() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("rewind");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).rewind(interaction, 0);
        musicPlayer.rewindHandler.onRewound(Duration.ofSeconds(10));

        assertThat(musicPlayer.rewindSeek).isEqualTo(Duration.ZERO);
        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Rewound the current track by 10s for you.");
    }

    @Test
    void showsDurationMessageWhenRewindingByPositiveSeconds() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("rewind");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).rewind(interaction, 30);
        musicPlayer.rewindHandler.onRewound(Duration.ofSeconds(30));

        assertThat(musicPlayer.rewindSeek).isEqualTo(Duration.ofSeconds(30));
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Rewound the current track by 30s for you.");
    }

    @Test
    void rejectsRewindWithNegativeSeconds() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("rewind");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).rewind(interaction, -5);

        assertThat(musicPlayer.rewindSeek).isNull();
        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo(NEGATIVE_REWIND_MESSAGE);
    }

    @Test
    void showsNothingPlayingMessageWhenRewindingWithoutTrack() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("rewind");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).rewind(interaction, 0);
        musicPlayer.rewindHandler.onNothingPlaying();

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Nothing's playing right now.");
    }

    // --- FORWARD ---

    @Test
    void showsAppliedSecondsWhenForwardingByTheDefault() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("forward");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).forward(interaction, 0);
        musicPlayer.forwardHandler.onForwarded(Duration.ofSeconds(30));

        assertThat(musicPlayer.forwardSeek).isEqualTo(Duration.ZERO);
        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Forwarded the current track by 30s for you.");
    }

    @Test
    void showsDurationMessageWhenForwardingByPositiveSeconds() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("forward");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).forward(interaction, 30);
        musicPlayer.forwardHandler.onForwarded(Duration.ofSeconds(30));

        assertThat(musicPlayer.forwardSeek).isEqualTo(Duration.ofSeconds(30));
        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Forwarded the current track by 30s for you.");
    }

    @Test
    void rejectsForwardWithNegativeSeconds() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("forward");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).forward(interaction, -5);

        assertThat(musicPlayer.forwardSeek).isNull();
        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo(NEGATIVE_FORWARD_MESSAGE);
    }

    @Test
    void showsSkipMessageWhenForwardRunsPastTheEndOfTheTrack() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("forward");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).forward(interaction, 60);
        musicPlayer.forwardHandler.onSkipped();

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("On to the next track.");
    }

    @Test
    void showsTrailEndsMessageWhenForwardRunsOutOfQueue() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("forward");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).forward(interaction, 5);
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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).clear(interaction);
        musicPlayer.clearHandler.onCleared();

        assertThat(musicPlayer.cleared).isTrue();
        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow()).contains("Cleared the playlist. Fresh paws.");
    }

    @Test
    void waitsForTheBearBeforeConfirmingClear() {
        RecordingMusicPlayer musicPlayer = new RecordingMusicPlayer();
        MockCommandInteraction interaction = buildInteraction("clear");

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.of(musicPlayer)), NO_SPOTIFY).clear(interaction);

        assertThat(interaction.getDeferredMessage().orElseThrow().hasEdits()).isFalse();
    }

    // --- LEAVE: VALIDATION ---

    @Test
    void rejectsLeaveWhenUserNotInVoiceChannel() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .guildId(GUILD_ID)
                .build();

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.empty()), NO_SPOTIFY).leave(interaction);

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

        new MusicPlayerCommandController(new RecordingMusicPlayerPool(Optional.empty()), NO_SPOTIFY).leave(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("server");
    }

    private static final TrackMetadata TRACK = new TrackMetadata("One More Time", "Daft Punk", "https://youtu.be/one-more-time", 320_000);

    private static final String QUEUED_TRACK_MESSAGE = "\uD83C\uDFB5 | Queued **One More Time**.";
    private static final String NEGATIVE_REWIND_MESSAGE = "\u23EA | Use a positive number of seconds, bear with me.";
    private static final String NEGATIVE_FORWARD_MESSAGE = "\u23E9 | Use a positive number of seconds, bear with me.";
    private static final String RESTARTED_MESSAGE = "\u23EE\uFE0F | Back to the start of this track.";

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
        private MusicPlayerEventListener rewindHandler;
        private MusicPlayerEventListener clearHandler;
        private MusicPlayerEventListener stopHandler;
        private TrackRequest playRequest;

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
        public void stop(MusicPlayerEventListener handler) {
            stopped = true;
            stopHandler = handler;
        }

        @Override
        public void play(TrackRequest request, MusicPlayerEventListener handler) {
            playRequest = request;
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
        public void rewind(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            rewindSeek = seek;
            rewindHandler = handler;
        }

        @Override
        public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            forwardSeek = seek;
            forwardHandler = handler;
        }

        @Override
        public void clear(String requesterTag, MusicPlayerEventListener handler) {
            cleared = true;
            clearHandler = handler;
        }

        @Override
        public void setVolume(int volume, String requesterTag, MusicPlayerEventListener handler) {
        }

        @Override
        public Optional<MusicPlayerEvent> queue() {
            return Optional.empty();
        }

        @Override
        public void remove(int position, String uriPrefix, String requesterTag, MusicPlayerEventListener handler) {
        }

        @Override
        public void shuffle(String requesterTag, MusicPlayerEventListener handler) {
        }
    }
}

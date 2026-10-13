package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.spotify.FixtureSpotifyPageFetcher;
import com.bearify.controller.music.spotify.SpotifyLinkResolver;
import com.bearify.discord.testing.MockMessageContextInteraction;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.LoopMode;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.model.TrackRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerPlaybackTest {

    private static final String QUEUES = "https://youtu.be/queues";
    private static final String NOT_FOUND = "https://youtu.be/not-found";
    private static final String TIMES_OUT = "https://youtu.be/times-out";
    private static final TrackMetadata TRACK = new TrackMetadata("One More Time", "Daft Punk", QUEUES, 320_000);
    private static final String NONE_PLAYED_MESSAGE = "🛑️ | None of those links could be played.";
    private static final String TRACK_NOT_FOUND_MESSAGE = "🛑️ | I couldn't sniff out that track. Try a different search or link?";
    private static final MusicPlayerPlayback.PlayReply COUNTING_REPLY = (queued, failed) -> queued.size() + " queued, " + failed + " failed";

    private final ScriptedMusicPlayer player = new ScriptedMusicPlayer(Map.of(
            QUEUES, (listener, query) -> listener.onTrackQueued(TRACK, 1),
            NOT_FOUND, (listener, query) -> listener.onTrackNotFound(query),
            TIMES_OUT, (listener, query) -> listener.onTimedOut()));
    private final MusicPlayerPlayback playback = new MusicPlayerPlayback(new SinglePlayerPool(player),
            new SpotifyLinkResolver(new FixtureSpotifyPageFetcher()));

    // happy path

    @Test
    void reportsFailedQueriesNextToQueuedOnes() {
        MockMessageContextInteraction interaction = interaction();

        playback.play(interaction, List.of(QUEUES, NOT_FOUND, QUEUES), COUNTING_REPLY);

        assertThat(player.played).containsExactly(QUEUES, NOT_FOUND, QUEUES);
        assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly("2 queued, 1 failed");
    }

    // edge cases

    @Test
    void repliesWithTheFailureWhenTheOnlyQueryFails() {
        MockMessageContextInteraction interaction = interaction();

        playback.play(interaction, List.of(NOT_FOUND), COUNTING_REPLY);

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit()).contains(TRACK_NOT_FOUND_MESSAGE);
    }

    @Test
    void repliesNoneCouldBePlayedWhenEveryQueryFails() {
        MockMessageContextInteraction interaction = interaction();

        playback.play(interaction, List.of(NOT_FOUND, FixtureSpotifyPageFetcher.PLAYLIST_LINK), COUNTING_REPLY);

        assertThat(player.played).containsExactly(NOT_FOUND);
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit()).contains(NONE_PLAYED_MESSAGE);
    }

    @Test
    void countsSpotifyAlbumOrPlaylistLinkAsFailedLink() {
        MockMessageContextInteraction interaction = interaction();

        playback.play(interaction, List.of(FixtureSpotifyPageFetcher.PLAYLIST_LINK, QUEUES), COUNTING_REPLY);

        assertThat(player.played).containsExactly(QUEUES);
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit()).contains("1 queued, 1 failed");
    }

    @Test
    void stopsQueueingWhenTheBearDoesNotAnswer() {
        MockMessageContextInteraction interaction = interaction();

        playback.play(interaction, List.of(QUEUES, TIMES_OUT, QUEUES), COUNTING_REPLY);

        assertThat(player.played).containsExactly(QUEUES, TIMES_OUT);
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit()).contains("1 queued, 2 failed");
    }

    private static MockMessageContextInteraction interaction() {
        return MockMessageContextInteraction.forMessageCommand("Play with Bearify")
                .voiceChannelId("vc-123")
                .guildId("guild-456")
                .textChannelId("txt-789")
                .build();
    }

    private record SinglePlayerPool(MusicPlayer player) implements MusicPlayerPool {
        @Override public MusicPlayer acquire(String guildId, String voiceChannelId) { return player; }
        @Override public Optional<MusicPlayer> find(String guildId, String voiceChannelId) { return Optional.of(player); }
        @Override public boolean hasActiveSessionFor(String guildId) { return true; }
    }

    /** Answers each play right away, by the query's script. */
    private static final class ScriptedMusicPlayer implements MusicPlayer {
        private final Map<String, BiConsumer<MusicPlayerEventListener, String>> answers;
        private final List<String> played = new ArrayList<>();

        private ScriptedMusicPlayer(Map<String, BiConsumer<MusicPlayerEventListener, String>> answers) {
            this.answers = answers;
        }

        @Override
        public void play(TrackRequest request, MusicPlayerEventListener handler) {
            played.add(request.query());
            answers.get(request.query()).accept(handler, request.query());
        }

        @Override public void join(MusicPlayerEventListener handler) {}
        @Override public void stop(MusicPlayerEventListener handler) {}
        @Override public void togglePause(String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void previous(String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void next(String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void rewind(Duration seek, String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void clear(String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void remove(int position, String uriPrefix, String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void shuffle(String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void setVolume(int volume, String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void jump(Duration position, String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void cycleLoop(String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void setLoop(LoopMode mode, String requesterTag, MusicPlayerEventListener handler) {}
        @Override public Optional<MusicPlayerEvent> queue() { return Optional.empty(); }
    }
}

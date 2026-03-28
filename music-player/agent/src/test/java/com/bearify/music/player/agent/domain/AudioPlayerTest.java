package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class AudioPlayerTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final PlayerProperties PROPS = new PlayerProperties(
            Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(5),
            Duration.ofSeconds(5),
            200,
            new PlayerProperties.Assignment(Duration.ofSeconds(30), Duration.ofSeconds(10)),
            new PlayerProperties.VoiceSession(Duration.ofSeconds(10), Duration.ofMinutes(5)),
            new PlayerProperties.Engine(new PlayerProperties.Engine.Youtube(null)));
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    private InMemoryAudioEngine engine;
    private RecordingEventDispatcher eventDispatcher;
    private AudioPlayer player;

    @BeforeEach
    void setUp() {
        engine = new InMemoryAudioEngine();
        eventDispatcher = new RecordingEventDispatcher();
        player = new AudioPlayer(engine, new NoOpAudioProvider(), eventDispatcher, PROPS, SCHEDULER, PLAYER_ID, GUILD_ID, () -> {});
    }

    @Test
    void playsTrackImmediatelyWhenNothingIsPlaying() {
        InMemoryTrack trackA = track("Song A", 120_000);

        player.play(trackA);

        assertThat(engine.getPlayingTrack()).isSameAs(trackA);
    }

    @Test
    void queuesTrackWhenPlayerIsAlreadyPlaying() {
        InMemoryTrack trackA = track("Song A", 120_000);
        engine.play(trackA);

        InMemoryTrack trackB = track("Song B", 180_000);
        player.play(trackB);

        assertThat(engine.getPlayingTrack()).isSameAs(trackA);
    }

    @Test
    void pausesPlaybackWhenPlaying() {
        engine.setPaused(false);

        player.togglePause(new Request("req-1", "@user"));

        assertThat(engine.isPaused()).isTrue();
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Paused(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));
    }

    @Test
    void resumesPlaybackWhenPaused() {
        engine.setPaused(true);

        player.togglePause(new Request("req-1", "@user"));

        assertThat(engine.isPaused()).isFalse();
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Resumed(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));
    }

    @Test
    void advancesToNextTrackInQueue() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        engine.play(trackA);
        player.play(trackB);

        player.next(new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack()).isSameAs(trackB);
        assertThat(eventDispatcher.getEvents())
                .contains(new MusicPlayerEvent.Skipped(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));
    }

    @Test
    void nextUnpausesWhenAdvancingToDifferentTrack() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        engine.play(trackA);
        player.play(trackB);
        engine.setPaused(true);

        player.next(new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack()).isSameAs(trackB);
        assertThat(engine.isPaused()).isFalse();
    }

    @Test
    void restartsCurrentTrackWhenPastThreshold() {
        InMemoryTrack trackA = track("Song A", 120_000);
        trackA.setPosition(5_000);
        engine.play(trackA);

        player.previous(new Request("req-1", "@user"));

        assertThat(trackA.position()).isEqualTo(0);
    }

    @Test
    void goesToPreviousTrackOnSecondPreviousAfterRestart() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        engine.play(trackA);
        player.play(trackB);
        player.next(new Request("req-1", "@alice"));

        trackB.setPosition(5_000);

        player.previous(new Request("req-1", "@user"));
        assertThat(trackB.position()).isEqualTo(0);

        player.previous(new Request("req-2", "@user"));

        assertThat(engine.getPlayingTrack().title()).isEqualTo("Song A");
        assertThat(eventDispatcher.getEvents())
                .contains(new MusicPlayerEvent.WentBack(PLAYER_ID, new Request("req-2", "@user"), GUILD_ID));
    }

    @Test
    void goesToPreviousTrackWhenNearStart() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");

        engine.play(trackA);
        player.play(trackB);
        player.next(new Request("req-1", "@alice"));

        trackB.setPosition(1_000);

        player.previous(new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().title()).isEqualTo("Song A");
        assertThat(eventDispatcher.getEvents())
                .contains(new MusicPlayerEvent.WentBack(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));
    }

    @Test
    void previousUnpausesWhenSwitchingToEarlierTrack() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");

        engine.play(trackA);
        player.play(trackB);
        player.next(new Request("req-1", "@alice"));
        engine.setPaused(true);
        trackB.setPosition(1_000);

        player.previous(new Request("req-2", "@user"));

        assertThat(engine.getPlayingTrack().title()).isEqualTo("Song A");
        assertThat(engine.isPaused()).isFalse();
    }

    @Test
    void seeksForwardByShortDefaultOnShortTrack() {
        InMemoryTrack track = track("Short", 180_000);
        track.setPosition(30_000);
        engine.play(track);

        player.forward(Duration.ZERO, new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().position()).isEqualTo(40_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Forwarded(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 10_000));
    }

    @Test
    void seeksForwardByLongDefaultOnLongTrack() {
        InMemoryTrack track = track("Long", 400_000);
        track.setPosition(30_000);
        engine.play(track);

        player.forward(Duration.ZERO, new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().position()).isEqualTo(60_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Forwarded(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 30_000));
    }

    @Test
    void seeksForwardBySpecifiedAmount() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(10_000);
        engine.play(track);

        player.forward(Duration.ofMillis(30_000), new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().position()).isEqualTo(40_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Forwarded(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 30_000));
    }

    @Test
    void forwardPreservesPausedStateWhenSeekingWithinTrack() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(10_000);
        engine.play(track);
        engine.setPaused(true);

        player.forward(Duration.ofMillis(30_000), new Request("req-1", "@user"));

        assertThat(engine.isPaused()).isTrue();
    }

    @Test
    void seeksBackwardByDefaultAmount() {
        InMemoryTrack track = track("Track", 180_000);
        track.setPosition(60_000);
        engine.play(track);

        player.rewind(Duration.ZERO, new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().position()).isEqualTo(50_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Rewound(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 10_000));
    }

    @Test
    void seeksBackwardBySpecifiedAmount() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(60_000);
        engine.play(track);

        player.rewind(Duration.ofMillis(15_000), new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().position()).isEqualTo(45_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Rewound(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 15_000));
    }

    @Test
    void rewindPreservesPausedState() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(60_000);
        engine.play(track);
        engine.setPaused(true);

        player.rewind(Duration.ofMillis(15_000), new Request("req-1", "@user"));

        assertThat(engine.isPaused()).isTrue();
    }

    @Test
    void clearsQueueAndEmitsClearedEvent() {
        engine.play(track("Playing", 120_000, "@alice"));
        player.play(track("Queued", 120_000, "@alice"));

        player.clear(new Request("req-1", "@user"));

        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Cleared(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, List.of()));
    }

    @Test
    void doesNotEmitQueueEmptyWhenNextFailsButCurrentTrackKeepsPlaying() {
        InMemoryTrack playing = track("Playing", 120_000);
        engine.play(playing);

        player.next(new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack()).isSameAs(playing);
        assertThat(eventDispatcher.getEvents()).doesNotContain(new MusicPlayerEvent.QueueEmpty(PLAYER_ID, "req-1", GUILD_ID));
    }

    @Test
    void rejectsPreviousWhenHistoryIsEmptyAndNearStart() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(500);
        engine.play(track);

        player.previous(new Request("req-1", "@user"));

        assertThat(eventDispatcher.getLastEvent()).isInstanceOf(MusicPlayerEvent.NothingToGoBack.class);
    }

    @Test
    void clipsRewindToTrackStart() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(5_000);
        engine.play(track);

        player.rewind(Duration.ofMillis(10_000), new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().position()).isEqualTo(0);
    }

    @Test
    void advancesToNextTrackWhenForwardExceedsDuration() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        engine.play(trackA);
        player.play(trackB);

        trackA.setPosition(115_000);

        player.forward(Duration.ofMillis(10_000), new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack()).isSameAs(trackB);
        assertThat(eventDispatcher.getEvents())
                .contains(new MusicPlayerEvent.Forwarded(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 10_000));
    }

    @Test
    void runnsOnCloseCallbackWhenClosed() {
        boolean[] closed = {false};
        AudioPlayer closeable = new AudioPlayer(engine, new NoOpAudioProvider(), eventDispatcher, PROPS, SCHEDULER, PLAYER_ID, GUILD_ID, () -> closed[0] = true);

        closeable.close();

        assertThat(closed[0]).isTrue();
    }

    @Test
    void dispatchesQueueUpdatedWhenTrackIsAddedToQueue() {
        InMemoryTrack trackA = track("Song A", 120_000);
        InMemoryTrack trackB = track("Song B", 180_000);
        engine.play(trackA);

        player.play(trackB);

        assertThat(eventDispatcher.getEvents())
                .anyMatch(e -> e instanceof MusicPlayerEvent.QueueUpdated updated
                        && updated.upNext().size() == 1
                        && updated.upNext().get(0).title().equals("Song B"));
    }

    @Test
    void trackStartUpNextIsEmptyWhenNothingIsQueued() {
        InMemoryTrack trackA = track("Song A", 120_000);

        player.play(trackA);

        List<MusicPlayerEvent.TrackStart> trackStarts = eventDispatcher.getEvents().stream()
                .filter(e -> e instanceof MusicPlayerEvent.TrackStart)
                .map(e -> (MusicPlayerEvent.TrackStart) e)
                .toList();
        assertThat(trackStarts).hasSize(1);
        assertThat(trackStarts.getFirst().upNext()).isEmpty();
    }

    @Test
    void trackStartUpNextContainsQueuedTracks() {
        InMemoryTrack placeholder = track("Placeholder", 120_000);
        engine.triggerOnTrackStart(placeholder);

        InMemoryTrack trackB = track("Song B", 120_000, "@bob");
        InMemoryTrack trackC = track("Song C", 180_000, "@carol");
        player.play(trackB);
        player.play(trackC);

        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        engine.triggerOnTrackStart(trackA);

        List<MusicPlayerEvent.TrackStart> trackStarts = eventDispatcher.getEvents().stream()
                .filter(e -> e instanceof MusicPlayerEvent.TrackStart)
                .map(e -> (MusicPlayerEvent.TrackStart) e)
                .toList();
        MusicPlayerEvent.TrackStart last = trackStarts.getLast();
        assertThat(last.track().title()).isEqualTo("Song A");
        assertThat(last.upNext()).hasSize(2);
        assertThat(last.upNext().get(0).title()).isEqualTo("Song B");
        assertThat(last.upNext().get(1).title()).isEqualTo("Song C");
    }

    @Test
    void trackStartUpNextIsCappedAtThreeWhenMoreAreQueued() {
        InMemoryTrack placeholder = track("Placeholder", 120_000);
        engine.triggerOnTrackStart(placeholder);

        player.play(track("Song B", 120_000, "@bob"));
        player.play(track("Song C", 120_000, "@carol"));
        player.play(track("Song D", 120_000, "@dave"));
        player.play(track("Song E", 120_000, "@eve"));

        engine.triggerOnTrackStart(track("Song A", 120_000, "@alice"));

        List<MusicPlayerEvent.TrackStart> trackStarts = eventDispatcher.getEvents().stream()
                .filter(e -> e instanceof MusicPlayerEvent.TrackStart)
                .map(e -> (MusicPlayerEvent.TrackStart) e)
                .toList();
        assertThat(trackStarts.getLast().upNext()).hasSize(3);
    }

    @Test
    void playStartsFirstTrackImmediatelyWhenIdle() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        InMemoryTrack trackC = track("Song C", 200_000, "@alice");

        player.play(List.of(trackA, trackB, trackC));

        assertThat(engine.getPlayingTrack()).isSameAs(trackA);
    }

    @Test
    void playQueuesRemainingTracksWhenIdle() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        InMemoryTrack trackC = track("Song C", 200_000, "@alice");

        player.play(List.of(trackA, trackB, trackC));

        player.next(new Request("req-2", "@alice"));
        assertThat(engine.getPlayingTrack()).isSameAs(trackB);
        player.next(new Request("req-3", "@alice"));
        assertThat(engine.getPlayingTrack()).isSameAs(trackC);
    }

    @Test
    void playQueuesAllTracksWhenAlreadyPlaying() {
        InMemoryTrack playing = track("Playing", 120_000, "@alice");
        engine.play(playing);

        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");

        player.play(List.of(trackA, trackB));

        assertThat(engine.getPlayingTrack()).isSameAs(playing);
        player.next(new Request("req-2", "@alice"));
        assertThat(engine.getPlayingTrack()).isSameAs(trackA);
    }

    @Test
    void playPlaylistEmitsSingleQueuedEvent() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        InMemoryTrack trackC = track("Song C", 200_000, "@alice");
        engine.play(track("Playing", 120_000, "@alice"));

        player.play(List.of(trackA, trackB, trackC));

        long playlistQueuedCount = eventDispatcher.getEvents().stream()
                .filter(e -> e instanceof MusicPlayerEvent.QueueUpdated)
                .count();
        assertThat(playlistQueuedCount).isEqualTo(1);

        MusicPlayerEvent.QueueUpdated event = eventDispatcher.getEvents().stream()
                .filter(e -> e instanceof MusicPlayerEvent.QueueUpdated)
                .map(e -> (MusicPlayerEvent.QueueUpdated) e)
                .findFirst().orElseThrow();
        assertThat(event.requestId()).isNotEmpty();
        assertThat(event.upNext()).hasSize(3);
        assertThat(event.upNext().get(0).title()).isEqualTo("Song A");
        assertThat(event.upNext().get(1).title()).isEqualTo("Song B");
        assertThat(event.upNext().get(2).title()).isEqualTo("Song C");
    }

    @Test
    void playWithSingleTrackStartsImmediatelyAndEmitsEmptyUpNext() {
        InMemoryTrack trackA = track("Solo", 120_000, "@alice");

        player.play(List.of(trackA));

        assertThat(engine.getPlayingTrack()).isSameAs(trackA);
        MusicPlayerEvent.QueueUpdated event = eventDispatcher.getEvents().stream()
                .filter(e -> e instanceof MusicPlayerEvent.QueueUpdated)
                .map(e -> (MusicPlayerEvent.QueueUpdated) e)
                .findFirst().orElseThrow();
        assertThat(event.upNext()).isEmpty();
    }

    private static InMemoryTrack track(String title, long duration) {
        return new InMemoryTrack(title, duration, null);
    }

    private static InMemoryTrack track(String title, long duration, String requesterTag) {
        return new InMemoryTrack(title, duration, requesterTag);
    }

    static final class InMemoryTrack implements Track {
        private final String title;
        private final long duration;
        private final String requesterTag;
        private long position;

        InMemoryTrack(String title, long duration, String requesterTag) {
            this.title = title;
            this.duration = duration;
            this.requesterTag = requesterTag;
        }

        @Override public long duration() { return duration; }
        @Override public long position() { return position; }
        @Override public void setPosition(long positionMs) { this.position = positionMs; }
        @Override public Track clone() { InMemoryTrack c = new InMemoryTrack(title, duration, requesterTag); c.position = position; return c; }
        @Override public String title() { return title; }
        @Override public String author() { return "Author"; }
        @Override public String uri() { return "https://example.com/" + title; }
        @Override public String requesterTag() { return requesterTag; }
    }

    static final class InMemoryAudioEngine implements AudioEngine {
        private Track playingTrack;
        private boolean paused;
        private AudioEngineListener listener;

        @Override public Track getPlayingTrack() { return playingTrack; }

        @Override
        public void play(Track track) {
            if (listener != null && playingTrack != null) {
                // Pass false to prevent re-entrancy: if playPlaylist calls engine.play() while
                // tracks are already in the queue, firing onTrackEnd(true) would auto-advance
                // and consume tracks. Tests that need to simulate natural track ending should
                // use triggerOnTrackEnd() instead.
                listener.onTrackEnd(playingTrack, false);
            }
            playingTrack = track;
            if (listener != null) {
                listener.onTrackStart(track);
            }
        }

        /** Simulates a track ending naturally and triggers auto-advance logic. */
        void triggerOnTrackEnd(Track track) {
            if (listener != null) {
                listener.onTrackEnd(track, true);
            }
        }

        /** Sets the playing track and fires onTrackStart without firing onTrackEnd first. */
        void triggerOnTrackStart(Track track) {
            playingTrack = track;
            if (listener != null) {
                listener.onTrackStart(track);
            }
        }

        @Override public boolean isPaused() { return paused; }
        @Override public void setPaused(boolean p) { this.paused = p; }
        @Override public void addListener(AudioEngineListener l) { this.listener = l; }
    }

    static final class NoOpAudioProvider implements AudioProvider {
        @Override public boolean canProvide() { return false; }
        @Override public byte[] provide20MsAudio() { return new byte[0]; }
        @Override public boolean isOpus() { return false; }
    }

    static final class RecordingEventDispatcher implements MusicPlayerEventDispatcher {
        private final List<MusicPlayerEvent> events = new ArrayList<>();

        @Override
        public void dispatch(MusicPlayerEvent event) {
            events.add(event);
        }

        MusicPlayerEvent getLastEvent() {
            return events.isEmpty() ? null : events.getLast();
        }

        List<MusicPlayerEvent> getEvents() {
            return List.copyOf(events);
        }
    }
}

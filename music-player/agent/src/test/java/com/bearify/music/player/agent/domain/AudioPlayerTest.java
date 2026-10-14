package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.LoopMode;
import com.bearify.music.player.bridge.model.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AudioPlayerTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final PlayerProperties PROPS = new PlayerProperties(
            Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(5),
            Duration.ofSeconds(5),
            200,
            100,
            new PlayerProperties.Assignment(Duration.ofSeconds(30), Duration.ofSeconds(10)),
            new PlayerProperties.VoiceSession(Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(10)),
            new PlayerProperties.Engine(new PlayerProperties.Engine.Youtube(null, null, null)));
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    private InMemoryAudioEngine engine;
    private RecordingEventDispatcher eventDispatcher;
    private VoiceChannelStatus status;
    private AudioPlayer player;

    @BeforeEach
    void setUp() {
        engine = new InMemoryAudioEngine();
        eventDispatcher = new RecordingEventDispatcher();
        status = mock(VoiceChannelStatus.class);
        player = new AudioPlayer(engine, new NoOpAudioProvider(), eventDispatcher, PROPS, SCHEDULER, PLAYER_ID, GUILD_ID, status, () -> {});
    }

    @Test
    void playsTrackImmediatelyWhenNothingIsPlaying() {
        InMemoryTrack trackA = track("Song A", 120_000);

        player.play(trackA);

        assertThat(engine.getPlayingTrack()).containsSame(trackA);
    }

    @Test
    void queuesTrackWhenPlayerIsAlreadyPlaying() {
        InMemoryTrack trackA = track("Song A", 120_000);
        engine.play(trackA);

        InMemoryTrack trackB = track("Song B", 180_000);
        player.play(trackB);

        assertThat(engine.getPlayingTrack()).containsSame(trackA);
    }

    @Test
    void pausesPlaybackWhenPlaying() {
        engine.play(track("Song A", 120_000));
        engine.setPaused(false);

        player.togglePause(new Request("req-1", "@user"));

        assertThat(engine.isPaused()).isTrue();
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Paused(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));
    }

    @Test
    void resumesPlaybackWhenPaused() {
        engine.play(track("Song A", 120_000));
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

        assertThat(engine.getPlayingTrack()).containsSame(trackB);
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

        assertThat(engine.getPlayingTrack()).containsSame(trackB);
        assertThat(engine.isPaused()).isFalse();
    }

    @Test
    void restartsTrackAndAnnouncesRestartWhenPastThreshold() {
        InMemoryTrack trackA = track("Song A", 120_000);
        trackA.setPosition(5_000);
        engine.play(trackA);

        player.previous(new Request("req-1", "@user"));

        assertThat(trackA.position()).isEqualTo(0);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Restarted(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));
    }

    @Test
    void answersNothingPlayingWhenTogglingPauseWithoutTrack() {
        player.togglePause(new Request("req-1", "@user"));

        assertThat(engine.isPaused()).isFalse();
        assertThat(eventDispatcher.getEvents())
                .containsExactly(new MusicPlayerEvent.NothingPlaying(PLAYER_ID, "req-1", GUILD_ID));
    }

    @Test
    void answersNothingPlayingWhenRewindingWithoutTrack() {
        player.rewind(Duration.ZERO, new Request("req-1", "@user"));

        assertThat(eventDispatcher.getEvents())
                .containsExactly(new MusicPlayerEvent.NothingPlaying(PLAYER_ID, "req-1", GUILD_ID));
    }

    @Test
    void answersNothingPlayingWhenForwardingWithoutTrack() {
        player.forward(Duration.ZERO, new Request("req-1", "@user"));

        assertThat(eventDispatcher.getEvents())
                .containsExactly(new MusicPlayerEvent.NothingPlaying(PLAYER_ID, "req-1", GUILD_ID));
    }

    @Test
    void answersSkippedOnlyOnceWhenForwardingPausedTrackPastItsEnd() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        engine.play(trackA);
        player.play(trackB);
        engine.setPaused(true);
        trackA.setPosition(115_000);

        player.forward(Duration.ofMillis(10_000), new Request("req-1", "@user"));

        assertThat(eventDispatcher.getEvents())
                .filteredOn(event -> event.requestId().equals("req-1"))
                .containsExactly(new MusicPlayerEvent.Skipped(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));
        assertThat(eventDispatcher.getEvents()).anySatisfy(event -> assertThat(event).isInstanceOf(MusicPlayerEvent.Paused.class));
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

        assertThat(engine.getPlayingTrack().orElseThrow().title()).isEqualTo("Song A");
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

        assertThat(engine.getPlayingTrack().orElseThrow().title()).isEqualTo("Song A");
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

        assertThat(engine.getPlayingTrack().orElseThrow().title()).isEqualTo("Song A");
        assertThat(engine.isPaused()).isFalse();
    }

    @Test
    void seeksForwardByShortDefaultOnShortTrack() {
        InMemoryTrack track = track("Short", 180_000);
        track.setPosition(30_000);
        engine.play(track);

        player.forward(Duration.ZERO, new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().orElseThrow().position()).isEqualTo(40_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Forwarded(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 10_000));
    }

    @Test
    void seeksForwardByLongDefaultOnLongTrack() {
        InMemoryTrack track = track("Long", 400_000);
        track.setPosition(30_000);
        engine.play(track);

        player.forward(Duration.ZERO, new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().orElseThrow().position()).isEqualTo(60_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Forwarded(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 30_000));
    }

    @Test
    void seeksForwardBySpecifiedAmount() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(10_000);
        engine.play(track);

        player.forward(Duration.ofMillis(30_000), new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().orElseThrow().position()).isEqualTo(40_000);
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

        assertThat(engine.getPlayingTrack().orElseThrow().position()).isEqualTo(50_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Rewound(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 10_000));
    }

    @Test
    void seeksBackwardBySpecifiedAmount() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(60_000);
        engine.play(track);

        player.rewind(Duration.ofMillis(15_000), new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack().orElseThrow().position()).isEqualTo(45_000);
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
    void reportsLiveStreamThatEndsBeforeAnySoundAsTrackError() {
        InMemoryTrack stream = track("Stream", Long.MAX_VALUE);
        engine.play(stream);

        engine.triggerOnTrackEnd(stream);

        assertThat(eventDispatcher.getEvents()).filteredOn(MusicPlayerEvent.TrackError.class::isInstance)
                .singleElement()
                .satisfies(event -> assertThat(((MusicPlayerEvent.TrackError) event).track().title()).isEqualTo("Stream"));
        assertThat(eventDispatcher.getEvents()).hasAtLeastOneElementOfType(MusicPlayerEvent.QueueEmpty.class);
    }

    @Test
    void endsLiveStreamThatPlayedAWhileWithoutError() {
        InMemoryTrack stream = track("Stream", Long.MAX_VALUE);
        engine.play(stream);
        stream.setPosition(60_000);

        engine.triggerOnTrackEnd(stream);

        assertThat(eventDispatcher.getEvents()).noneMatch(MusicPlayerEvent.TrackError.class::isInstance);
    }

    @Test
    void rejectsRewindOnLiveStream() {
        InMemoryTrack stream = track("Stream", Long.MAX_VALUE);
        stream.setPosition(60_000);
        engine.play(stream);

        player.rewind(Duration.ofMillis(15_000), new Request("req-1", "@user"));

        assertThat(stream.position()).isEqualTo(60_000);
        assertThat(eventDispatcher.getLastEvent()).isEqualTo(new MusicPlayerEvent.NotSeekable(PLAYER_ID, "req-1", GUILD_ID));
    }

    @Test
    void rejectsForwardOnLiveStream() {
        InMemoryTrack stream = track("Stream", Long.MAX_VALUE);
        stream.setPosition(60_000);
        engine.play(stream);
        player.play(track("Queued", 120_000));

        player.forward(Duration.ofMillis(15_000), new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack()).containsSame(stream);
        assertThat(stream.position()).isEqualTo(60_000);
        assertThat(eventDispatcher.getLastEvent()).isEqualTo(new MusicPlayerEvent.NotSeekable(PLAYER_ID, "req-1", GUILD_ID));
    }

    @Test
    void jumpsToRequestedPosition() {
        InMemoryTrack track = track("Track", 225_000);
        track.setPosition(10_000);
        engine.play(track);

        player.jump(Duration.ofSeconds(83), new Request("req-1", "@user"));

        assertThat(track.position()).isEqualTo(83_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Jumped(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 83_000));
    }

    @Test
    void keepsPausedStateWhenJumping() {
        InMemoryTrack track = track("Track", 225_000);
        engine.play(track);
        engine.setPaused(true);

        player.jump(Duration.ofSeconds(83), new Request("req-1", "@user"));

        assertThat(engine.isPaused()).isTrue();
    }

    @Test
    void rejectsJumpPastTheEnd() {
        InMemoryTrack track = track("Track", 225_000);
        track.setPosition(10_000);
        engine.play(track);

        player.jump(Duration.ofMillis(225_000), new Request("req-1", "@user"));

        assertThat(track.position()).isEqualTo(10_000);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.JumpOutOfRange(PLAYER_ID, "req-1", GUILD_ID, 225_000));
    }

    @Test
    void rejectsJumpOnLiveStream() {
        InMemoryTrack stream = track("Stream", Long.MAX_VALUE);
        stream.setPosition(60_000);
        engine.play(stream);

        player.jump(Duration.ofSeconds(83), new Request("req-1", "@user"));

        assertThat(stream.position()).isEqualTo(60_000);
        assertThat(eventDispatcher.getLastEvent()).isEqualTo(new MusicPlayerEvent.NotSeekable(PLAYER_ID, "req-1", GUILD_ID));
    }

    @Test
    void answersNothingPlayingWhenJumpingWithoutTrack() {
        player.jump(Duration.ofSeconds(83), new Request("req-1", "@user"));

        assertThat(eventDispatcher.getEvents())
                .containsExactly(new MusicPlayerEvent.NothingPlaying(PLAYER_ID, "req-1", GUILD_ID));
    }

    @Test
    void goesBackInsteadOfRestartingLiveStream() {
        InMemoryTrack earlier = track("Earlier", 120_000);
        engine.play(earlier);
        InMemoryTrack stream = track("Stream", Long.MAX_VALUE);
        player.play(stream);
        player.next(new Request("req-1", "@user"));
        stream.setPosition(60_000);

        player.previous(new Request("req-2", "@user"));

        assertThat(engine.getPlayingTrack()).map(Track::title).contains("Earlier");
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.WentBack(PLAYER_ID, new Request("req-2", "@user"), GUILD_ID));
    }

    @Test
    void answersNothingToGoBackOnLiveStreamWithoutHistory() {
        InMemoryTrack stream = track("Stream", Long.MAX_VALUE);
        stream.setPosition(60_000);
        engine.play(stream);

        player.previous(new Request("req-1", "@user"));

        assertThat(stream.position()).isEqualTo(60_000);
        assertThat(eventDispatcher.getLastEvent()).isEqualTo(new MusicPlayerEvent.NothingToGoBack(PLAYER_ID, "req-1", GUILD_ID));
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
    void removesTrackAtPositionWhenUriMatches() {
        engine.play(track("Playing", 120_000));
        InMemoryTrack one = track("One", 60_000);
        InMemoryTrack two = track("Two", 70_000);
        InMemoryTrack three = track("Three", 80_000);
        player.play(List.of(one, two, three));

        player.remove(2, two.uri(), new Request("req-1", "@user"));

        assertThat(eventDispatcher.getLastEvent()).isEqualTo(new MusicPlayerEvent.Removed(
                PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, two.toMetadata(),
                List.of(one.toMetadata(), three.toMetadata()), 2, 140_000L));
    }

    @Test
    void removesShiftedTrackByUriWhenPositionMoved() {
        engine.play(track("Playing", 120_000));
        InMemoryTrack one = track("One", 60_000);
        InMemoryTrack two = track("Two", 60_000);
        player.play(List.of(one, two));

        // picked as the 3rd track, but one before it left the queue since
        player.remove(3, two.uri(), new Request("req-1", "@user"));

        assertThat(eventDispatcher.getLastEvent())
                .isInstanceOfSatisfying(MusicPlayerEvent.Removed.class, removed -> {
                    assertThat(removed.track()).isEqualTo(two.toMetadata());
                    assertThat(removed.upNext()).containsExactly(one.toMetadata());
                });
    }

    @Test
    void removesByPositionWhenNoUriGiven() {
        engine.play(track("Playing", 120_000));
        InMemoryTrack one = track("One", 60_000);
        InMemoryTrack two = track("Two", 60_000);
        player.play(List.of(one, two));

        player.remove(2, "", new Request("req-1", "@user"));

        assertThat(eventDispatcher.getLastEvent())
                .isInstanceOfSatisfying(MusicPlayerEvent.Removed.class, removed -> {
                    assertThat(removed.track()).isEqualTo(two.toMetadata());
                    assertThat(removed.upNext()).containsExactly(one.toMetadata());
                });
    }

    @Test
    void answersTrackNotInQueueWhenNothingMatches() {
        InMemoryTrack playing = track("Playing", 120_000);
        engine.play(playing);
        InMemoryTrack one = track("One", 60_000);
        player.play(List.of(one));

        player.remove(1, "https://example.com/Gone", new Request("req-1", "@user"));
        player.snapshot("req-2");

        assertThat(eventDispatcher.getEvents()).endsWith(
                new MusicPlayerEvent.TrackNotInQueue(PLAYER_ID, "req-1", GUILD_ID),
                new MusicPlayerEvent.QueueSnapshot(PLAYER_ID, "req-2", GUILD_ID, Optional.of(playing.toMetadata()), List.of(one.toMetadata())));
    }

    @Test
    void shufflesUpcomingTracksAndKeepsCurrent() {
        InMemoryTrack playing = track("Playing", 120_000);
        engine.play(playing);
        List<Track> queued = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            queued.add(track("Song " + i, 60_000));
        }
        player.play(queued);

        player.shuffle(new Request("req-1", "@user"));
        player.snapshot("req-2");

        List<MusicPlayerEvent> events = eventDispatcher.getEvents();
        assertThat(events.get(events.size() - 2))
                .isInstanceOfSatisfying(MusicPlayerEvent.Shuffled.class, shuffled -> {
                    assertThat(shuffled.count()).isEqualTo(12);
                    assertThat(shuffled.upNext()).hasSize(3);
                });
        assertThat(events.getLast()).isInstanceOfSatisfying(MusicPlayerEvent.QueueSnapshot.class, snapshot -> {
            assertThat(snapshot.current()).contains(playing.toMetadata());
            assertThat(snapshot.upcoming()).containsExactlyInAnyOrderElementsOf(queued.stream().map(Track::toMetadata).toList());
        });
    }

    @Test
    void answersShuffledZeroWhenQueueEmpty() {
        engine.play(track("Playing", 120_000));

        player.shuffle(new Request("req-1", "@user"));

        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Shuffled(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 0, List.of()));
    }

    @Test
    void setsVolumeOnEngineAndAnnouncesIt() {
        player.setVolume(40, new Request("req-1", "@user"));

        assertThat(engine.getVolume()).isEqualTo(40);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.VolumeChanged(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 40));
    }

    @Test
    void clampsVolumeAboveHundred() {
        player.setVolume(150, new Request("req-1", "@user"));

        assertThat(engine.getVolume()).isEqualTo(100);
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.VolumeChanged(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 100));
    }

    @Test
    void cyclesLoopModeOffQueueTrack() {
        player.cycleLoop(new Request("req-1", "@user"));
        player.cycleLoop(new Request("req-2", "@user"));
        player.cycleLoop(new Request("req-3", "@user"));

        assertThat(eventDispatcher.getEvents()).containsExactly(
                new MusicPlayerEvent.LoopModeChanged(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, LoopMode.QUEUE),
                new MusicPlayerEvent.LoopModeChanged(PLAYER_ID, new Request("req-2", "@user"), GUILD_ID, LoopMode.TRACK),
                new MusicPlayerEvent.LoopModeChanged(PLAYER_ID, new Request("req-3", "@user"), GUILD_ID, LoopMode.OFF));
    }

    @Test
    void setsLoopModeDirectly() {
        player.setLoop(LoopMode.TRACK, new Request("req-1", "@user"));

        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.LoopModeChanged(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, LoopMode.TRACK));
        player.cycleLoop(new Request("req-2", "@user"));
        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.LoopModeChanged(PLAYER_ID, new Request("req-2", "@user"), GUILD_ID, LoopMode.OFF));
    }

    @Test
    void replaysSameTrackInTrackMode() {
        engine.play(track("Song A", 120_000));
        player.play(track("Song B", 180_000));
        loop(LoopMode.TRACK);

        finishPlayingTrack();

        assertThat(playingTitle()).isEqualTo("Song A");
        assertThat(upcomingTitles()).containsExactly("Song B");
        player.previous(new Request("req-1", "@user"));
        assertThat(eventDispatcher.getLastEvent()).isInstanceOf(MusicPlayerEvent.NothingToGoBack.class);
    }

    @Test
    void advancesOnNextInTrackModeAndKeepsLooping() {
        engine.play(track("Song A", 120_000));
        player.play(track("Song B", 180_000));
        loop(LoopMode.TRACK);

        player.next(new Request("req-1", "@user"));
        finishPlayingTrack();

        assertThat(playingTitle()).isEqualTo("Song B");
        assertThat(upcomingTitles()).isEmpty();
    }

    @Test
    void wrapsQueueInQueueMode() {
        engine.play(track("Song A", 120_000));
        player.play(track("Song B", 180_000));
        player.play(track("Song C", 200_000));
        loop(LoopMode.QUEUE);

        finishPlayingTrack();
        finishPlayingTrack();
        finishPlayingTrack();

        assertThat(playingTitle()).isEqualTo("Song A");
        assertThat(upcomingTitles()).containsExactly("Song B", "Song C");
    }

    @Test
    void reappendsSkippedTrackInQueueMode() {
        engine.play(track("Song A", 120_000));
        player.play(track("Song B", 180_000));
        player.play(track("Song C", 200_000));
        loop(LoopMode.QUEUE);

        player.next(new Request("req-1", "@user"));

        assertThat(playingTitle()).isEqualTo("Song B");
        assertThat(upcomingTitles()).containsExactly("Song C", "Song A");
    }

    @Test
    void reappendsTrackForwardedPastItsEndInQueueMode() {
        engine.play(track("Song A", 120_000));
        player.play(track("Song B", 180_000));
        loop(LoopMode.QUEUE);

        player.forward(Duration.ofMinutes(5), new Request("req-1", "@user"));

        assertThat(playingTitle()).isEqualTo("Song B");
        assertThat(upcomingTitles()).containsExactly("Song A");
    }

    @Test
    void goesBackWithoutDuplicatingTrackInQueueMode() {
        engine.play(track("Song A", 120_000));
        player.play(track("Song B", 180_000));
        player.play(track("Song C", 200_000));
        loop(LoopMode.QUEUE);

        finishPlayingTrack();
        player.previous(new Request("req-1", "@user"));
        finishPlayingTrack();

        assertThat(playingTitle()).isEqualTo("Song B");
        assertThat(upcomingTitles()).containsExactly("Song C", "Song A");
    }

    @Test
    void repeatsSingleTrackInQueueMode() {
        InMemoryTrack song = track("Song A", 120_000);
        engine.play(song);
        loop(LoopMode.QUEUE);

        finishPlayingTrack();
        player.next(new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack()).get().isNotSameAs(song);
        assertThat(playingTitle()).isEqualTo("Song A");
        assertThat(upcomingTitles()).isEmpty();
        assertThat(eventDispatcher.getEvents()).noneMatch(MusicPlayerEvent.NothingToAdvance.class::isInstance);
    }

    @Test
    void doesNotLoopTrackThatErrored() {
        InMemoryTrack broken = track("Broken", 120_000);
        engine.play(broken);
        player.play(track("Song B", 180_000));
        loop(LoopMode.QUEUE);

        engine.triggerOnTrackError(broken);
        engine.triggerOnTrackEnd(broken);

        assertThat(upcomingTitles()).isEmpty();
    }

    @Test
    void doesNotLoopLiveStreamThatEndedBeforeAnySound() {
        InMemoryTrack stream = track("Stream", Long.MAX_VALUE);
        engine.play(stream);
        loop(LoopMode.TRACK);

        engine.triggerOnTrackEnd(stream);

        assertThat(eventDispatcher.getEvents()).filteredOn(MusicPlayerEvent.TrackStart.class::isInstance).hasSize(1);
        assertThat(eventDispatcher.getEvents()).hasAtLeastOneElementOfType(MusicPlayerEvent.QueueEmpty.class);
    }

    @Test
    void ignoresLoopWhenTrackEndIsReplaced() {
        engine.play(track("Song A", 120_000));
        player.play(track("Song B", 180_000));
        loop(LoopMode.QUEUE);

        // the skip replaces Song A, which must not append it a second time
        player.next(new Request("req-1", "@user"));

        assertThat(upcomingTitles()).containsExactly("Song A");
    }

    @Test
    void includesLoopModeInTrackStart() {
        loop(LoopMode.TRACK);

        player.play(track("Song A", 120_000));

        MusicPlayerEvent.TrackStart start = (MusicPlayerEvent.TrackStart) eventDispatcher.getLastEvent();
        assertThat(start.loopMode()).isEqualTo(LoopMode.TRACK);
    }

    @Test
    void snapshotsCurrentTrackAndFullQueue() {
        InMemoryTrack playing = track("Playing", 120_000);
        engine.play(playing);
        List<InMemoryTrack> queued = List.of(
                track("One", 60_000), track("Two", 60_000), track("Three", 60_000), track("Four", 60_000));
        player.play(List.copyOf(queued));

        player.snapshot("req-1");

        assertThat(eventDispatcher.getLastEvent()).isEqualTo(new MusicPlayerEvent.QueueSnapshot(
                PLAYER_ID, "req-1", GUILD_ID,
                Optional.of(playing.toMetadata()),
                queued.stream().map(Track::toMetadata).toList()));
    }

    @Test
    void doesNotEmitQueueEmptyWhenNextFailsButCurrentTrackKeepsPlaying() {
        InMemoryTrack playing = track("Playing", 120_000);
        engine.play(playing);

        player.next(new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack()).containsSame(playing);
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

        assertThat(engine.getPlayingTrack().orElseThrow().position()).isEqualTo(0);
    }

    @Test
    void answersAsSkipWhenForwardRunsPastTheEndOfTheTrack() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        engine.play(trackA);
        player.play(trackB);

        trackA.setPosition(115_000);

        player.forward(Duration.ofMillis(10_000), new Request("req-1", "@user"));

        assertThat(engine.getPlayingTrack()).containsSame(trackB);
        assertThat(eventDispatcher.getEvents())
                .filteredOn(event -> event.requestId().equals("req-1"))
                .containsExactly(new MusicPlayerEvent.Skipped(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));
    }

    @Test
    void reportsDistanceMovedWhenRewindingPastTheStart() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(5_000);
        engine.play(track);

        player.rewind(Duration.ZERO, new Request("req-1", "@user"));

        assertThat(eventDispatcher.getLastEvent())
                .isEqualTo(new MusicPlayerEvent.Rewound(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 5_000));
    }

    @Test
    void runnsOnCloseCallbackWhenClosed() {
        boolean[] closed = {false};
        AudioPlayer closeable = new AudioPlayer(engine, new NoOpAudioProvider(), eventDispatcher, PROPS, SCHEDULER, PLAYER_ID, GUILD_ID, status, () -> closed[0] = true);

        closeable.close();

        assertThat(closed[0]).isTrue();
    }

    @Test
    void showsStatusOnTrackStart() {
        InMemoryTrack trackA = track("Song A", 120_000);

        player.play(trackA);

        verify(status).show(trackA);
    }

    @Test
    void clearsStatusWhenQueueRunsOut() {
        InMemoryTrack trackA = track("Song A", 120_000);
        engine.play(trackA);

        engine.triggerOnTrackEnd(trackA);

        verify(status).clear();
    }

    @Test
    void keepsStatusWhenPausing() {
        engine.play(track("Song A", 120_000));

        player.togglePause(new Request("req-1", "@user"));
        player.togglePause(new Request("req-2", "@user"));
        player.autoPause();
        player.autoResume();

        verify(status, never()).clear();
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
    void trackStartCountsWholeQueueBeyondUpNext() {
        player.play(List.of(
                track("Playing", 60_000), track("B", 60_000), track("C", 60_000), track("D", 60_000), track("E", 90_000)));

        engine.triggerOnTrackStart(track("Playing", 60_000));

        MusicPlayerEvent.TrackStart start = (MusicPlayerEvent.TrackStart) eventDispatcher.getLastEvent();
        assertThat(start.upNext()).hasSize(3);
        assertThat(start.queueSize()).isEqualTo(4);
        assertThat(start.queueDurationMs()).isEqualTo(270_000);
    }

    @Test
    void includesCurrentVolumeInTrackStart() {
        player.setVolume(40, new Request("req-1", "@user"));

        player.play(track("Song A", 120_000));

        MusicPlayerEvent.TrackStart start = (MusicPlayerEvent.TrackStart) eventDispatcher.getLastEvent();
        assertThat(start.volume()).isEqualTo(40);
    }

    @Test
    void queueUpdatedLeavesLiveStreamsOutOfQueueDuration() {
        engine.play(track("Playing", 60_000));

        player.play(List.of(track("Radio", Long.MAX_VALUE), track("Song", 90_000)));

        MusicPlayerEvent.QueueUpdated updated = (MusicPlayerEvent.QueueUpdated) eventDispatcher.getLastEvent();
        assertThat(updated.queueSize()).isEqualTo(2);
        assertThat(updated.queueDurationMs()).isEqualTo(90_000);
    }

    @Test
    void playStartsFirstTrackImmediatelyWhenIdle() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        InMemoryTrack trackC = track("Song C", 200_000, "@alice");

        player.play(List.of(trackA, trackB, trackC));

        assertThat(engine.getPlayingTrack()).containsSame(trackA);
    }

    @Test
    void playQueuesRemainingTracksWhenIdle() {
        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");
        InMemoryTrack trackC = track("Song C", 200_000, "@alice");

        player.play(List.of(trackA, trackB, trackC));

        player.next(new Request("req-2", "@alice"));
        assertThat(engine.getPlayingTrack()).containsSame(trackB);
        player.next(new Request("req-3", "@alice"));
        assertThat(engine.getPlayingTrack()).containsSame(trackC);
    }

    @Test
    void playQueuesAllTracksWhenAlreadyPlaying() {
        InMemoryTrack playing = track("Playing", 120_000, "@alice");
        engine.play(playing);

        InMemoryTrack trackA = track("Song A", 120_000, "@alice");
        InMemoryTrack trackB = track("Song B", 180_000, "@alice");

        player.play(List.of(trackA, trackB));

        assertThat(engine.getPlayingTrack()).containsSame(playing);
        player.next(new Request("req-2", "@alice"));
        assertThat(engine.getPlayingTrack()).containsSame(trackA);
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

        assertThat(engine.getPlayingTrack()).containsSame(trackA);
        MusicPlayerEvent.QueueUpdated event = eventDispatcher.getEvents().stream()
                .filter(e -> e instanceof MusicPlayerEvent.QueueUpdated)
                .map(e -> (MusicPlayerEvent.QueueUpdated) e)
                .findFirst().orElseThrow();
        assertThat(event.upNext()).isEmpty();
    }

    @Test
    void autoPauseDispatchesAutoPaused() {
        engine.play(track("Song A", 120_000));

        player.autoPause();

        assertThat(engine.isPaused()).isTrue();
        assertThat(eventDispatcher.getLastEvent()).isInstanceOf(MusicPlayerEvent.AutoPaused.class);
    }

    @Test
    void autoPauseIgnoresAlreadyPausedPlayer() {
        engine.play(track("Song A", 120_000));
        engine.setPaused(true);

        player.autoPause();

        assertThat(eventDispatcher.getEvents()).noneMatch(MusicPlayerEvent.AutoPaused.class::isInstance);
    }

    @Test
    void autoPauseIgnoresPlayerWithoutTrack() {
        player.autoPause();

        assertThat(engine.isPaused()).isFalse();
        assertThat(eventDispatcher.getEvents()).isEmpty();
    }

    @Test
    void autoResumeResumesAutoPausedPlayer() {
        engine.play(track("Song A", 120_000));
        player.autoPause();

        player.autoResume();

        assertThat(engine.isPaused()).isFalse();
        assertThat(eventDispatcher.getLastEvent()).isInstanceOf(MusicPlayerEvent.AutoResumed.class);
    }

    @Test
    void autoResumeOnlyResumesAutoPausedPlayer() {
        engine.play(track("Song A", 120_000));
        player.togglePause(new Request("req-1", "@user"));

        player.autoResume();

        assertThat(engine.isPaused()).isTrue();
        assertThat(eventDispatcher.getEvents()).noneMatch(MusicPlayerEvent.AutoResumed.class::isInstance);
    }

    @Test
    void togglePauseClearsAutoPause() {
        engine.play(track("Song A", 120_000));
        player.autoPause();
        player.togglePause(new Request("req-1", "@user"));
        player.togglePause(new Request("req-2", "@user"));

        player.autoResume();

        assertThat(engine.isPaused()).isTrue();
        assertThat(eventDispatcher.getEvents()).noneMatch(MusicPlayerEvent.AutoResumed.class::isInstance);
    }

    @Test
    void keepsAutoPauseWhenForwardingPastTheEnd() {
        engine.play(track("Song A", 120_000));
        player.play(track("Song B", 180_000));
        player.autoPause();

        player.forward(Duration.ofMinutes(5), new Request("req-1", "@user"));

        assertThat(engine.isPaused()).isTrue();
        assertThat(eventDispatcher.getEvents()).filteredOn(MusicPlayerEvent.AutoPaused.class::isInstance).hasSize(2);
        assertThat(eventDispatcher.getEvents()).noneMatch(MusicPlayerEvent.Paused.class::isInstance);
    }

    @Test
    void reportsIdleWhenNothingPlays() {
        assertThat(player.isIdle()).isTrue();
    }

    @Test
    void reportsIdleWhenHumanPaused() {
        engine.play(track("Song A", 120_000));
        player.togglePause(new Request("req-1", "@user"));

        assertThat(player.isIdle()).isTrue();
    }

    @Test
    void doesNotReportIdleWhenAutoPaused() {
        engine.play(track("Song A", 120_000));
        player.autoPause();

        assertThat(player.isIdle()).isFalse();
    }

    @Test
    void doesNotReportIdleWhilePlaying() {
        engine.play(track("Song A", 120_000));

        assertThat(player.isIdle()).isFalse();
    }

    private void loop(LoopMode mode) {
        for (int i = 0; i < mode.ordinal(); i++) {
            player.cycleLoop(new Request("req-loop", "@user"));
        }
    }

    private void finishPlayingTrack() {
        engine.triggerOnTrackEnd(engine.getPlayingTrack().orElseThrow());
    }

    private String playingTitle() {
        return engine.getPlayingTrack().orElseThrow().title();
    }

    private List<String> upcomingTitles() {
        player.snapshot("req-snapshot");
        return ((MusicPlayerEvent.QueueSnapshot) eventDispatcher.getLastEvent()).upcoming().stream()
                .map(track -> track.title())
                .toList();
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
        // like LavaPlayer: a stream reports an unknown length and can't seek
        @Override public boolean isSeekable() { return duration != Long.MAX_VALUE; }
        @Override public Track clone() { InMemoryTrack c = new InMemoryTrack(title, duration, requesterTag); c.position = position; return c; }
        @Override public String title() { return title; }
        @Override public String author() { return "Author"; }
        @Override public String uri() { return "https://example.com/" + title; }
        @Override public String requesterTag() { return requesterTag; }
    }

    static final class InMemoryAudioEngine implements AudioEngine {
        private Track playingTrack;
        private boolean paused;
        private int volume = 100;
        private AudioEngineListener listener;

        @Override public Optional<Track> getPlayingTrack() { return Optional.ofNullable(playingTrack); }
        @Override public int getVolume() { return volume; }
        @Override public void setVolume(int volume) { this.volume = volume; }

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

        /** Simulates LavaPlayer's exception that comes before a failed track's end. */
        void triggerOnTrackError(Track track) {
            if (listener != null) {
                listener.onTrackError(track, "boom");
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

package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioPlayerTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final PlayerProperties PROPS = new PlayerProperties(
            Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(5),
            new PlayerProperties.Assignment(Duration.ofSeconds(30), Duration.ofSeconds(10)));

    private InMemoryAudioEngine engine;
    private RecordingEventDispatcher eventDispatcher;
    private AudioPlayer player;

    @BeforeEach
    void setUp() {
        engine = new InMemoryAudioEngine();
        eventDispatcher = new RecordingEventDispatcher();
        player = new AudioPlayer(engine, new NoOpAudioProvider(), eventDispatcher, PROPS, PLAYER_ID, GUILD_ID, () -> {});
    }

    // --- PLAY ---

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

        assertThat(engine.getPlayingTrack()).isSameAs(trackA); // trackB is queued, not playing
    }

    // --- PAUSE / RESUME ---

    @Test
    void pausesPlaybackWhenPlaying() {
        engine.setPaused(false);

        player.togglePause("req-1");

        assertThat(engine.isPaused()).isTrue();
        assertThat(eventDispatcher.getLastEvent()).isInstanceOf(MusicPlayerEvent.Paused.class);
    }

    @Test
    void resumesPlaybackWhenPaused() {
        engine.setPaused(true);

        player.togglePause("req-1");

        assertThat(engine.isPaused()).isFalse();
        assertThat(eventDispatcher.getLastEvent()).isInstanceOf(MusicPlayerEvent.Resumed.class);
    }

    // --- NEXT ---

    @Test
    void advancesToNextTrackInQueue() {
        InMemoryTrack trackA = track("Song A", 120_000);
        InMemoryTrack trackB = track("Song B", 180_000);
        engine.play(trackA);
        player.play(trackB); // queues trackB

        player.next("req-1");

        assertThat(engine.getPlayingTrack()).isSameAs(trackB);
    }

    // --- PREVIOUS ---

    @Test
    void restartsCurrentTrackWhenPastThreshold() {
        InMemoryTrack trackA = track("Song A", 120_000);
        trackA.setPosition(5_000); // past 3s threshold
        engine.play(trackA);

        player.previous("req-1");

        assertThat(trackA.position()).isEqualTo(0);
    }

    @Test
    void goesToPreviousTrackOnSecondPreviousAfterRestart() {
        InMemoryTrack trackA = track("Song A", 120_000);
        InMemoryTrack trackB = track("Song B", 180_000);
        engine.play(trackA);
        player.play(trackB);
        player.next("req-1"); // trackA → history, trackB now playing

        trackB.setPosition(5_000); // past 3s threshold

        player.previous("req-1"); // first previous: restarts trackB (position → 0)
        assertThat(trackB.position()).isEqualTo(0);

        player.previous("req-2"); // second previous: should go to trackA despite position = 0

        assertThat(engine.getPlayingTrack().title()).isEqualTo("Song A");
    }

    @Test
    void goesToPreviousTrackWhenNearStart() {
        InMemoryTrack trackA = track("Song A", 120_000);
        InMemoryTrack trackB = track("Song B", 180_000);

        engine.play(trackA);
        player.play(trackB);
        player.next("req-1"); // trackA → history, trackB now playing

        trackB.setPosition(1_000);

        player.previous("req-1");

        assertThat(engine.getPlayingTrack().title()).isEqualTo("Song A");
    }

    // --- SEEK ---

    @Test
    void seeksForwardByShortDefaultOnShortTrack() {
        InMemoryTrack track = track("Short", 180_000); // < 5min
        track.setPosition(30_000);
        engine.play(track);

        player.forward(Duration.ZERO, "req-1");

        assertThat(engine.getPlayingTrack().position()).isEqualTo(40_000); // 30s + 10s default
    }

    @Test
    void seeksForwardByLongDefaultOnLongTrack() {
        InMemoryTrack track = track("Long", 400_000); // >= 5min
        track.setPosition(30_000);
        engine.play(track);

        player.forward(Duration.ZERO, "req-1");

        assertThat(engine.getPlayingTrack().position()).isEqualTo(60_000); // 30s + 30s default
    }

    @Test
    void seeksForwardBySpecifiedAmount() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(10_000);
        engine.play(track);

        player.forward(Duration.ofMillis(30_000), "req-1");

        assertThat(engine.getPlayingTrack().position()).isEqualTo(40_000);
    }

    @Test
    void seeksBackwardByDefaultAmount() {
        InMemoryTrack track = track("Track", 180_000); // < 5min
        track.setPosition(60_000);
        engine.play(track);

        player.rewind(Duration.ZERO);

        assertThat(engine.getPlayingTrack().position()).isEqualTo(50_000); // 60s - 10s
    }

    @Test
    void seeksBackwardBySpecifiedAmount() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(60_000);
        engine.play(track);

        player.rewind(Duration.ofMillis(15_000));

        assertThat(engine.getPlayingTrack().position()).isEqualTo(45_000);
    }

    // --- EDGE CASES ---

    @Test
    void rejectsNextWhenQueueIsEmpty() {
        engine.play(track("Playing", 120_000));

        player.next("req-1");

        assertThat(eventDispatcher.getLastEvent()).isInstanceOf(MusicPlayerEvent.QueueEmpty.class);
    }

    @Test
    void rejectsPreviousWhenHistoryIsEmptyAndNearStart() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(500); // under threshold
        engine.play(track);

        player.previous("req-1");

        assertThat(eventDispatcher.getLastEvent()).isInstanceOf(MusicPlayerEvent.NothingToGoBack.class);
    }

    @Test
    void clipsRewindToTrackStart() {
        InMemoryTrack track = track("Track", 120_000);
        track.setPosition(5_000); // 5s, rewind 10s → clips to 0
        engine.play(track);

        player.rewind(Duration.ofMillis(10_000));

        assertThat(engine.getPlayingTrack().position()).isEqualTo(0);
    }

    @Test
    void advancesToNextTrackWhenForwardExceedsDuration() {
        InMemoryTrack trackA = track("Song A", 120_000);
        InMemoryTrack trackB = track("Song B", 180_000);
        engine.play(trackA);
        player.play(trackB); // queues trackB

        trackA.setPosition(115_000); // 5s left, forward 10s

        player.forward(Duration.ofMillis(10_000), "req-1"); // goes past end → advance to next

        assertThat(engine.getPlayingTrack()).isSameAs(trackB);
    }

    @Test
    void runnsOnCloseCallbackWhenClosed() {
        boolean[] closed = {false};
        AudioPlayer closeable = new AudioPlayer(engine, new NoOpAudioProvider(), eventDispatcher, PROPS, PLAYER_ID, GUILD_ID, () -> closed[0] = true);

        closeable.close();

        assertThat(closed[0]).isTrue();
    }

    // ==============================
    // Test doubles
    // ==============================

    private static InMemoryTrack track(String title, long duration) {
        return new InMemoryTrack(title, duration);
    }

    static final class InMemoryTrack implements Track {
        private final String title;
        private final long duration;
        private long position;

        InMemoryTrack(String title, long duration) {
            this.title = title;
            this.duration = duration;
        }

        @Override public long duration() { return duration; }
        @Override public long position() { return position; }
        @Override public void setPosition(long positionMs) { this.position = positionMs; }
        @Override public Track clone() { InMemoryTrack c = new InMemoryTrack(title, duration); c.position = position; return c; }
        @Override public String title() { return title; }
        @Override public String author() { return "Author"; }
        @Override public String uri() { return "https://example.com/" + title; }
    }

    static final class InMemoryAudioEngine implements AudioEngine {
        private Track playingTrack;
        private boolean paused;
        private AudioEngineListener listener;

        @Override public Track getPlayingTrack() { return playingTrack; }
        @Override public void play(Track track) {
            if (listener != null && playingTrack != null) {
                listener.onTrackEnd(playingTrack, true);
            }
            playingTrack = track;
            if (listener != null) listener.onTrackStart(track);
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
        public void dispatch(MusicPlayerEvent event) { events.add(event); }

        MusicPlayerEvent getLastEvent() { return events.isEmpty() ? null : events.getLast(); }
    }
}

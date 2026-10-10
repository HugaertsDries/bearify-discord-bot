package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.music.player.agent.config.HealthProbeProperties;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class HealthProbeTest {

    private static final String PLAYER_ID = "player-1";
    private static final String REQUEST_ID = "req-1";
    private static final String PROBE_URL = "https://www.youtube.com/watch?v=probe";
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RecordingEventDispatcher events = new RecordingEventDispatcher();
    private final FakeEngine engine = new FakeEngine();
    private final StubTrackLoader loader = new StubTrackLoader();
    private final HealthProbe probe = new HealthProbe(
            loader, () -> engine, events, scheduler, new HealthProbeProperties(PROBE_URL, PROBE_TIMEOUT), PLAYER_ID);

    @AfterEach
    void shutdownScheduler() {
        scheduler.shutdownNow();
    }

    // --- HAPPY PATH ---

    @Test
    void reportsOkWhenAudioFrameArrives() {
        loader.answer = callback -> callback.trackLoaded(track());
        engine.canProvide = true;

        probe.run(REQUEST_ID);

        awaitResult(true, Optional.empty());
        assertThat(loader.loadedQueries).containsExactly(PROBE_URL);
        assertThat(engine.played).isTrue();
    }

    @Test
    void playsFirstTrackWhenProbeUrlIsAPlaylist() {
        loader.answer = callback -> callback.playlistLoaded(List.of(track(), track()));
        engine.canProvide = true;

        probe.run(REQUEST_ID);

        awaitResult(true, Optional.empty());
    }

    @Test
    void destroysEngineAfterProbe() {
        loader.answer = callback -> callback.trackLoaded(track());
        engine.canProvide = true;

        probe.run(REQUEST_ID);

        await().atMost(2, TimeUnit.SECONDS).until(() -> engine.destroyed);
    }

    // --- FAILURES ---

    @Test
    void reportsFailureWhenNoFrameWithinTimeout() {
        loader.answer = callback -> callback.trackLoaded(track());

        probe.run(REQUEST_ID);

        awaitResult(false, Optional.of("couldn't get any audio within 1s"));
        assertThat(engine.destroyed).isTrue();
    }

    @Test
    void reportsLoadTimeoutWhenProbeTrackLoadsTooSlowly() {
        // the stub loader never answers, like a load stuck on a cipher request

        probe.run(REQUEST_ID);

        awaitResult(false, Optional.of("loading the probe track timed out"));
        assertThat(engine.played).isFalse();
    }

    @Test
    void ignoresProbeTrackThatLoadsAfterTimeout() {
        AtomicReference<AudioTrackLoader.AudioTrackLoadCallback> lateCallback = new AtomicReference<>();
        loader.answer = lateCallback::set;
        engine.canProvide = true;

        probe.run(REQUEST_ID);
        awaitResult(false, Optional.of("loading the probe track timed out"));
        lateCallback.get().trackLoaded(track());

        assertThat(engine.played).isFalse();
        assertThat(events.events).hasSize(1);
    }

    @Test
    void reportsFailureWhenProbeTrackNotFound() {
        loader.answer = AudioTrackLoader.AudioTrackLoadCallback::noMatches;

        probe.run(REQUEST_ID);

        awaitResult(false, Optional.of("probe track not found"));
        assertThat(engine.played).isFalse();
    }

    @Test
    void reportsFailureWhenProbePlaylistIsEmpty() {
        loader.answer = callback -> callback.playlistLoaded(List.of());

        probe.run(REQUEST_ID);

        awaitResult(false, Optional.of("probe track not found"));
    }

    @Test
    void reportsLoadFailureReason() {
        loader.answer = callback -> callback.loadFailed("Sign in to confirm you're not a bot");

        probe.run(REQUEST_ID);

        awaitResult(false, Optional.of("Sign in to confirm you're not a bot"));
    }

    @Test
    void reportsPlaybackErrorOnlyOnce() throws InterruptedException {
        loader.answer = callback -> callback.trackLoaded(track());

        probe.run(REQUEST_ID);
        engine.listener.onTrackError(track(), "Failed to decipher signature");

        awaitResult(false, Optional.of("Failed to decipher signature"));
        assertThat(engine.destroyed).isTrue();
        // the timeout still fires later, but a finished probe must not report again
        Thread.sleep(PROBE_TIMEOUT.toMillis() + 300);
        assertThat(events.events).hasSize(1);
    }

    // --- HELPERS ---

    private void awaitResult(boolean ok, Optional<String> reason) {
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events.events)
                .containsExactly(new MusicPlayerEvent.HealthCheckResult(PLAYER_ID, REQUEST_ID, ok, reason)));
    }

    private static Track track() {
        return new AudioPlayerTest.InMemoryTrack("Never Gonna Give You Up", 213_000, "health-check");
    }

    @FunctionalInterface
    private interface LoadAnswer {
        void answer(AudioTrackLoader.AudioTrackLoadCallback callback);
    }

    private static final class StubTrackLoader implements AudioTrackLoader {
        private final List<String> loadedQueries = new CopyOnWriteArrayList<>();
        private LoadAnswer answer = callback -> {};

        @Override
        public void load(String query, String requesterTag, AudioTrackLoadCallback callback) {
            loadedQueries.add(query);
            answer.answer(callback);
        }

        @Override
        public void search(String query, int limit, AudioTrackSearchCallback callback) {
        }
    }

    private static final class FakeEngine implements AudioEngine, AudioProvider {
        private volatile boolean canProvide;
        private volatile boolean played;
        private volatile boolean destroyed;
        private volatile AudioEngineListener listener;

        @Override public void play(Track track) { played = true; }
        @Override public void destroy() { destroyed = true; }
        @Override public Optional<Track> getPlayingTrack() { return Optional.empty(); }
        @Override public boolean isPaused() { return false; }
        @Override public void setPaused(boolean paused) {}
        @Override public void addListener(AudioEngineListener listener) { this.listener = listener; }

        @Override public boolean canProvide() { return canProvide; }
        @Override public byte[] provide20MsAudio() { return new byte[0]; }
        @Override public boolean isOpus() { return true; }
    }

    private static final class RecordingEventDispatcher implements MusicPlayerEventDispatcher {
        private final List<MusicPlayerEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void dispatch(MusicPlayerEvent event) {
            events.add(event);
        }
    }
}

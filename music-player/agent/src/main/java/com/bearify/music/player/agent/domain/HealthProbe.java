package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.music.player.agent.config.HealthProbeProperties;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Checks that YouTube playback works end to end: loads a known-good track into a throwaway engine,
 * without joining voice, and waits for the first audio frame. Pulling audio is what catches
 * cipher and stream breakage, which a plain search would miss.
 */
public class HealthProbe {

    private static final Logger LOG = LoggerFactory.getLogger(HealthProbe.class);
    private static final String REQUESTER_TAG = "health-check";
    private static final String TRACK_NOT_FOUND = "probe track not found";
    private static final String LOAD_TIMED_OUT = "loading the probe track timed out";
    private static final long POLL_INTERVAL_MS = 100;

    private record ProbeEngine(AudioEngine playback, AudioProvider audio) {}

    private final AudioTrackLoader loader;
    private final Supplier<ProbeEngine> engines;
    private final MusicPlayerEventDispatcher eventDispatcher;
    private final ScheduledExecutorService scheduler;
    private final HealthProbeProperties properties;
    private final String playerId;

    public <E extends AudioEngine & AudioProvider> HealthProbe(AudioTrackLoader loader,
                                                               Supplier<E> engineFactory,
                                                               MusicPlayerEventDispatcher eventDispatcher,
                                                               ScheduledExecutorService scheduler,
                                                               HealthProbeProperties properties,
                                                               String playerId) {
        this.loader = loader;
        this.engines = () -> {
            E engine = engineFactory.get();
            return new ProbeEngine(engine, engine);
        };
        this.eventDispatcher = eventDispatcher;
        this.scheduler = scheduler;
        this.properties = properties;
        this.playerId = playerId;
    }

    public void run(String requestId) {
        Probe probe = new Probe(requestId);
        // the budget covers loading too: a stalled load must still answer before the controller gives up
        scheduler.schedule(probe::timeOut, properties.probeTimeout().toMillis(), TimeUnit.MILLISECONDS);
        loader.load(properties.probeUrl(), REQUESTER_TAG, new AudioTrackLoader.AudioTrackLoadCallback() {
            @Override
            public void trackLoaded(Track track) {
                listen(probe, track);
            }

            @Override
            public void playlistLoaded(List<Track> tracks) {
                if (tracks.isEmpty()) {
                    probe.finish(false, Optional.of(TRACK_NOT_FOUND));
                } else {
                    listen(probe, tracks.getFirst());
                }
            }

            @Override
            public void noMatches() {
                probe.finish(false, Optional.of(TRACK_NOT_FOUND));
            }

            @Override
            public void loadFailed(String message) {
                probe.finish(false, Optional.of(message));
            }
        });
    }

    private void listen(Probe probe, Track track) {
        if (probe.isDone()) {
            return;
        }
        ProbeEngine engine = engines.get();
        engine.playback().addListener(probe);
        if (!probe.start(engine)) {
            // the budget ran out while the engine was being created
            destroy(engine);
            return;
        }
        engine.playback().play(track);
        scheduler.schedule(probe::poll, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private static void destroy(ProbeEngine engine) {
        try {
            engine.playback().destroy();
        } catch (RuntimeException e) {
            LOG.warn("Failed to destroy the health probe engine", e);
        }
    }

    private final class Probe implements AudioEngineListener {

        private final String requestId;
        private boolean done;
        private Optional<ProbeEngine> engine = Optional.empty();

        private Probe(String requestId) {
            this.requestId = requestId;
        }

        private synchronized boolean isDone() {
            return done;
        }

        private synchronized boolean start(ProbeEngine started) {
            if (done) {
                return false;
            }
            engine = Optional.of(started);
            return true;
        }

        // reschedules itself instead of running at a fixed rate, so a finished probe simply stops
        private void poll() {
            if (isDone()) {
                return;
            }
            if (engine.orElseThrow().audio().canProvide()) {
                finish(true, Optional.empty());
            } else {
                scheduler.schedule(this::poll, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            }
        }

        private void timeOut() {
            boolean loaded;
            synchronized (this) {
                loaded = engine.isPresent();
            }
            finish(false, Optional.of(loaded
                    ? "couldn't get any audio within " + properties.probeTimeout().toSeconds() + "s"
                    : LOAD_TIMED_OUT));
        }

        private void finish(boolean ok, Optional<String> reason) {
            Optional<ProbeEngine> toDestroy;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                toDestroy = engine;
            }
            toDestroy.ifPresent(HealthProbe::destroy);
            reason.ifPresent(message -> LOG.warn("YouTube health probe failed: {}", message));
            eventDispatcher.dispatch(new MusicPlayerEvent.HealthCheckResult(playerId, requestId, ok, reason));
        }

        @Override
        public void onTrackError(Track track, String message) {
            finish(false, Optional.ofNullable(message).or(() -> Optional.of("playback failed")));
        }

        @Override public void onTrackStart(Track track) {}
        @Override public void onTrackEnd(Track track, boolean mayStartNext) {}
        @Override public void onTrackStuck(Track track, long thresholdMs) {}
    }
}

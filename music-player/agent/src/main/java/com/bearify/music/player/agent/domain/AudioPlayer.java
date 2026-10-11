package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AudioPlayer implements AudioProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPlayer.class);

    private final AudioEngine engine;
    private final AudioProvider audioProvider;
    private final MusicPlayerEventDispatcher eventDispatcher;
    private final PlayerProperties properties;
    private final ScheduledExecutorService scheduler;
    private final String playerId;
    private final String guildId;
    private final Runnable onClose;

    private final Deque<Track> queue = new ArrayDeque<>();
    private final Deque<Track> history = new ArrayDeque<>();
    private boolean justRestarted = false;
    private boolean closed = false;
    private boolean autoPaused = false;
    private volatile boolean lastTrackWasError = false;

    protected AudioPlayer(AudioEngine engine,
                          AudioProvider audioProvider,
                          MusicPlayerEventDispatcher eventDispatcher,
                          PlayerProperties properties,
                          ScheduledExecutorService scheduler,
                          String playerId,
                          String guildId,
                          Runnable onClose) {
        this.engine = engine;
        this.audioProvider = audioProvider;
        this.eventDispatcher = eventDispatcher;
        this.properties = properties;
        this.scheduler = scheduler;
        this.playerId = playerId;
        this.guildId = guildId;
        this.onClose = onClose;
        engine.addListener(new TrackEventHandler());
    }

    // --- AudioProvider ---

    @Override
    public boolean canProvide() {
        return audioProvider.canProvide();
    }

    @Override
    public byte[] provide20MsAudio() {
        return audioProvider.provide20MsAudio();
    }

    @Override
    public boolean isOpus() {
        return audioProvider.isOpus();
    }

    // --- Playback control ---

    public synchronized void play(Track track) {
        if (engine.getPlayingTrack().isEmpty()) {
            engine.play(track);
        } else {
            queue.addLast(track);
            List<TrackMetadata> upNext = queue.stream().limit(3).map(Track::toMetadata).toList();
            eventDispatcher.dispatch(new MusicPlayerEvent.QueueUpdated(playerId, randomId(), guildId, upNext));
        }
    }

    public synchronized void play(List<Track> tracks) {
        if (tracks.isEmpty()) return;
        for (Track track : tracks) {
            queue.addLast(track);
        }
        if (engine.getPlayingTrack().isEmpty()) {
            engine.play(queue.pollFirst());
        }
        List<TrackMetadata> upNext = queue.stream().limit(3).map(Track::toMetadata).toList();
        eventDispatcher.dispatch(new MusicPlayerEvent.QueueUpdated(playerId, randomId(), guildId, upNext));
    }

    public synchronized void togglePause(Request request) {
        if (engine.getPlayingTrack().isEmpty()) {
            eventDispatcher.dispatch(new MusicPlayerEvent.NothingPlaying(playerId, request.id(), guildId));
            return;
        }
        boolean nowPaused = !engine.isPaused();
        engine.setPaused(nowPaused);
        // a human took over, so a returning listener must not toggle it again
        autoPaused = false;
        if (nowPaused) {
            eventDispatcher.dispatch(new MusicPlayerEvent.Paused(playerId, request, guildId));
        } else {
            eventDispatcher.dispatch(new MusicPlayerEvent.Resumed(playerId, request, guildId));
        }
    }

    public synchronized void next(Request request) {
        if (queue.isEmpty()) {
            eventDispatcher.dispatch(new MusicPlayerEvent.NothingToAdvance(playerId, request.id(), guildId));
            return;
        }
        engine.getPlayingTrack().ifPresent(current -> history.addFirst(current.clone()));
        engine.setPaused(false);
        engine.play(queue.pollFirst());
        eventDispatcher.dispatch(new MusicPlayerEvent.Skipped(playerId, request, guildId));
    }

    public synchronized void previous(Request request) {
        Optional<Track> current = engine.getPlayingTrack();
        // TODO why is this justRestarted needed? Why is the second validation not enough?
        if (!justRestarted && current.isPresent() && current.get().position() > properties.previousRestartThreshold().toMillis()) {
            current.get().setPosition(0);
            justRestarted = true;
            eventDispatcher.dispatch(new MusicPlayerEvent.Restarted(playerId, request, guildId));
            return;
        }
        justRestarted = false;
        if (history.isEmpty()) {
            eventDispatcher.dispatch(new MusicPlayerEvent.NothingToGoBack(playerId, request.id(), guildId));
            return;
        }
        current.ifPresent(track -> queue.addFirst(track.clone()));
        engine.setPaused(false);
        engine.play(history.pollFirst());
        eventDispatcher.dispatch(new MusicPlayerEvent.WentBack(playerId, request, guildId));
    }

    public synchronized void rewind(Duration seek, Request request) {
        Optional<Track> playing = engine.getPlayingTrack();
        if (playing.isEmpty()) {
            eventDispatcher.dispatch(new MusicPlayerEvent.NothingPlaying(playerId, request.id(), guildId));
            return;
        }
        Track track = playing.get();
        long effectiveSeekMs = seek.isZero() ? defaultSeekMs(track) : seek.toMillis();
        long before = track.position();
        long after = Math.max(0, before - effectiveSeekMs);
        track.setPosition(after);
        // the distance actually moved: rewinding past the start stops at 0
        eventDispatcher.dispatch(new MusicPlayerEvent.Rewound(playerId, request, guildId, before - after));
    }

    public synchronized void forward(Duration seek, Request request) {
        Optional<Track> playing = engine.getPlayingTrack();
        if (playing.isEmpty()) {
            eventDispatcher.dispatch(new MusicPlayerEvent.NothingPlaying(playerId, request.id(), guildId));
            return;
        }
        Track track = playing.get();
        long effectiveSeekMs = seek.isZero() ? defaultSeekMs(track) : seek.toMillis();
        long before = track.position();
        long after = before + effectiveSeekMs;
        if (after < track.duration()) {
            track.setPosition(after);
            eventDispatcher.dispatch(new MusicPlayerEvent.Forwarded(playerId, request, guildId, after - before));
            return;
        }
        // forwarding past the end is a skip to the next track
        if (queue.isEmpty()) {
            eventDispatcher.dispatch(new MusicPlayerEvent.NothingToAdvance(playerId, request.id(), guildId));
            return;
        }
        boolean wasPaused = engine.isPaused();
        history.addFirst(track.clone());
        engine.play(queue.pollFirst());
        if (wasPaused) {
            // a fresh id, so this Paused doesn't answer the forward request in place of Skipped
            eventDispatcher.dispatch(autoPaused
                    ? new MusicPlayerEvent.AutoPaused(playerId, randomId(), guildId)
                    : new MusicPlayerEvent.Paused(playerId, new Request(randomId(), request.requesterTag()), guildId));
        }
        eventDispatcher.dispatch(new MusicPlayerEvent.Skipped(playerId, request, guildId));
    }

    public synchronized void clear(Request request) {
        queue.clear();
        eventDispatcher.dispatch(new MusicPlayerEvent.Cleared(playerId, request, guildId, List.of()));
    }

    /** Pauses because nobody is listening; only a playing, unpaused track is paused. */
    public synchronized void autoPause() {
        if (engine.getPlayingTrack().isEmpty() || engine.isPaused()) {
            return;
        }
        engine.setPaused(true);
        autoPaused = true;
        eventDispatcher.dispatch(new MusicPlayerEvent.AutoPaused(playerId, randomId(), guildId));
    }

    /** Resumes only what {@link #autoPause()} paused, never a human pause. */
    public synchronized void autoResume() {
        if (!autoPaused) {
            return;
        }
        autoPaused = false;
        if (!engine.isPaused()) {
            // a skip or previous already resumed playback
            return;
        }
        engine.setPaused(false);
        eventDispatcher.dispatch(new MusicPlayerEvent.AutoResumed(playerId, randomId(), guildId));
    }

    /** Nothing is playing, or a human paused it; an auto-pause is not idle. */
    public synchronized boolean isIdle() {
        return engine.getPlayingTrack().isEmpty() || (engine.isPaused() && !autoPaused);
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        engine.destroy();
        queue.clear();
        history.clear();
        onClose.run();
    }

    // --- Private helpers ---

    private long defaultSeekMs(Track track) {
        return track.duration() >= properties.seekTrackLengthThreshold().toMillis()
                ? properties.seekLongDefault().toMillis()
                : properties.seekShortDefault().toMillis();
    }

    private static String randomId() {
        return UUID.randomUUID().toString();
    }

    private class TrackEventHandler implements AudioEngineListener {

        @Override
        public void onTrackStart(Track track) {
            List<TrackMetadata> upNext;
            synchronized (AudioPlayer.this) {
                justRestarted = false;
                upNext = queue.stream().limit(3).map(Track::toMetadata).toList();
            }
            LOG.info("Now playing: {} by {}", track.title(), track.author());
            eventDispatcher.dispatch(new MusicPlayerEvent.TrackStart(
                    playerId,
                    new Request(randomId(), track.requesterTag()),
                    guildId,
                    track.toMetadata(),
                    upNext));
        }

        @Override
        public void onTrackEnd(Track track, boolean mayStartNext) {
            if (!mayStartNext) return;
            boolean wasError = lastTrackWasError;
            lastTrackWasError = false;
            synchronized (AudioPlayer.this) {
                if (!queue.isEmpty()) {
                    history.addFirst(track.clone());
                    Track next = queue.pollFirst();
                    if (wasError) {
                        long delayMs = properties.errorSkipDelay().toMillis();
                        scheduler.schedule(() -> engine.play(next), delayMs, TimeUnit.MILLISECONDS);
                    } else {
                        engine.play(next);
                    }
                } else {
                    eventDispatcher.dispatch(new MusicPlayerEvent.QueueEmpty(playerId, randomId(), guildId));
                }
            }
        }

        @Override
        public void onTrackError(Track track, String message) {
            LOG.error("Track error for '{}': {}", track.title(), message);
            lastTrackWasError = true;
            eventDispatcher.dispatch(new MusicPlayerEvent.TrackError(playerId, randomId(), guildId, track.toMetadata()));
        }

        @Override
        public void onTrackStuck(Track track, long thresholdMs) {
            LOG.warn("Track stuck for '{}' after {}ms", track.title(), thresholdMs);
        }
    }
}

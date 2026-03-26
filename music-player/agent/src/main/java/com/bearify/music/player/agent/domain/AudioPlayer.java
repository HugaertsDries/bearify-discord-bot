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
        if (engine.getPlayingTrack() == null) {
            engine.play(track);
        } else {
            queue.addLast(track);
            List<TrackMetadata> upNext = queue.stream().limit(3).map(this::toTrackMetadata).toList();
            eventDispatcher.dispatch(new MusicPlayerEvent.QueueUpdated(playerId, randomId(), guildId, upNext));
        }
    }

    public void togglePause(Request request) {
        boolean nowPaused = !engine.isPaused();
        engine.setPaused(nowPaused);
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
        Track current = engine.getPlayingTrack();
        if (current != null) {
            history.addFirst(current.clone());
        }
        engine.setPaused(false);
        engine.play(queue.pollFirst());
        eventDispatcher.dispatch(new MusicPlayerEvent.Skipped(playerId, request, guildId));
    }

    public synchronized void previous(Request request) {
        Track current = engine.getPlayingTrack();
        // TODO why is this justRestarted needed? Why is the second validation not enough?
        if (!justRestarted && current != null && current.position() > properties.previousRestartThreshold().toMillis()) {
            current.setPosition(0);
            justRestarted = true;
            return;
        }
        justRestarted = false;
        if (history.isEmpty()) {
            eventDispatcher.dispatch(new MusicPlayerEvent.NothingToGoBack(playerId, request.id(), guildId));
            return;
        }
        if (current != null) {
            queue.addFirst(current.clone());
        }
        engine.setPaused(false);
        engine.play(history.pollFirst());
        eventDispatcher.dispatch(new MusicPlayerEvent.WentBack(playerId, request, guildId));
    }

    public void rewind(Duration seek, Request request) {
        Track track = engine.getPlayingTrack();
        if (track == null) return;
        long effectiveSeekMs = seek.isZero() ? defaultSeekMs(track) : seek.toMillis();
        long newPosition = Math.max(0, track.position() - effectiveSeekMs);
        track.setPosition(newPosition);
        eventDispatcher.dispatch(new MusicPlayerEvent.Rewound(playerId, request, guildId, effectiveSeekMs));
    }

    public synchronized void forward(Duration seek, Request request) {
        Track track = engine.getPlayingTrack();
        if (track == null) return;
        long effectiveSeekMs = seek.isZero() ? defaultSeekMs(track) : seek.toMillis();
        long newPosition = track.position() + effectiveSeekMs;
        if (newPosition >= track.duration()) {
            if (queue.isEmpty()) {
                eventDispatcher.dispatch(new MusicPlayerEvent.NothingToAdvance(playerId, request.id(), guildId));
                return;
            } else {
                boolean wasPaused = engine.isPaused();
                history.addFirst(track.clone());
                engine.play(queue.pollFirst());
                if (wasPaused) {
                    eventDispatcher.dispatch(new MusicPlayerEvent.Paused(playerId, request, guildId));
                }
            }
        } else {
            track.setPosition(newPosition);
        }
        eventDispatcher.dispatch(new MusicPlayerEvent.Forwarded(playerId, request, guildId, effectiveSeekMs));
    }

    public synchronized void clear(Request request) {
        queue.clear();
        eventDispatcher.dispatch(new MusicPlayerEvent.Cleared(playerId, request, guildId));
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

    private TrackMetadata toTrackMetadata(Track t) {
        return new TrackMetadata(t.title(), t.author(), t.uri(), t.duration());
    }

    private class TrackEventHandler implements AudioEngineListener {

        @Override
        public void onTrackStart(Track track) {
            List<TrackMetadata> upNext;
            synchronized (AudioPlayer.this) {
                justRestarted = false;
                upNext = queue.stream().limit(3).map(AudioPlayer.this::toTrackMetadata).toList();
            }
            LOG.info("Now playing: {} by {}", track.title(), track.author());
            eventDispatcher.dispatch(new MusicPlayerEvent.TrackStart(
                    playerId,
                    new Request(randomId(), track.requesterTag()),
                    guildId,
                    toTrackMetadata(track),
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
            eventDispatcher.dispatch(new MusicPlayerEvent.TrackError(playerId, randomId(), guildId, toTrackMetadata(track)));
        }

        @Override
        public void onTrackStuck(Track track, long thresholdMs) {
            LOG.warn("Track stuck for '{}' after {}ms", track.title(), thresholdMs);
        }
    }
}

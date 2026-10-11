package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.controller.music.discord.DiscordPlaybackAnnouncerFactory;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackRequest;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

class RedisMusicPlayer implements MusicPlayer {

    private static final Logger LOG = LoggerFactory.getLogger(RedisMusicPlayer.class);
    // answers edit Discord messages, which blocks; off the timeout and Redis listener threads a slow edit stalls nothing else
    private static final Executor ANSWERS = Executors.newVirtualThreadPerTaskExecutor();
    private static final String NOT_READY_REASON = "Player is not ready yet";
    private static final String UNREACHABLE_REASON = "Could not reach the music players";

    private sealed interface State extends MusicPlayer {
    }

    private volatile State state;
    private final String guildId;
    private final String voiceChannelId;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MusicPlayerPendingInteractions pendingInteractions;
    private final MusicPlayerAnnouncementRegistry announcementRegistry;
    private final DiscordPlaybackAnnouncerFactory trackAnnouncerFactory;
    private final MusicPlayerPoolProperties properties;
    private final RedisBroadcast broadcast;

    RedisMusicPlayer(Optional<String> playerId,
                     String guildId,
                     String voiceChannelId,
                     StringRedisTemplate redis,
                     ObjectMapper objectMapper,
                     MusicPlayerPendingInteractions pendingInteractions,
                     MusicPlayerAnnouncementRegistry announcementRegistry,
                     DiscordPlaybackAnnouncerFactory trackAnnouncerFactory,
                     MusicPlayerPoolProperties properties) {
        this.state = playerId.<State>map(Connected::new).orElseGet(Pending::new);
        this.guildId = guildId;
        this.voiceChannelId = voiceChannelId;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.pendingInteractions = pendingInteractions;
        this.announcementRegistry = announcementRegistry;
        this.trackAnnouncerFactory = trackAnnouncerFactory;
        this.properties = properties;
        this.broadcast = new RedisBroadcast(redis, objectMapper, pendingInteractions);
    }

    @Override
    public void join(MusicPlayerEventListener handler) { state.join(handler); }

    @Override
    public void stop(MusicPlayerEventListener handler) { state.stop(handler); }

    @Override
    public void play(TrackRequest request, MusicPlayerEventListener handler) { state.play(request, handler); }

    @Override
    public void togglePause(String requesterTag, MusicPlayerEventListener handler) { state.togglePause(requesterTag, handler); }

    @Override
    public void next(String requesterTag, MusicPlayerEventListener handler) { state.next(requesterTag, handler); }

    @Override
    public void previous(String requesterTag, MusicPlayerEventListener handler) { state.previous(requesterTag, handler); }

    @Override
    public void rewind(Duration seek, String requesterTag, MusicPlayerEventListener handler) { state.rewind(seek, requesterTag, handler); }

    @Override
    public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) { state.forward(seek, requesterTag, handler); }

    @Override
    public void clear(String requesterTag, MusicPlayerEventListener handler) { state.clear(requesterTag, handler); }

    @Override
    public Optional<MusicPlayerEvent> queue() { return state.queue(); }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize interaction", e);
        }
    }

    private final class Pending implements State {

        @Override
        public void join(MusicPlayerEventListener handler) {
            MusicPlayerPendingInteractions.PendingInteraction pending = pendingInteractions.register();
            pending.future()
                    .orTimeout(properties.connectRequestTTL().toMillis(), TimeUnit.MILLISECONDS)
                    .whenCompleteAsync((event, ex) -> answerSafely(() -> {
                        if (ex instanceof TimeoutException) {
                            handler.onNoPlayersAvailable();
                        } else if (ex != null) {
                            handler.onFailed(UNREACHABLE_REASON);
                        } else {
                            switch (event) {
                                case MusicPlayerEvent.Ready r -> {
                                    RedisMusicPlayer.this.state = new Connected(r.playerId());
                                    handler.onReady();
                                }
                                case MusicPlayerEvent.ConnectFailed f -> handler.onFailed(f.reason());
                                default -> LOG.warn("Unexpected event '{}' for pending join", event.getClass().getSimpleName());
                            }
                        }
                    }), ANSWERS);
            send(pending, () -> {
                redis.opsForValue().set(
                        PlayerRedisProtocol.Keys.connectRequest(pending.requestId()), "1",
                        properties.connectRequestTTL());
                redis.convertAndSend(
                        PlayerRedisProtocol.Channels.REQUESTS,
                        serialize(new JoinRequest(pending.requestId(), guildId, voiceChannelId)));
            });
        }

        @Override
        public void stop(MusicPlayerEventListener handler) {
            refuse("stop", handler);
        }

        @Override
        public void play(TrackRequest request, MusicPlayerEventListener handler) {
            refuse("play", handler);
        }

        @Override
        public void togglePause(String requesterTag, MusicPlayerEventListener handler) {
            refuse("togglePause", handler);
        }

        @Override
        public void next(String requesterTag, MusicPlayerEventListener handler) {
            refuse("next", handler);
        }

        @Override
        public void previous(String requesterTag, MusicPlayerEventListener handler) {
            refuse("previous", handler);
        }

        @Override
        public void rewind(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            refuse("rewind", handler);
        }

        @Override
        public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            refuse("forward", handler);
        }

        @Override
        public void clear(String requesterTag, MusicPlayerEventListener handler) {
            refuse("clear", handler);
        }

        @Override
        public Optional<MusicPlayerEvent> queue() {
            LOG.warn("queue() called on player in Pending state for guild '{}'", guildId);
            return Optional.empty();
        }

        // only reachable before join answered; answering keeps a deferred caller from hanging
        private void refuse(String action, MusicPlayerEventListener handler) {
            LOG.warn("{}() called on player in Pending state for guild '{}'", action, guildId);
            handler.onFailed(NOT_READY_REASON);
        }
    }

    private final class Connected implements State {

        private final String playerId;

        Connected(String playerId) {
            this.playerId = playerId;
        }

        @Override
        public void join(MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.Connect(playerId, requestId, voiceChannelId, guildId), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.Ready ignored -> handler.onReady();
                    case MusicPlayerEvent.ConnectFailed f -> handler.onFailed(f.reason());
                    default -> LOG.warn("Unexpected event '{}' for connected join", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void stop(MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.Stop(playerId, requestId, guildId), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.Stopped ignored -> handler.onStopped();
                    default -> LOG.warn("Unexpected event '{}' for stop request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void play(TrackRequest request, MusicPlayerEventListener handler) {
            announcementRegistry.subscribe(playerId, guildId, trackAnnouncerFactory.create(request.textChannelId()));
            request(requestId -> new MusicPlayerInteraction.Play(playerId, requestId, guildId, request), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.TrackQueued q -> handler.onTrackQueued(q.track(), q.queuedCount());
                    case MusicPlayerEvent.TrackNotFound t -> handler.onTrackNotFound(t.query());
                    case MusicPlayerEvent.TrackLoadFailed t -> handler.onTrackLoadFailed(t.reason());
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for play request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void togglePause(String requesterTag, MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.TogglePause(playerId, new Request(requestId, requesterTag), guildId), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.Paused ignored -> handler.onPaused();
                    case MusicPlayerEvent.Resumed ignored -> handler.onResumed();
                    case MusicPlayerEvent.NothingPlaying ignored -> handler.onNothingPlaying();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for togglePause request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void next(String requesterTag, MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.Next(playerId, new Request(requestId, requesterTag), guildId), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.Skipped ignored -> handler.onSkipped();
                    case MusicPlayerEvent.NothingToAdvance ignored -> handler.onNothingToAdvance();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for next request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void previous(String requesterTag, MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.Previous(playerId, new Request(requestId, requesterTag), guildId), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.WentBack ignored -> handler.onWentBack();
                    case MusicPlayerEvent.Restarted ignored -> handler.onRestarted();
                    case MusicPlayerEvent.NothingToGoBack ignored -> handler.onNothingToGoBack();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for previous request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void rewind(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.Rewind(playerId, new Request(requestId, requesterTag), guildId, seek.toMillis()), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.Rewound r -> handler.onRewound(Duration.ofMillis(r.seekMs()));
                    case MusicPlayerEvent.NothingPlaying ignored -> handler.onNothingPlaying();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for rewind request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.Forward(playerId, new Request(requestId, requesterTag), guildId, seek.toMillis()), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.Forwarded f -> handler.onForwarded(Duration.ofMillis(f.seekMs()));
                    case MusicPlayerEvent.Skipped ignored -> handler.onSkipped();
                    case MusicPlayerEvent.NothingToAdvance ignored -> handler.onNothingToAdvance();
                    case MusicPlayerEvent.NothingPlaying ignored -> handler.onNothingPlaying();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for forward request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void clear(String requesterTag, MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.Clear(playerId, new Request(requestId, requesterTag), guildId), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.Cleared ignored -> handler.onCleared();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for clear request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public Optional<MusicPlayerEvent> queue() {
            try {
                return broadcast.request(
                        PlayerRedisProtocol.Channels.interactions(playerId),
                        requestId -> new MusicPlayerInteraction.QueueRequest(playerId, requestId, guildId),
                        MusicPlayerEvent.class,
                        properties.queueTimeout());
            } catch (RuntimeException e) {
                LOG.warn("Failed to send a queue request to the music players", e);
                return Optional.empty();
            }
        }

        // a timeout or a failed send also answers handler, so no action can leave the user waiting
        private void request(Function<String, MusicPlayerInteraction> interaction,
                             MusicPlayerEventListener handler,
                             Consumer<MusicPlayerEvent> onReply) {
            MusicPlayerPendingInteractions.PendingInteraction pending = pendingInteractions.register();
            pending.future()
                    .orTimeout(properties.interactionTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .whenCompleteAsync((event, ex) -> answerSafely(() -> {
                        if (ex instanceof TimeoutException) {
                            handler.onTimedOut();
                        } else if (ex != null) {
                            handler.onFailed(UNREACHABLE_REASON);
                        } else {
                            onReply.accept(event);
                        }
                    }), ANSWERS);
            send(pending, () -> redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId), serialize(interaction.apply(pending.requestId()))));
        }
    }

    // a failed send completes the request, which answers it and frees its pending entry
    private static void send(MusicPlayerPendingInteractions.PendingInteraction pending, Runnable sending) {
        try {
            sending.run();
        } catch (RuntimeException e) {
            LOG.warn("Failed to send a request to the music players", e);
            pending.future().completeExceptionally(e);
        }
    }

    // whenComplete swallows what its action throws, so a failing answer (e.g. a Discord edit) would vanish unlogged
    private static void answerSafely(Runnable answer) {
        try {
            answer.run();
        } catch (RuntimeException e) {
            LOG.warn("Failed to answer a music player reply", e);
        }
    }
}

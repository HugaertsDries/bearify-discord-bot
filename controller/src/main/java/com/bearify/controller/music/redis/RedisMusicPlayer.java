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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

class RedisMusicPlayer implements MusicPlayer {

    private static final Logger LOG = LoggerFactory.getLogger(RedisMusicPlayer.class);

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
    }

    @Override
    public void join(MusicPlayerEventListener handler) { state.join(handler); }

    @Override
    public void stop() { state.stop(); }

    @Override
    public void play(TrackRequest request, MusicPlayerEventListener handler) { state.play(request, handler); }

    @Override
    public void togglePause(String requesterTag, MusicPlayerEventListener handler) { state.togglePause(requesterTag, handler); }

    @Override
    public void next(String requesterTag, MusicPlayerEventListener handler) { state.next(requesterTag, handler); }

    @Override
    public void previous(String requesterTag, MusicPlayerEventListener handler) { state.previous(requesterTag, handler); }

    @Override
    public void rewind(Duration seek, String requesterTag) { state.rewind(seek, requesterTag); }

    @Override
    public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) { state.forward(seek, requesterTag, handler); }

    @Override
    public void clear(String requesterTag) { state.clear(requesterTag); }

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
            redis.opsForValue().set(
                    PlayerRedisProtocol.Keys.connectRequest(pending.requestId()), "1",
                    properties.connectRequestTTL());
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.REQUESTS,
                    serialize(new JoinRequest(pending.requestId(), guildId, voiceChannelId)));
            pending.future()
                    .orTimeout(properties.connectRequestTTL().toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((event, ex) -> {
                        if (ex != null) {
                            handler.onNoPlayersAvailable();
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
                    });
        }

        @Override
        public void stop() {
        }

        @Override
        public void play(TrackRequest request, MusicPlayerEventListener handler) {
            LOG.warn("play() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void togglePause(String requesterTag, MusicPlayerEventListener handler) {
            LOG.warn("togglePause() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void next(String requesterTag, MusicPlayerEventListener handler) {
            LOG.warn("next() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void previous(String requesterTag, MusicPlayerEventListener handler) {
            LOG.warn("previous() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void rewind(Duration seek, String requesterTag) {
            LOG.warn("rewind() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            LOG.warn("forward() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void clear(String requesterTag) {
            LOG.warn("clear() called on player in Pending state for guild '{}' — ignoring", guildId);
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
        public void stop() {
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Stop(playerId, UUID.randomUUID().toString(), guildId)));
        }

        @Override
        public void play(TrackRequest request, MusicPlayerEventListener handler) {
            announcementRegistry.subscribe(playerId, guildId, trackAnnouncerFactory.create(request.textChannelId()));
            // no reply on success: the agent only answers play when it fails
            request(requestId -> new MusicPlayerInteraction.Play(playerId, requestId, guildId, request), new MusicPlayerEventListener() {}, event -> {
                switch (event) {
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
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for togglePause request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void next(String requesterTag, MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.Next(playerId, new Request(requestId, requesterTag), guildId), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.Skipped ignored -> {}
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
                    case MusicPlayerEvent.WentBack ignored -> {}
                    case MusicPlayerEvent.NothingToGoBack ignored -> handler.onNothingToGoBack();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for previous request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void rewind(Duration seek, String requesterTag) {
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Rewind(playerId, new Request(UUID.randomUUID().toString(), requesterTag), guildId, seek.toMillis())));
        }

        @Override
        public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            request(requestId -> new MusicPlayerInteraction.Forward(playerId, new Request(requestId, requesterTag), guildId, seek.toMillis()), handler, event -> {
                switch (event) {
                    case MusicPlayerEvent.Forwarded ignored -> {}
                    case MusicPlayerEvent.NothingToAdvance ignored -> handler.onNothingToAdvance();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for forward request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void clear(String requesterTag) {
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Clear(playerId, new Request(UUID.randomUUID().toString(), requesterTag), guildId)));
        }

        // a timeout tells timeoutListener the request failed, so no action can leave the user waiting
        private void request(Function<String, MusicPlayerInteraction> interaction,
                             MusicPlayerEventListener timeoutListener,
                             Consumer<MusicPlayerEvent> onReply) {
            MusicPlayerPendingInteractions.PendingInteraction pending = pendingInteractions.register();
            redis.convertAndSend(PlayerRedisProtocol.Channels.interactions(playerId), serialize(interaction.apply(pending.requestId())));
            pending.future()
                    .orTimeout(properties.interactionTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((event, ex) -> {
                        if (ex != null) {
                            timeoutListener.onFailed("Request timed out");
                        } else {
                            onReply.accept(event);
                        }
                    });
        }
    }
}

package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.controller.music.discord.TextChannelMusicPlayerTrackAnnouncerFactory;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.TrackRequest;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private final TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory;
    private final MusicPlayerPoolProperties properties;

    static PendingBuilder pending() {
        return new PendingBuilder();
    }

    static ConnectedBuilder connected() {
        return new ConnectedBuilder();
    }

    private RedisMusicPlayer(String guildId,
                             String voiceChannelId,
                             StringRedisTemplate redis,
                             ObjectMapper objectMapper,
                             MusicPlayerPendingInteractions pendingInteractions,
                             MusicPlayerAnnouncementRegistry announcementRegistry,
                             TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory,
                             MusicPlayerPoolProperties properties) {
        this.state = new Pending();
        this.guildId = guildId;
        this.voiceChannelId = voiceChannelId;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.pendingInteractions = pendingInteractions;
        this.announcementRegistry = announcementRegistry;
        this.trackAnnouncerFactory = trackAnnouncerFactory;
        this.properties = properties;
    }

    private RedisMusicPlayer(String playerId,
                             String guildId,
                             String voiceChannelId,
                             StringRedisTemplate redis,
                             ObjectMapper objectMapper,
                             MusicPlayerPendingInteractions pendingInteractions,
                             MusicPlayerAnnouncementRegistry announcementRegistry,
                             TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory,
                             MusicPlayerPoolProperties properties) {
        this.state = new Connected(playerId);
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
    public void togglePause(MusicPlayerEventListener handler) { state.togglePause(handler); }

    @Override
    public void next(MusicPlayerEventListener handler) { state.next(handler); }

    @Override
    public void previous(MusicPlayerEventListener handler) { state.previous(handler); }

    @Override
    public void rewind(Duration seek) { state.rewind(seek); }

    @Override
    public void forward(Duration seek, MusicPlayerEventListener handler) { state.forward(seek, handler); }

    @Override
    public void clear() { state.clear(); }

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
        public void togglePause(MusicPlayerEventListener handler) {
            LOG.warn("togglePause() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void next(MusicPlayerEventListener handler) {
            LOG.warn("next() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void previous(MusicPlayerEventListener handler) {
            LOG.warn("previous() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void rewind(Duration seek) {
            LOG.warn("rewind() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void forward(Duration seek, MusicPlayerEventListener handler) {
            LOG.warn("forward() called on player in Pending state for guild '{}' — ignoring", guildId);
        }

        @Override
        public void clear() {
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
            MusicPlayerPendingInteractions.PendingInteraction pending = pendingInteractions.register();
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Connect(playerId, pending.requestId(), voiceChannelId, guildId)));
            pending.future()
                    .orTimeout(properties.interactionTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((event, ex) -> {
                        if (ex != null) {
                            handler.onFailed("Request timed out");
                        } else {
                            switch (event) {
                                case MusicPlayerEvent.Ready r -> handler.onReady();
                                case MusicPlayerEvent.ConnectFailed f -> handler.onFailed(f.reason());
                                default -> LOG.warn("Unexpected event '{}' for connected join", event.getClass().getSimpleName());
                            }
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
            announcementRegistry.subscribe(playerId, trackAnnouncerFactory.create(request.textChannelId()));
            MusicPlayerPendingInteractions.PendingInteraction p = pendingInteractions.register();
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Play(playerId, p.requestId(), guildId, request)));
            p.future().orTimeout(properties.interactionTimeout().toMillis(), TimeUnit.MILLISECONDS).whenComplete((event, ex) -> {
                if (ex != null) return;
                switch (event) {
                    case MusicPlayerEvent.TrackNotFound t -> handler.onTrackNotFound(t.query());
                    case MusicPlayerEvent.TrackLoadFailed t -> handler.onTrackLoadFailed(t.reason());
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for play request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void togglePause(MusicPlayerEventListener handler) {
            MusicPlayerPendingInteractions.PendingInteraction p = pendingInteractions.register();
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.TogglePause(playerId, p.requestId(), guildId)));
            p.future().orTimeout(properties.interactionTimeout().toMillis(), TimeUnit.MILLISECONDS).whenComplete((event, ex) -> {
                if (ex != null) return;
                switch (event) {
                    case MusicPlayerEvent.Paused ignored -> handler.onPaused();
                    case MusicPlayerEvent.Resumed ignored -> handler.onResumed();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for togglePause request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void next(MusicPlayerEventListener handler) {
            MusicPlayerPendingInteractions.PendingInteraction p = pendingInteractions.register();
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Next(playerId, p.requestId(), guildId)));
            p.future().orTimeout(properties.interactionTimeout().toMillis(), TimeUnit.MILLISECONDS).whenComplete((event, ex) -> {
                if (ex != null) return;
                switch (event) {
                    case MusicPlayerEvent.QueueEmpty ignored -> handler.onQueueEmpty();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for next request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void previous(MusicPlayerEventListener handler) {
            MusicPlayerPendingInteractions.PendingInteraction p = pendingInteractions.register();
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Previous(playerId, p.requestId(), guildId)));
            p.future().orTimeout(properties.interactionTimeout().toMillis(), TimeUnit.MILLISECONDS).whenComplete((event, ex) -> {
                if (ex != null) return;
                switch (event) {
                    case MusicPlayerEvent.NothingToGoBack ignored -> handler.onNothingToGoBack();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for previous request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void rewind(Duration seek) {
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Rewind(playerId, UUID.randomUUID().toString(), guildId, seek.toMillis())));
        }

        @Override
        public void forward(Duration seek, MusicPlayerEventListener handler) {
            MusicPlayerPendingInteractions.PendingInteraction p = pendingInteractions.register();
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Forward(playerId, p.requestId(), guildId, seek.toMillis())));
            p.future().orTimeout(properties.interactionTimeout().toMillis(), TimeUnit.MILLISECONDS).whenComplete((event, ex) -> {
                if (ex != null) return;
                switch (event) {
                    case MusicPlayerEvent.QueueEmpty ignored -> handler.onQueueEmpty();
                    case MusicPlayerEvent.PlayerNotFound ignored -> handler.onFailed("No player found in channel");
                    default -> LOG.warn("Unexpected event '{}' for forward request", event.getClass().getSimpleName());
                }
            });
        }

        @Override
        public void clear() {
            redis.convertAndSend(
                    PlayerRedisProtocol.Channels.interactions(playerId),
                    serialize(new MusicPlayerInteraction.Clear(playerId, UUID.randomUUID().toString(), guildId)));
        }
    }

    static final class PendingBuilder {
        private String guildId;
        private String voiceChannelId;
        private StringRedisTemplate redis;
        private ObjectMapper objectMapper;
        private MusicPlayerPendingInteractions pendingInteractions;
        private MusicPlayerAnnouncementRegistry announcementRegistry;
        private TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory;
        private MusicPlayerPoolProperties properties;

        PendingBuilder withGuildId(String guildId) { this.guildId = guildId; return this; }
        PendingBuilder withVoiceChannelId(String voiceChannelId) { this.voiceChannelId = voiceChannelId; return this; }
        PendingBuilder withRedis(StringRedisTemplate redis) { this.redis = redis; return this; }
        PendingBuilder withObjectMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; return this; }
        PendingBuilder withPendingInteractions(MusicPlayerPendingInteractions pendingInteractions) { this.pendingInteractions = pendingInteractions; return this; }
        PendingBuilder withAnnouncementRegistry(MusicPlayerAnnouncementRegistry announcementRegistry) { this.announcementRegistry = announcementRegistry; return this; }
        PendingBuilder withTrackAnnouncerFactory(TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory) { this.trackAnnouncerFactory = trackAnnouncerFactory; return this; }
        PendingBuilder withProperties(MusicPlayerPoolProperties properties) { this.properties = properties; return this; }

        RedisMusicPlayer build() {
            return new RedisMusicPlayer(guildId, voiceChannelId, redis, objectMapper, pendingInteractions, announcementRegistry, trackAnnouncerFactory, properties);
        }
    }

    static final class ConnectedBuilder {
        private String playerId;
        private String guildId;
        private String voiceChannelId;
        private StringRedisTemplate redis;
        private ObjectMapper objectMapper;
        private MusicPlayerPendingInteractions pendingInteractions;
        private MusicPlayerAnnouncementRegistry announcementRegistry;
        private TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory;
        private MusicPlayerPoolProperties properties;

        ConnectedBuilder withPlayerId(String playerId) { this.playerId = playerId; return this; }
        ConnectedBuilder withGuildId(String guildId) { this.guildId = guildId; return this; }
        ConnectedBuilder withVoiceChannelId(String voiceChannelId) { this.voiceChannelId = voiceChannelId; return this; }
        ConnectedBuilder withRedis(StringRedisTemplate redis) { this.redis = redis; return this; }
        ConnectedBuilder withObjectMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; return this; }
        ConnectedBuilder withPendingInteractions(MusicPlayerPendingInteractions pendingInteractions) { this.pendingInteractions = pendingInteractions; return this; }
        ConnectedBuilder withAnnouncementRegistry(MusicPlayerAnnouncementRegistry announcementRegistry) { this.announcementRegistry = announcementRegistry; return this; }
        ConnectedBuilder withTrackAnnouncerFactory(TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory) { this.trackAnnouncerFactory = trackAnnouncerFactory; return this; }
        ConnectedBuilder withProperties(MusicPlayerPoolProperties properties) { this.properties = properties; return this; }

        RedisMusicPlayer build() {
            return new RedisMusicPlayer(playerId, guildId, voiceChannelId, redis, objectMapper, pendingInteractions, announcementRegistry, trackAnnouncerFactory, properties);
        }
    }
}

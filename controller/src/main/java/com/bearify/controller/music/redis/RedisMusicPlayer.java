package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerInteractions;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerTextChannelRegistry;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
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

    private final String playerId;
    private final String guildId;
    private final String voiceChannelId;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MusicPlayerInteractions pendingInteractions;
    private final MusicPlayerTextChannelRegistry textChannelRegistry;

    RedisMusicPlayer(String playerId,
                     String guildId,
                     String voiceChannelId,
                     StringRedisTemplate redis,
                     ObjectMapper objectMapper,
                     MusicPlayerInteractions pendingInteractions,
                     MusicPlayerTextChannelRegistry textChannelRegistry) {
        this.playerId = playerId;
        this.guildId = guildId;
        this.voiceChannelId = voiceChannelId;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.pendingInteractions = pendingInteractions;
        this.textChannelRegistry = textChannelRegistry;
    }

    @Override
    public void join(MusicPlayerEventListener handler) {
        MusicPlayerInteractions.Request pending = pendingInteractions.queue();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.interactions(playerId),
                serialize(new MusicPlayerInteraction.Connect(playerId, pending.requestId(), voiceChannelId, guildId)));
        pending.future().orTimeout(30, TimeUnit.SECONDS).whenComplete((event, ex) -> {
            if (ex != null) {
                handler.onFailed("Request timed out");
            } else {
                switch (event) {
                    case MusicPlayerEvent.Ready r -> handler.onReady();
                    case MusicPlayerEvent.ConnectFailed f -> handler.onFailed(f.reason());
                    default -> LOG.warn("Unexpected event type '{}' for join request", event.getClass().getSimpleName());
                }
            }
        });
    }

    @Override
    public void stop() {
        String requestId = UUID.randomUUID().toString();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.interactions(playerId),
                serialize(new MusicPlayerInteraction.Stop(playerId, requestId, guildId)));
        redis.delete(PlayerRedisProtocol.Keys.assignment(guildId, voiceChannelId));
        textChannelRegistry.remove(guildId);
    }

    @Override
    public void play(String query, String textChannelId, MusicPlayerEventListener handler) {
        textChannelRegistry.store(guildId, textChannelId);
        MusicPlayerInteractions.Request p = pendingInteractions.queue();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.interactions(playerId),
                serialize(new MusicPlayerInteraction.Play(playerId, p.requestId(), textChannelId, query, guildId)));
        p.future().orTimeout(30, TimeUnit.SECONDS).whenComplete((event, ex) -> {
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
        MusicPlayerInteractions.Request p = pendingInteractions.queue();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.interactions(playerId),
                serialize(new MusicPlayerInteraction.TogglePause(playerId, p.requestId(), guildId)));
        p.future().orTimeout(30, TimeUnit.SECONDS).whenComplete((event, ex) -> {
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
        MusicPlayerInteractions.Request p = pendingInteractions.queue();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.interactions(playerId),
                serialize(new MusicPlayerInteraction.Next(playerId, p.requestId(), guildId)));
        p.future().orTimeout(30, TimeUnit.SECONDS).whenComplete((event, ex) -> {
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
        MusicPlayerInteractions.Request p = pendingInteractions.queue();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.interactions(playerId),
                serialize(new MusicPlayerInteraction.Previous(playerId, p.requestId(), guildId)));
        p.future().orTimeout(30, TimeUnit.SECONDS).whenComplete((event, ex) -> {
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
        String requestId = UUID.randomUUID().toString();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.interactions(playerId),
                serialize(new MusicPlayerInteraction.Rewind(playerId, requestId, guildId, seek.toMillis())));
    }

    @Override
    public void forward(Duration seek, MusicPlayerEventListener handler) {
        MusicPlayerInteractions.Request p = pendingInteractions.queue();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.interactions(playerId),
                serialize(new MusicPlayerInteraction.Forward(playerId, p.requestId(), guildId, seek.toMillis())));
        p.future().orTimeout(30, TimeUnit.SECONDS).whenComplete((event, ex) -> {
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
        String requestId = UUID.randomUUID().toString();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.interactions(playerId),
                serialize(new MusicPlayerInteraction.Clear(playerId, requestId, guildId)));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize interaction", e);
        }
    }
}

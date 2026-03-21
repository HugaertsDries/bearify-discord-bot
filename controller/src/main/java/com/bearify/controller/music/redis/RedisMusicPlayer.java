package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerJoinResultHandler;
import com.bearify.controller.music.domain.MusicPlayerPendingRequests;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

class RedisMusicPlayer implements MusicPlayer {

    private static final Logger LOG = LoggerFactory.getLogger(RedisMusicPlayer.class);

    private final String playerId;
    private final String guildId;
    private final String voiceChannelId;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MusicPlayerPendingRequests pendingRequests;

    RedisMusicPlayer(String playerId,
                     String guildId,
                     String voiceChannelId,
                     StringRedisTemplate redis,
                     ObjectMapper objectMapper,
                     MusicPlayerPendingRequests pendingRequests) {
        this.playerId = playerId;
        this.guildId = guildId;
        this.voiceChannelId = voiceChannelId;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.pendingRequests = pendingRequests;
    }

    @Override
    public void join(MusicPlayerJoinResultHandler handler) {
        MusicPlayerPendingRequests.Pending pending = pendingRequests.register();
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
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize interaction", e);
        }
    }
}

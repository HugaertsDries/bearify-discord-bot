package com.bearify.controller.music.domain.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerPendingRequests;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class RedisMusicPlayer implements MusicPlayer {

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
    public CompletableFuture<MusicPlayerEvent> join() {
        MusicPlayerPendingRequests.Pending pending = pendingRequests.register();
        String json = serialize(new MusicPlayerInteraction.Connect(playerId, pending.requestId(), voiceChannelId, guildId));
        redis.convertAndSend(PlayerRedisProtocol.Channels.interactions(playerId), json);
        return pending.future();
    }

    @Override
    public void stop() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(guildId, voiceChannelId));
        String requestId = UUID.randomUUID().toString();
        String json = serialize(new MusicPlayerInteraction.Stop(playerId, requestId, guildId));
        redis.convertAndSend(PlayerRedisProtocol.Channels.interactions(playerId), json);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize interaction", e);
        }
    }
}

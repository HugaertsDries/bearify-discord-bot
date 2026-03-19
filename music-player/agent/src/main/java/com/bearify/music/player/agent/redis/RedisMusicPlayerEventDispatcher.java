package com.bearify.music.player.agent.redis;

import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisMusicPlayerEventDispatcher implements MusicPlayerEventDispatcher {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    RedisMusicPlayerEventDispatcher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void dispatch(MusicPlayerEvent event) {
        try {
            redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MusicPlayerEvent", e);
        }
    }
}

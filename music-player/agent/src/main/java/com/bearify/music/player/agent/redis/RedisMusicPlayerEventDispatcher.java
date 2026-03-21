package com.bearify.music.player.agent.redis;

import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize MusicPlayerEvent", e);
        }
    }
}

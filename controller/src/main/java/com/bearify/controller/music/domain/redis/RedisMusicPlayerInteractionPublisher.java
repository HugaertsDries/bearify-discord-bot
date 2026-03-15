package com.bearify.controller.music.domain.redis;

import com.bearify.shared.events.PlayerInteraction;
import com.bearify.shared.player.PlayerMessageCodec;
import com.bearify.shared.player.PlayerRedisProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RedisMusicPlayerInteractionPublisher implements MusicPlayerInteractionPublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    RedisMusicPlayerInteractionPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void connect(PlayerInteraction.Connect interaction) {
        String json = PlayerMessageCodec.writeCommand(objectMapper, interaction);
        redis.convertAndSend(PlayerRedisProtocol.commandChannel(interaction.playerId()), json);
    }
}

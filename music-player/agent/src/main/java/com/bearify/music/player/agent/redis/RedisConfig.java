package com.bearify.music.player.agent.redis;

import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.agent.port.MusicPlayerInteractionDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

    private final ObjectMapper objectMapper;
    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate stringRedisTemplate;
    private final String playerId;

    public RedisConfig(ObjectMapper objectMapper,
                       RedisConnectionFactory connectionFactory,
                       StringRedisTemplate stringRedisTemplate,
                       @Value("${player.id}") String playerId) {
        this.objectMapper = objectMapper;
        this.connectionFactory = connectionFactory;
        this.stringRedisTemplate = stringRedisTemplate;
        this.playerId = playerId;
    }

    @Bean
    InteractionChannelListener interactionChannelListener(MusicPlayerInteractionDispatcher dispatcher) {
        return new InteractionChannelListener(connectionFactory, objectMapper, dispatcher, playerId);
    }

    @Bean
    JoinRequestChannelListener connectRequestChannelListener(VoiceConnectionManager voiceConnectionManager) {
        return new JoinRequestChannelListener(connectionFactory, objectMapper, voiceConnectionManager);
    }

    @Bean
    RedisMusicPlayerEventDispatcher redisMusicPlayerEventDispatcher() {
        return new RedisMusicPlayerEventDispatcher(stringRedisTemplate, objectMapper);
    }

}

package com.bearify.music.player.agent.domain.redis;

import com.bearify.music.player.agent.domain.InteractionHandler;
import com.bearify.music.player.bridge.protocol.PlayerMessageCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfiguration {

    @Bean
    PlayerMessageCodec codec(ObjectMapper objectMapper) {
        return new PlayerMessageCodec(objectMapper);
    }

    @Bean
    RedisRegistrar registrar(StringRedisTemplate redis, @Value("${player.id}") String playerId) {
        return new RedisRegistrar(redis, playerId);
    }

    @Bean
    RedisInteractionSubscription interactionSubscription(RedisConnectionFactory connectionFactory,
                                                        PlayerMessageCodec codec,
                                                        InteractionHandler interactionHandler,
                                                        @Value("${player.id}") String playerId) {
        return new RedisInteractionSubscription(connectionFactory, codec, interactionHandler, playerId);
    }
}

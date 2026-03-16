package com.bearify.controller.music.domain.redis;

import com.bearify.controller.music.domain.MusicPlayerEventRouter;
import com.bearify.controller.music.domain.MusicPlayerPendingRequests;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.shared.player.PlayerMessageCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    MusicPlayerPool pool(StringRedisTemplate redis,
                         PlayerMessageCodec codec,
                         MusicPlayerPendingRequests pendingRequests) {
        return new RedisMusicPlayerPool(redis, codec, pendingRequests);
    }

    @Bean
    RedisMusicPlayerEventSubscription eventSubscription(RedisConnectionFactory connectionFactory,
                                                        PlayerMessageCodec codec,
                                                        MusicPlayerEventRouter eventRouter) {
        return new RedisMusicPlayerEventSubscription(connectionFactory, codec, eventRouter);
    }
}

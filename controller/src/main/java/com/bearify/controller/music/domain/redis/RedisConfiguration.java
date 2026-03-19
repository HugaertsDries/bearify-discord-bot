package com.bearify.controller.music.domain.redis;

import com.bearify.controller.music.domain.MusicPlayerEventRouter;
import com.bearify.controller.music.domain.MusicPlayerPendingRequests;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfiguration {

    @Bean
    MusicPlayerPool pool(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         MusicPlayerPendingRequests pendingRequests) {
        return new RedisMusicPlayerPool(redis, objectMapper, pendingRequests);
    }

    @Bean
    RedisMusicPlayerEventSubscription eventSubscription(RedisConnectionFactory connectionFactory,
                                                        ObjectMapper objectMapper,
                                                        MusicPlayerEventRouter eventRouter) {
        return new RedisMusicPlayerEventSubscription(connectionFactory, objectMapper, eventRouter);
    }
}

package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerInteractions;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.domain.MusicPlayerTextChannelRegistry;
import com.bearify.controller.music.port.MusicPlayerEventRouter;
import com.bearify.controller.music.port.MusicPlayerTrackAnnouncer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

    @Bean
    MusicPlayerInteractions pendingInteractions() {
        return new MusicPlayerInteractions();
    }

    @Bean
    MusicPlayerTextChannelRegistry textChannelRegistry() {
        return new MusicPlayerTextChannelRegistry();
    }

    @Bean
    MusicPlayerEventRouter eventRouter(MusicPlayerInteractions pendingInteractions,
                                       MusicPlayerTrackAnnouncer trackAnnouncer) {
        return new MusicPlayerEventRouter(pendingInteractions, trackAnnouncer);
    }

    @Bean
    MusicPlayerPool pool(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         MusicPlayerInteractions pendingInteractions,
                         MusicPlayerTextChannelRegistry textChannelRegistry) {
        return new RedisMusicPlayerPool(redis, objectMapper, pendingInteractions, textChannelRegistry);
    }

    @Bean
    RedisMusicPlayerEventSubscription eventSubscription(RedisConnectionFactory connectionFactory,
                                                        ObjectMapper objectMapper,
                                                        MusicPlayerEventRouter eventRouter) {
        return new RedisMusicPlayerEventSubscription(connectionFactory, objectMapper, eventRouter);
    }
}

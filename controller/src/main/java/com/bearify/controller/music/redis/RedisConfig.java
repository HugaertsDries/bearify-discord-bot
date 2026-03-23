package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerQueue;
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
    MusicPlayerQueue pendingInteractions() {
        return new MusicPlayerQueue();
    }

    @Bean
    MusicPlayerTextChannelRegistry textChannelRegistry() {
        return new MusicPlayerTextChannelRegistry();
    }

    @Bean
    MusicPlayerEventRouter eventRouter(MusicPlayerQueue pendingInteractions,
                                       MusicPlayerTrackAnnouncer trackAnnouncer) {
        return new MusicPlayerEventRouter(pendingInteractions, trackAnnouncer);
    }

    @Bean
    MusicPlayerPool pool(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         MusicPlayerQueue pendingInteractions,
                         MusicPlayerTextChannelRegistry textChannelRegistry,
                         MusicPlayerPoolProperties properties) {
        return new RedisMusicPlayerPool(redis, objectMapper, pendingInteractions, textChannelRegistry, properties);
    }

    @Bean
    RedisMusicPlayerEventSubscription eventSubscription(RedisConnectionFactory connectionFactory,
                                                        ObjectMapper objectMapper,
                                                        MusicPlayerEventRouter eventRouter) {
        return new RedisMusicPlayerEventSubscription(connectionFactory, objectMapper, eventRouter);
    }
}

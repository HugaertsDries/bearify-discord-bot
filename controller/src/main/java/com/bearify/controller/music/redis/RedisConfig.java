package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerAnnouncementConsumer;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.MusicPlayerEventDispatcher;
import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.discord.TextChannelMusicPlayerTrackAnnouncerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

    @Bean
    MusicPlayerPendingInteractions pendingInteractions() {
        return new MusicPlayerPendingInteractions();
    }

    @Bean
    MusicPlayerAnnouncementRegistry announcementRegistry() {
        return new MusicPlayerAnnouncementRegistry();
    }

    @Bean
    MusicPlayerAnnouncementConsumer announcementConsumer(MusicPlayerAnnouncementRegistry announcementRegistry) {
        return new MusicPlayerAnnouncementConsumer(announcementRegistry);
    }

    @Bean
    MusicPlayerEventDispatcher eventDispatcher(MusicPlayerPendingInteractions pendingInteractions,
                                               MusicPlayerAnnouncementConsumer announcementConsumer) {
        return new MusicPlayerEventDispatcher(java.util.List.of(pendingInteractions, announcementConsumer));
    }

    @Bean
    MusicPlayerPool pool(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         MusicPlayerPendingInteractions pendingInteractions,
                         MusicPlayerAnnouncementRegistry announcementRegistry,
                         TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory,
                         MusicPlayerPoolProperties properties) {
        return new RedisMusicPlayerPool(redis, objectMapper, pendingInteractions, announcementRegistry, trackAnnouncerFactory, properties);
    }

    @Bean
    RedisMusicPlayerEventSubscription eventSubscription(RedisConnectionFactory connectionFactory,
                                                        ObjectMapper objectMapper,
                                                        MusicPlayerEventDispatcher eventDispatcher) {
        return new RedisMusicPlayerEventSubscription(connectionFactory, objectMapper, eventDispatcher);
    }
}

package com.bearify.controller.music.redis;

import com.bearify.controller.music.discord.HealthProperties;
import com.bearify.controller.music.discord.YoutubeHealthMonitor;
import com.bearify.controller.music.domain.HealthChecker;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementConsumer;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.MusicPlayerEventDispatcher;
import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.domain.TrackCatalogue;
import com.bearify.controller.music.discord.DiscordPlaybackAnnouncerFactory;
import com.bearify.discord.api.gateway.DiscordClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.Executors;

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
                                               MusicPlayerAnnouncementConsumer announcementConsumer,
                                               YoutubeHealthMonitor youtubeHealthMonitor) {
        return new MusicPlayerEventDispatcher(java.util.List.of(pendingInteractions, announcementConsumer, youtubeHealthMonitor));
    }

    @Bean
    HealthChecker healthChecker(StringRedisTemplate redis,
                                ObjectMapper objectMapper,
                                MusicPlayerPendingInteractions pendingInteractions,
                                HealthProperties properties) {
        return new RedisHealthChecker(redis, objectMapper, pendingInteractions, properties.timeout());
    }

    @Bean
    YoutubeHealthMonitor youtubeHealthMonitor(HealthChecker healthChecker,
                                              DiscordClient discord,
                                              HealthProperties properties,
                                              Clock clock) {
        return new YoutubeHealthMonitor(healthChecker, discord, properties, clock, Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    MusicPlayerPool pool(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         MusicPlayerPendingInteractions pendingInteractions,
                         MusicPlayerAnnouncementRegistry announcementRegistry,
                         DiscordPlaybackAnnouncerFactory trackAnnouncerFactory,
                         MusicPlayerPoolProperties properties) {
        return new RedisMusicPlayerPool(redis, objectMapper, pendingInteractions, announcementRegistry, trackAnnouncerFactory, properties);
    }

    @Bean
    TrackCatalogue trackCatalogue(StringRedisTemplate redis,
                                  ObjectMapper objectMapper,
                                  MusicPlayerPendingInteractions pendingInteractions,
                                  MusicPlayerPoolProperties properties) {
        return new RedisTrackCatalogue(redis, objectMapper, pendingInteractions, properties);
    }

    @Bean
    RedisMusicPlayerEventSubscription eventSubscription(RedisConnectionFactory connectionFactory,
                                                        ObjectMapper objectMapper,
                                                        MusicPlayerEventDispatcher eventDispatcher) {
        return new RedisMusicPlayerEventSubscription(connectionFactory, objectMapper, eventDispatcher);
    }
}

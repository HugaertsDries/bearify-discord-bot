package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.HealthChecker;
import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

class RedisHealthChecker implements HealthChecker {

    private final RedisBroadcast broadcast;
    private final Duration timeout;

    RedisHealthChecker(StringRedisTemplate redis,
                       ObjectMapper objectMapper,
                       MusicPlayerPendingInteractions pendingInteractions,
                       Duration timeout) {
        this.broadcast = new RedisBroadcast(redis, objectMapper, pendingInteractions);
        this.timeout = timeout;
    }

    @Override
    public Optional<MusicPlayerEvent.HealthCheckResult> check() {
        // every bear answers; the first result completes the request and later ones are ignored
        return broadcast.request(
                PlayerRedisProtocol.Channels.HEALTH,
                MusicPlayerInteraction.HealthCheck::new,
                MusicPlayerEvent.HealthCheckResult.class,
                timeout);
    }
}

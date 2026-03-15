package com.bearify.controller.music.domain.redis;

import com.bearify.shared.player.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class RedisMusicPlayerAllocator implements MusicPlayerAllocator {

    private final StringRedisTemplate redis;

    RedisMusicPlayerAllocator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> findAssignedTo(String guildId, String voiceChannelId) {
        return Optional.ofNullable(redis.opsForValue().get(PlayerRedisProtocol.assignmentKey(guildId, voiceChannelId)));
    }

    @Override
    public Optional<String> claim() {
        return Optional.ofNullable(redis.opsForSet().pop(PlayerRedisProtocol.AVAILABLE_PLAYERS_KEY));
    }

    @Override
    public boolean assign(String guildId, String voiceChannelId, String playerId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PlayerRedisProtocol.assignmentKey(guildId, voiceChannelId), playerId));
    }

    @Override
    public void release(String playerId) {
        redis.opsForSet().add(PlayerRedisProtocol.AVAILABLE_PLAYERS_KEY, playerId);
    }
}

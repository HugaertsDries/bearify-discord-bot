package com.bearify.controller.music.redis;

import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Whether a bear still claims a voice channel in a guild; its heartbeat keeps that key alive, so a dead bear's expires. */
class RedisPlayerAssignments {

    private final StringRedisTemplate redis;

    RedisPlayerAssignments(StringRedisTemplate redis) {
        this.redis = redis;
    }

    boolean isAssigned(String playerId, String guildId) {
        Set<String> keys = redis.keys(PlayerRedisProtocol.Keys.assignment(guildId));
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        List<String> owners = redis.opsForValue().multiGet(keys);
        return owners != null && owners.contains(playerId);
    }

    /** The Discord user ID the bear logged in with, as it wrote it at startup. */
    Optional<String> botUserId(String playerId) {
        return Optional.ofNullable(redis.opsForValue().get(PlayerRedisProtocol.Keys.bot(playerId)));
    }
}

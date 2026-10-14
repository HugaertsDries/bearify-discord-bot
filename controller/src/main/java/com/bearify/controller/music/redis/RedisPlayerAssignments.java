package com.bearify.controller.music.redis;

import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

/** Whether a bear still claims a voice channel in a guild; its heartbeat keeps that key alive, so a dead bear's expires. */
class RedisPlayerAssignments {

    private final StringRedisTemplate redis;

    RedisPlayerAssignments(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // SCAN, not KEYS: this runs for every session on every liveness tick, and KEYS blocks Redis while it walks the keyspace
    boolean isAssigned(String playerId, String guildId) {
        try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(PlayerRedisProtocol.Keys.assignment(guildId)).build())) {
            return keys.stream().anyMatch(key -> playerId.equals(redis.opsForValue().get(key)));
        }
    }

    /** The Discord user ID the bear logged in with, as it wrote it at startup. */
    Optional<String> botUserId(String playerId) {
        return Optional.ofNullable(redis.opsForValue().get(PlayerRedisProtocol.Keys.bot(playerId)));
    }
}

package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.PlayerMessageStore;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One hash per bear and guild: text channel ID → player message ID. */
class RedisPlayerMessageStore implements PlayerMessageStore {

    // compare-and-delete in one step, so a message remembered between our read and our delete survives
    private static final RedisScript<Long> FORGET_IF_UNCHANGED = RedisScript.of("""
            if redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2] then
                return redis.call('HDEL', KEYS[1], ARGV[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisPlayerMessageStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void remember(String playerId, String guildId, String textChannelId, String messageId) {
        redis.opsForHash().put(PlayerRedisProtocol.Keys.messages(playerId, guildId), textChannelId, messageId);
    }

    @Override
    public void forget(String playerId, String guildId, String textChannelId, String messageId) {
        redis.execute(FORGET_IF_UNCHANGED, List.of(PlayerRedisProtocol.Keys.messages(playerId, guildId)), textChannelId, messageId);
    }

    @Override
    public List<PlayerMessage> all() {
        // shortcut: KEYS scans the whole keyspace, fine for one friend server; switch to SCAN if Redis grows large
        Set<String> keys = redis.keys(PlayerRedisProtocol.Keys.MESSAGES_PREFIX + "*");
        List<PlayerMessage> messages = new ArrayList<>();
        for (String key : keys == null ? Set.<String>of() : keys) {
            // guild IDs never contain ':', so the last one separates them from a player ID that might
            int split = key.lastIndexOf(':');
            String playerId = key.substring(PlayerRedisProtocol.Keys.MESSAGES_PREFIX.length(), split);
            String guildId = key.substring(split + 1);
            for (Map.Entry<Object, Object> entry : redis.opsForHash().entries(key).entrySet()) {
                messages.add(new PlayerMessage(playerId, guildId, (String) entry.getKey(), (String) entry.getValue()));
            }
        }
        return messages;
    }
}

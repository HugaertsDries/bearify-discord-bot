package com.bearify.controller.music.domain.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.domain.MusicPlayerPendingRequests;
import com.bearify.music.player.bridge.protocol.PlayerMessageCodec;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

class RedisMusicPlayerPool implements MusicPlayerPool {

    private final StringRedisTemplate redis;
    private final PlayerMessageCodec codec;
    private final MusicPlayerPendingRequests pendingRequests;

    RedisMusicPlayerPool(StringRedisTemplate redis,
                         PlayerMessageCodec codec,
                         MusicPlayerPendingRequests pendingRequests) {
        this.redis = redis;
        this.codec = codec;
        this.pendingRequests = pendingRequests;
    }

    @Override
    public Optional<MusicPlayer> acquire(String guildId, String voiceChannelId) {
        return findAssignedTo(guildId, voiceChannelId)
                .map(playerId -> player(playerId, guildId, voiceChannelId))
                .or(() -> claim(guildId, voiceChannelId));
    }

    private Optional<MusicPlayer> claim(String guildId, String voiceChannelId) {
        return Optional.ofNullable(redis.opsForSet().randomMember(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS))
                .flatMap(playerId -> {
                    if (assign(guildId, voiceChannelId, playerId)) {
                        return Optional.of(player(playerId, guildId, voiceChannelId));
                    } else {
                        return acquire(guildId, voiceChannelId);
                    }
                });
    }

    private Optional<String> findAssignedTo(String guildId, String voiceChannelId) {
        return Optional.ofNullable(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(guildId, voiceChannelId)));
    }

    private boolean assign(String guildId, String voiceChannelId, String playerId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PlayerRedisProtocol.Keys.assignment(guildId, voiceChannelId), playerId));
    }

    private MusicPlayer player(String playerId, String guildId, String voiceChannelId) {
        return new RedisMusicPlayer(playerId, guildId, voiceChannelId, redis, codec, pendingRequests);
    }
}

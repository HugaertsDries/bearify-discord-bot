package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.domain.MusicPlayerQueue;
import com.bearify.controller.music.domain.MusicPlayerTextChannelRegistry;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

class RedisMusicPlayerPool implements MusicPlayerPool {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MusicPlayerQueue pendingInteractions;
    private final MusicPlayerTextChannelRegistry textChannelRegistry;
    private final MusicPlayerPoolProperties properties;

    RedisMusicPlayerPool(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         MusicPlayerQueue pendingInteractions,
                         MusicPlayerTextChannelRegistry textChannelRegistry,
                         MusicPlayerPoolProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.pendingInteractions = pendingInteractions;
        this.textChannelRegistry = textChannelRegistry;
        this.properties = properties;
    }

    @Override
    public MusicPlayer acquire(String guildId, String voiceChannelId) {
        return findAssignedTo(guildId, voiceChannelId)
                .map(playerId -> connected(playerId, guildId, voiceChannelId))
                .orElseGet(() -> pending(guildId, voiceChannelId));
    }

    @Override
    public Optional<MusicPlayer> find(String guildId, String voiceChannelId) {
        return findAssignedTo(guildId, voiceChannelId)
                .map(playerId -> connected(playerId, guildId, voiceChannelId));
    }

    @Override
    public boolean hasActiveSessionFor(String guildId) {
        var keys = redis.keys(PlayerRedisProtocol.Keys.assignment(guildId));
        return keys != null && !keys.isEmpty();
    }

    private Optional<String> findAssignedTo(String guildId, String voiceChannelId) {
        return Optional.ofNullable(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(guildId, voiceChannelId)));
    }

    private MusicPlayer connected(String playerId, String guildId, String voiceChannelId) {
        return RedisMusicPlayer.connected()
                .withPlayerId(playerId)
                .withGuildId(guildId)
                .withVoiceChannelId(voiceChannelId)
                .withRedis(redis)
                .withObjectMapper(objectMapper)
                .withPendingInteractions(pendingInteractions)
                .withTextChannelRegistry(textChannelRegistry)
                .withProperties(properties)
                .build();
    }

    private MusicPlayer pending(String guildId, String voiceChannelId) {
        return RedisMusicPlayer.pending()
                .withGuildId(guildId)
                .withVoiceChannelId(voiceChannelId)
                .withRedis(redis)
                .withObjectMapper(objectMapper)
                .withPendingInteractions(pendingInteractions)
                .withTextChannelRegistry(textChannelRegistry)
                .withProperties(properties)
                .build();
    }
}

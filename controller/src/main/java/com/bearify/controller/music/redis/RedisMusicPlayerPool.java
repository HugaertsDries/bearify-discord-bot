package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.discord.TextChannelMusicPlayerTrackAnnouncerFactory;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

class RedisMusicPlayerPool implements MusicPlayerPool {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MusicPlayerPendingInteractions pendingInteractions;
    private final MusicPlayerAnnouncementRegistry announcementRegistry;
    private final TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory;
    private final MusicPlayerPoolProperties properties;

    RedisMusicPlayerPool(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         MusicPlayerPendingInteractions pendingInteractions,
                         MusicPlayerAnnouncementRegistry announcementRegistry,
                         TextChannelMusicPlayerTrackAnnouncerFactory trackAnnouncerFactory,
                         MusicPlayerPoolProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.pendingInteractions = pendingInteractions;
        this.announcementRegistry = announcementRegistry;
        this.trackAnnouncerFactory = trackAnnouncerFactory;
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
                .withAnnouncementRegistry(announcementRegistry)
                .withTrackAnnouncerFactory(trackAnnouncerFactory)
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
                .withAnnouncementRegistry(announcementRegistry)
                .withTrackAnnouncerFactory(trackAnnouncerFactory)
                .withProperties(properties)
                .build();
    }
}

package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssignmentHeartbeat {

    private final AudioPlayerPool pool;
    private final DiscordClient client;
    private final StringRedisTemplate redis;
    private final PlayerProperties properties;

    public AssignmentHeartbeat(AudioPlayerPool pool,
                                DiscordClient client,
                                StringRedisTemplate redis,
                                PlayerProperties properties) {
        this.pool = pool;
        this.client = client;
        this.redis = redis;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${music-player.assignment.heartbeat-interval:10s}")
    public void renew() {
        pool.activeGuildIds().forEach(guildId ->
                client.guild(guildId).voice()
                        .map(VoiceSession::getChannelId)
                        .ifPresent(channelId ->
                                redis.expire(
                                        PlayerRedisProtocol.Keys.assignment(guildId, channelId),
                                        properties.assignment().ttl())));
    }
}

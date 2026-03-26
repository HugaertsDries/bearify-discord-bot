package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceSessionHeartbeat {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceSessionHeartbeat.class);

    private final AudioPlayerPool pool;
    private final DiscordClient client;
    private final StringRedisTemplate redis;
    private final PlayerProperties properties;
    private final VoiceConnectionManager manager;
    private final Clock clock;
    private final Map<String, LonelyState> lonelyGuilds = new ConcurrentHashMap<>();

    VoiceSessionHeartbeat(AudioPlayerPool pool,
                          DiscordClient client,
                          StringRedisTemplate redis,
                          PlayerProperties properties,
                          VoiceConnectionManager manager,
                          Clock clock) {
        this.pool = pool;
        this.client = client;
        this.redis = redis;
        this.properties = properties;
        this.manager = manager;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${music-player.voice-session.heartbeat-interval:10s}")
    public void maintain() {
        pool.activeGuildIds().forEach(this::maintain);
    }

    private void maintain(String guildId) {
        try {
            client.guild(guildId).voice()
                    .ifPresentOrElse(
                            session -> maintainConnected(guildId, session),
                            () -> lonelyGuilds.remove(guildId));
        } catch (RuntimeException ex) {
            LOG.warn("Voice-session heartbeat failed for guild {}", guildId, ex);
        }
    }

    private void maintainConnected(String guildId, VoiceSession session) {
        String channelId = session.getChannelId();
        redis.expire(
                PlayerRedisProtocol.Keys.assignment(guildId, channelId),
                properties.assignment().ttl());

        if (!session.isLonely()) {
            lonelyGuilds.remove(guildId);
            return;
        }

        Instant now = clock.instant();
        LonelyState state = lonelyGuilds.compute(guildId, (_, existing) -> {
            if (existing == null || !existing.channelId().equals(channelId)) {
                return new LonelyState(channelId, now);
            }
            return existing;
        });

        if (Duration.between(state.lonelySince(), now).compareTo(properties.voiceSession().lonelyTimeout()) < 0) {
            return;
        }
        client.guild(guildId).voice().ifPresentOrElse(s -> {
            if (!s.getChannelId().equals(state.channelId()) || !s.isLonely()) {
                lonelyGuilds.remove(guildId);
            } else {
                try {
                    manager.disconnect(guildId);
                    lonelyGuilds.remove(guildId);
                } catch (RuntimeException ex) {
                    LOG.warn("Failed to disconnect lonely guild {}", guildId, ex);
                }
            }
        }, () -> lonelyGuilds.remove(guildId));
    }

    private record LonelyState(String channelId, Instant lonelySince) {
    }
}

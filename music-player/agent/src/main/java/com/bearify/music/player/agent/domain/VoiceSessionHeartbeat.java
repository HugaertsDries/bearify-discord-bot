package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.bridge.model.StopReason;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Component
public class VoiceSessionHeartbeat {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceSessionHeartbeat.class);

    private final AudioPlayerPool pool;
    private final DiscordClient client;
    private final StringRedisTemplate redis;
    private final PlayerProperties properties;
    private final VoiceConnectionManager manager;
    private final Clock clock;
    private final Map<String, Since> nobodyListeningSince = new ConcurrentHashMap<>();
    private final Map<String, Since> idleSince = new ConcurrentHashMap<>();

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
        Set<String> active = pool.activeGuildIds();
        // a /player leave closes the player without this heartbeat seeing it, so its timers would linger
        nobodyListeningSince.keySet().retainAll(active);
        idleSince.keySet().retainAll(active);
        active.forEach(this::maintain);
    }

    private void maintain(String guildId) {
        try {
            client.guild(guildId).voice()
                    .ifPresentOrElse(
                            session -> maintainConnected(guildId, session),
                            () -> forget(guildId));
        } catch (RuntimeException ex) {
            LOG.warn("Voice-session heartbeat failed for guild {}", guildId, ex);
        }
    }

    private void maintainConnected(String guildId, VoiceSession session) {
        String channelId = session.getChannelId();
        redis.expire(
                PlayerRedisProtocol.Keys.assignment(guildId, channelId),
                properties.assignment().ttl());

        Optional<AudioPlayer> player = pool.get(guildId);
        if (!session.hasListeners()) {
            // the idle timer only counts with listeners present, so an empty room always leaves as NOBODY_LISTENING
            idleSince.remove(guildId);
            player.ifPresent(AudioPlayer::autoPause);
            if (lasted(nobodyListeningSince, guildId, channelId, properties.voiceSession().nobodyListeningTimeout())) {
                disconnectIfStill(nobodyListeningSince, guildId, channelId, s -> !s.hasListeners(), StopReason.NOBODY_LISTENING);
            }
            return;
        }
        nobodyListeningSince.remove(guildId);
        player.ifPresent(AudioPlayer::autoResume);

        if (!player.map(AudioPlayer::isIdle).orElse(false)) {
            idleSince.remove(guildId);
            return;
        }
        if (lasted(idleSince, guildId, channelId, properties.voiceSession().idleTimeout())) {
            disconnectIfStill(idleSince, guildId, channelId, _ -> pool.get(guildId).map(AudioPlayer::isIdle).orElse(false), StopReason.IDLE);
        }
    }

    /** Remembers when the condition started in this channel and tells whether it has lasted {@code timeout}. */
    private boolean lasted(Map<String, Since> since, String guildId, String channelId, Duration timeout) {
        Instant now = clock.instant();
        Since state = since.compute(guildId, (_, existing) -> {
            if (existing == null || !existing.channelId().equals(channelId)) {
                return new Since(channelId, now);
            }
            return existing;
        });
        return Duration.between(state.since(), now).compareTo(timeout) >= 0;
    }

    private void disconnectIfStill(Map<String, Since> since, String guildId, String channelId,
                                   Predicate<VoiceSession> useless, StopReason reason) {
        client.guild(guildId).voice().ifPresentOrElse(s -> {
            if (!s.getChannelId().equals(channelId) || !useless.test(s)) {
                since.remove(guildId);
            } else {
                try {
                    manager.disconnect(guildId, reason);
                    forget(guildId);
                } catch (RuntimeException ex) {
                    LOG.warn("Failed to disconnect guild {} ({})", guildId, reason, ex);
                }
            }
        }, () -> forget(guildId));
    }

    private void forget(String guildId) {
        nobodyListeningSince.remove(guildId);
        idleSince.remove(guildId);
    }

    private record Since(String channelId, Instant since) {
    }
}

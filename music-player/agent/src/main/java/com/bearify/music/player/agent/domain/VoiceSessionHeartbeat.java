package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.bridge.model.StopReason;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final String playerId;
    // the player and channel each guild was last seen connected with; a guild never seen connected may still be joining
    private final Map<String, Seen> connectedChannels = new ConcurrentHashMap<>();
    private final Map<String, Since> nobodyListeningSince = new ConcurrentHashMap<>();
    private final Map<String, Since> idleSince = new ConcurrentHashMap<>();

    VoiceSessionHeartbeat(AudioPlayerPool pool,
                          DiscordClient client,
                          StringRedisTemplate redis,
                          PlayerProperties properties,
                          VoiceConnectionManager manager,
                          Clock clock,
                          @Value("${player.id}") String playerId) {
        this.pool = pool;
        this.client = client;
        this.redis = redis;
        this.properties = properties;
        this.manager = manager;
        this.clock = clock;
        this.playerId = playerId;
    }

    @Scheduled(fixedDelayString = "${music-player.voice-session.heartbeat-interval:10s}")
    public void maintain() {
        Set<String> active = pool.activeGuildIds();
        // a /player leave closes the player without this heartbeat seeing it, so its timers would linger
        nobodyListeningSince.keySet().retainAll(active);
        idleSince.keySet().retainAll(active);
        connectedChannels.keySet().retainAll(active);
        active.forEach(this::maintain);
    }

    private void maintain(String guildId) {
        try {
            client.guild(guildId).voice()
                    .ifPresentOrElse(
                            session -> maintainConnected(guildId, session),
                            () -> Optional.ofNullable(connectedChannels.get(guildId))
                                    // a stop and quick re-join replaced the player: that new one isn't the one sent out
                                    .filter(seen -> pool.get(guildId).filter(current -> current == seen.player()).isPresent())
                                    .ifPresentOrElse(seen -> sentOut(guildId, seen), () -> forget(guildId)));
        } catch (RuntimeException ex) {
            LOG.warn("Voice-session heartbeat failed for guild {}", guildId, ex);
        }
    }

    private void maintainConnected(String guildId, VoiceSession session) {
        String channelId = session.getChannelId();
        Optional<AudioPlayer> player = pool.get(guildId);
        keepAssignment(guildId, channelId, player);

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

    /** Someone disconnected or kicked the bear: free its player and assignment, and tell the controller it stopped. */
    private void sentOut(String guildId, Seen seen) {
        manager.stopSentOut(guildId, seen.channelId(), seen.player());
        // only after it worked: a failed stop is retried on the next tick
        forget(guildId);
    }

    /** Refreshes this channel's assignment, moving it when a moderator dragged the bear and recreating it when it was lost. */
    private void keepAssignment(String guildId, String channelId, Optional<AudioPlayer> player) {
        String key = PlayerRedisProtocol.Keys.assignment(guildId, channelId);
        Seen previous = player.isPresent()
                ? connectedChannels.put(guildId, new Seen(player.get(), channelId))
                : connectedChannels.remove(guildId);
        if (previous != null && !previous.channelId().equals(channelId)) {
            deleteIfOurs(PlayerRedisProtocol.Keys.assignment(guildId, previous.channelId()));
        }
        if (Boolean.TRUE.equals(redis.expire(key, properties.assignment().ttl()))) {
            return;
        }
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, playerId, properties.assignment().ttl()))) {
            // two bears in one channel is a moderator's doing; keep playing rather than fight over the key
            LOG.warn("Channel {} in guild {} is assigned to another bear", channelId, guildId);
        }
    }

    // never removes another agent's assignment
    private void deleteIfOurs(String key) {
        if (playerId.equals(redis.opsForValue().get(key))) {
            redis.delete(key);
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
        connectedChannels.remove(guildId);
        nobodyListeningSince.remove(guildId);
        idleSince.remove(guildId);
    }

    private record Since(String channelId, Instant since) {
    }

    private record Seen(AudioPlayer player, String channelId) {
    }
}

package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceConnectionManager implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceConnectionManager.class);

    private final DiscordClient client;
    private final MusicPlayerEventDispatcher eventDispatcher;
    private final AudioPlayerPool pool;
    private final StringRedisTemplate redis;
    private final PlayerProperties properties;
    private final String playerId;
    private final Clock clock;
    private final ConcurrentHashMap<String, Object> guildLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> joiningGuilds = new ConcurrentHashMap<>();

    public VoiceConnectionManager(DiscordClient client,
                                  MusicPlayerEventDispatcher eventDispatcher,
                                  AudioPlayerPool pool,
                                  StringRedisTemplate redis,
                                  PlayerProperties properties,
                                  @Value("${player.id}") String playerId,
                                  Clock clock) {
        this.client = client;
        this.eventDispatcher = eventDispatcher;
        this.pool = pool;
        this.redis = redis;
        this.properties = properties;
        this.playerId = playerId;
        this.clock = clock;
    }

    public void claim(JoinRequest request) {
        Object lock = guildLocks.computeIfAbsent(request.guildId(), k -> new Object());
        synchronized (lock) {
            if (!Boolean.TRUE.equals(redis.hasKey(PlayerRedisProtocol.Keys.connectRequest(request.requestId())))) {
                return;
            }
            Instant joinStartedAt = joiningGuilds.get(request.guildId());
            if (joinStartedAt != null && clock.instant().isBefore(joinStartedAt.plus(properties.assignment().ttl()))) {
                return;
            }
            // a join older than the assignment TTL hung without calling back, and its key has expired
            joiningGuilds.remove(request.guildId());
            var guild = client.guild(request.guildId());
            guild.voice().ifPresentOrElse(
                    session -> handleInVoice(request, guild, session),
                    () -> handleIdle(request, guild));
        }
    }

    public void connect(MusicPlayerInteraction.Connect request) {
        var guild = client.guild(request.guildId());
        AudioPlayer player = pool.getOrCreate(request.guildId());
        guild.voice().ifPresentOrElse(session -> {
            if (session.getChannelId().equals(request.voiceChannelId())) {
                eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
            } else if (session.isLonely()) {
                join(guild, request.requestId(), request.guildId(), request.voiceChannelId(), player, Optional.empty(), _ -> {
                    eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
                });
            } else {
                eventDispatcher.dispatch(new MusicPlayerEvent.ConnectFailed(playerId, request.requestId(), request.guildId(), "already connected to a different channel"));
            }
        }, () -> join(guild, request.requestId(), request.guildId(), request.voiceChannelId(), player, Optional.empty(), _ -> {
            eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
        }));
    }

    public void disconnect(String guildId) {
        joiningGuilds.remove(guildId);
        client.guild(guildId).voice().ifPresent(session -> {
            redis.delete(PlayerRedisProtocol.Keys.assignment(guildId, session.getChannelId()));
            session.leave();
        });
        pool.get(guildId).ifPresent(AudioPlayer::close);
        eventDispatcher.dispatch(new MusicPlayerEvent.Stopped(playerId, UUID.randomUUID().toString(), guildId));
    }

    private void handleInVoice(JoinRequest request, Guild guild, VoiceSession session) {
        if (session.getChannelId().equals(request.voiceChannelId())) {
            // CASE A: already in the right channel — refresh key and dispatch Ready
            redis.opsForValue().set(
                    PlayerRedisProtocol.Keys.assignment(request.guildId(), request.voiceChannelId()),
                    playerId,
                    properties.assignment().ttl());
            eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
        } else if (session.isLonely()) {
            // CASE B: different channel, alone — attempt to claim and migrate
            String assignmentKey = PlayerRedisProtocol.Keys.assignment(request.guildId(), request.voiceChannelId());
            boolean claimed = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                    assignmentKey,
                    playerId,
                    properties.assignment().ttl()));
            if (!claimed) return;
            String oldChannelId = session.getChannelId();
            AudioPlayer player = pool.getOrCreate(request.guildId());
            Instant startedAt = clock.instant();
            joiningGuilds.put(request.guildId(), startedAt);
            join(guild, request.requestId(), request.guildId(), request.voiceChannelId(), player, Optional.of(assignmentKey), _ -> {
                joiningGuilds.remove(request.guildId(), startedAt);
                redis.delete(PlayerRedisProtocol.Keys.assignment(request.guildId(), oldChannelId));
                eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
            });
        }
        // CASE C: different channel, not alone — skip silently
    }

    private void handleIdle(JoinRequest request, Guild guild) {
        // Case D: not in any channel for this guild — attempt to claim
        String assignmentKey = PlayerRedisProtocol.Keys.assignment(request.guildId(), request.voiceChannelId());
        boolean claimed = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                assignmentKey,
                playerId,
                properties.assignment().ttl()));
        if (!claimed) return;
        AudioPlayer player = pool.getOrCreate(request.guildId());
        Instant startedAt = clock.instant();
        joiningGuilds.put(request.guildId(), startedAt);
        join(guild, request.requestId(), request.guildId(), request.voiceChannelId(), player, Optional.of(assignmentKey), _ -> {
            // a stale join that calls back late must not clear a newer join's entry
            joiningGuilds.remove(request.guildId(), startedAt);
            eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
        });
    }

    private void join(Guild guild, String requestId, String guildId, String voiceChannelId, AudioPlayer player,
                      Optional<String> claimedAssignmentKey, VoiceSessionListener onJoined) {
        try {
            guild.join(voiceChannelId, player, onJoined);
        } catch (RuntimeException e) {
            joiningGuilds.remove(guildId);
            claimedAssignmentKey.ifPresent(redis::delete);
            LOG.warn("Failed to join voice channel {} in guild {}", voiceChannelId, guildId, e);
            eventDispatcher.dispatch(new MusicPlayerEvent.ConnectFailed(playerId, requestId, guildId, "failed to join the voice channel"));
        }
    }

    @Override
    public void close() {
        pool.activeGuildIds().forEach(this::disconnect);
    }
}

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
import com.bearify.music.player.bridge.model.StopReason;
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
            if (isJoining(request.guildId())) {
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
        // under the guild lock, so a heartbeat can't stop this player between getOrCreate and join
        synchronized (guildLocks.computeIfAbsent(request.guildId(), k -> new Object())) {
            var guild = client.guild(request.guildId());
            AudioPlayer player = pool.getOrCreate(request.guildId());
            guild.voice().ifPresentOrElse(session -> {
                if (session.getChannelId().equals(request.voiceChannelId())) {
                    eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
                } else if (session.isLonely()) {
                    moveLonely(guild, request, session.getChannelId(), player);
                } else {
                    eventDispatcher.dispatch(new MusicPlayerEvent.ConnectFailed(playerId, request.requestId(), request.guildId(), "already connected to a different channel"));
                }
            }, () -> join(guild, request.requestId(), request.guildId(), request.voiceChannelId(), player, Optional.empty(), _ -> {
                eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
            }));
        }
    }

    // moves the assignment along with the bear, so the old channel's key doesn't keep pointing at it
    private void moveLonely(Guild guild, MusicPlayerInteraction.Connect request, String oldChannelId, AudioPlayer player) {
        String assignmentKey = PlayerRedisProtocol.Keys.assignment(request.guildId(), request.voiceChannelId());
        if (!claimOrOwn(assignmentKey)) {
            eventDispatcher.dispatch(new MusicPlayerEvent.ConnectFailed(playerId, request.requestId(), request.guildId(), "another bear is in that channel"));
            return;
        }
        join(guild, request.requestId(), request.guildId(), request.voiceChannelId(), player, Optional.of(assignmentKey), _ -> {
            deleteIfOurs(PlayerRedisProtocol.Keys.assignment(request.guildId(), oldChannelId));
            eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
        });
    }

    // the controller only sends Connect for a channel already assigned to this bear, so our own key counts as claimed
    private boolean claimOrOwn(String key) {
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, playerId, properties.assignment().ttl()))) {
            return true;
        }
        if (!playerId.equals(redis.opsForValue().get(key))) {
            return false;
        }
        redis.expire(key, properties.assignment().ttl());
        return true;
    }

    // never removes another agent's assignment
    private void deleteIfOurs(String key) {
        if (playerId.equals(redis.opsForValue().get(key))) {
            redis.delete(key);
        }
    }

    /**
     * Stops {@code player} after someone disconnected or kicked the bear. Does nothing when the guild is (re)joining,
     * is back in voice, or has a newer player by now: the heartbeat that asks may have seen an older state.
     */
    public void stopSentOut(String guildId, String channelId, AudioPlayer player) {
        synchronized (guildLocks.computeIfAbsent(guildId, k -> new Object())) {
            if (isJoining(guildId)
                    || client.guild(guildId).voice().isPresent()
                    || pool.get(guildId).filter(current -> current == player).isEmpty()) {
                return;
            }
            deleteIfOurs(PlayerRedisProtocol.Keys.assignment(guildId, channelId));
            disconnect(guildId, UUID.randomUUID().toString(), StopReason.DISCONNECTED);
        }
    }

    // a join older than the assignment TTL hung without calling back, so it no longer counts
    private boolean isJoining(String guildId) {
        Instant startedAt = joiningGuilds.get(guildId);
        return startedAt != null && clock.instant().isBefore(startedAt.plus(properties.assignment().ttl()));
    }

    /** Leaves on the bear's own initiative; does nothing when a stop request already left, so no second leave note follows it. */
    public void disconnect(String guildId, StopReason reason) {
        synchronized (guildLocks.computeIfAbsent(guildId, k -> new Object())) {
            if (client.guild(guildId).voice().isEmpty() && pool.get(guildId).isEmpty()) {
                return;
            }
            disconnect(guildId, UUID.randomUUID().toString(), reason);
        }
    }

    /** Leaves voice and answers {@code Stopped} under {@code requestId}, so a stop request gets its reply. */
    public void disconnect(String guildId, String requestId, StopReason reason) {
        synchronized (guildLocks.computeIfAbsent(guildId, k -> new Object())) {
            joiningGuilds.remove(guildId);
            client.guild(guildId).voice().ifPresent(session -> {
                redis.delete(PlayerRedisProtocol.Keys.assignment(guildId, session.getChannelId()));
                // once out of the channel, clearing would need Manage Channels
                session.setStatus("");
                session.leave();
            });
            pool.get(guildId).ifPresent(AudioPlayer::close);
            eventDispatcher.dispatch(new MusicPlayerEvent.Stopped(playerId, requestId, guildId, reason));
        }
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
            join(guild, request.requestId(), request.guildId(), request.voiceChannelId(), player, Optional.of(assignmentKey), _ -> {
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
        join(guild, request.requestId(), request.guildId(), request.voiceChannelId(), player, Optional.of(assignmentKey), _ -> {
            eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId(), request.guildId()));
        });
    }

    private void join(Guild guild, String requestId, String guildId, String voiceChannelId, AudioPlayer player,
                      Optional<String> claimedAssignmentKey, VoiceSessionListener onJoined) {
        Instant startedAt = clock.instant();
        // until the callback, an empty voice state means "still joining", not "kicked"
        joiningGuilds.put(guildId, startedAt);
        try {
            guild.join(voiceChannelId, player, channelId -> {
                // a stale join that calls back late must not clear a newer join's entry
                joiningGuilds.remove(guildId, startedAt);
                onJoined.onJoined(channelId);
            });
        } catch (RuntimeException e) {
            joiningGuilds.remove(guildId, startedAt);
            claimedAssignmentKey.ifPresent(redis::delete);
            LOG.warn("Failed to join voice channel {} in guild {}", voiceChannelId, guildId, e);
            eventDispatcher.dispatch(new MusicPlayerEvent.ConnectFailed(playerId, requestId, guildId, "failed to join the voice channel"));
        }
    }

    @Override
    public void close() {
        pool.activeGuildIds().forEach(guildId -> disconnect(guildId, StopReason.REQUESTED));
    }
}

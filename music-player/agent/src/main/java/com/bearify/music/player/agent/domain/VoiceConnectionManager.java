package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceConnectionManager implements AutoCloseable {

    private final DiscordClient client;
    private final MusicPlayerEventDispatcher eventDispatcher;
    private final AudioPlayerPool pool;
    private final StringRedisTemplate redis;
    private final PlayerProperties properties;
    private final String playerId;
    private final ConcurrentHashMap<String, Object> guildLocks = new ConcurrentHashMap<>();
    private final Set<String> joiningGuilds = ConcurrentHashMap.newKeySet();

    public VoiceConnectionManager(DiscordClient client,
                                  MusicPlayerEventDispatcher eventDispatcher,
                                  AudioPlayerPool pool,
                                  StringRedisTemplate redis,
                                  PlayerProperties properties,
                                  @Value("${player.id}") String playerId) {
        this.client = client;
        this.eventDispatcher = eventDispatcher;
        this.pool = pool;
        this.redis = redis;
        this.properties = properties;
        this.playerId = playerId;
    }

    public void claim(JoinRequest request) {
        Object lock = guildLocks.computeIfAbsent(request.guildId(), k -> new Object());
        synchronized (lock) {
            if (!Boolean.TRUE.equals(redis.hasKey(PlayerRedisProtocol.Keys.connectRequest(request.requestId())))) {
                return;
            }
            if (joiningGuilds.contains(request.guildId())) {
                return;
            }
            var guild = client.guild(request.guildId());
            guild.voice().ifPresentOrElse(
                    session -> handleInVoice(request, guild, session),
                    () -> handleIdle(request, guild));
        }
    }

    public void connect(ConnectionRequest request) {
        var guild = client.guild(request.guildId());
        AudioPlayer player = pool.getOrCreate(request.guildId());
        guild.voice().ifPresentOrElse(session -> {
            if (session.getChannelId().equals(request.voiceChannelId())) {
                eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId()));
            } else if (session.isLonely()) {
                guild.join(request.voiceChannelId(), player, _ -> {
                    eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId()));
                });
            } else {
                eventDispatcher.dispatch(new MusicPlayerEvent.ConnectFailed(playerId, request.requestId(), "already connected to a different channel"));
            }
        }, () -> guild.join(request.voiceChannelId(), player, _ -> {
            eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId()));
        }));
    }

    public void disconnect(String guildId) {
        joiningGuilds.remove(guildId);
        client.guild(guildId).voice().ifPresent(session -> {
            redis.delete(PlayerRedisProtocol.Keys.assignment(guildId, session.getChannelId()));
            session.leave();
        });
        pool.get(guildId).ifPresent(AudioPlayer::close);
    }

    private void handleInVoice(JoinRequest request, Guild guild, VoiceSession session) {
        if (session.getChannelId().equals(request.voiceChannelId())) {
            // CASE A: already in the right channel — refresh key and dispatch Ready
            redis.opsForValue().set(
                    PlayerRedisProtocol.Keys.assignment(request.guildId(), request.voiceChannelId()),
                    playerId,
                    properties.assignment().ttl());
            eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId()));
        } else if (session.isLonely()) {
            // CASE B: different channel, alone — attempt to claim and migrate
            boolean claimed = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                    PlayerRedisProtocol.Keys.assignment(request.guildId(), request.voiceChannelId()),
                    playerId,
                    properties.assignment().ttl()));
            if (!claimed) return;
            String oldChannelId = session.getChannelId();
            AudioPlayer player = pool.getOrCreate(request.guildId());
            joiningGuilds.add(request.guildId());
            guild.join(request.voiceChannelId(), player, _ -> {
                joiningGuilds.remove(request.guildId());
                redis.delete(PlayerRedisProtocol.Keys.assignment(request.guildId(), oldChannelId));
                eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId()));
            });
        }
        // CASE C: different channel, not alone — skip silently
    }

    private void handleIdle(JoinRequest request, Guild guild) {
        // Case D: not in any channel for this guild — attempt to claim
        boolean claimed = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                PlayerRedisProtocol.Keys.assignment(request.guildId(), request.voiceChannelId()),
                playerId,
                properties.assignment().ttl()));
        if (!claimed) return;
        AudioPlayer player = pool.getOrCreate(request.guildId());
        joiningGuilds.add(request.guildId());
        guild.join(request.voiceChannelId(), player, _ -> {
            joiningGuilds.remove(request.guildId());
            eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId()));
        });
    }

    @Override
    public void close() {
        pool.activeGuildIds().forEach(this::disconnect);
    }
}

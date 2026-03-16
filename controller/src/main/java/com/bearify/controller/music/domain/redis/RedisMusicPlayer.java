package com.bearify.controller.music.domain.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerPendingRequests;
import com.bearify.shared.events.MusicPlayerEvent;
import com.bearify.shared.events.MusicPlayerInteraction;
import com.bearify.shared.player.PlayerMessageCodec;
import com.bearify.shared.player.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CompletableFuture;

class RedisMusicPlayer implements MusicPlayer {

    private final String playerId;
    private final String guildId;
    private final String voiceChannelId;
    private final StringRedisTemplate redis;
    private final PlayerMessageCodec codec;
    private final MusicPlayerPendingRequests pendingRequests;

    RedisMusicPlayer(String playerId,
                     String guildId,
                     String voiceChannelId,
                     StringRedisTemplate redis,
                     PlayerMessageCodec codec,
                     MusicPlayerPendingRequests pendingRequests) {
        this.playerId = playerId;
        this.guildId = guildId;
        this.voiceChannelId = voiceChannelId;
        this.redis = redis;
        this.codec = codec;
        this.pendingRequests = pendingRequests;
    }

    @Override
    public CompletableFuture<MusicPlayerEvent> join() {
        MusicPlayerPendingRequests.Pending pending = pendingRequests.register();
        String json = codec.serialize(new MusicPlayerInteraction.Connect(playerId, pending.requestId(), voiceChannelId, guildId));
        redis.convertAndSend(PlayerRedisProtocol.Channels.commands(playerId), json);
        return pending.future();
    }
}

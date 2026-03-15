package com.bearify.controller.music.domain;

import com.bearify.controller.music.domain.redis.MusicPlayerAllocator;
import com.bearify.controller.music.domain.redis.MusicPlayerInteractionPublisher;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MusicPlayerPool {

    private final MusicPlayerAllocator allocator;
    private final MusicPlayerRequestRegistry requests;
    private final MusicPlayerInteractionPublisher interactionPublisher;

    public MusicPlayerPool(MusicPlayerAllocator allocator,
                           MusicPlayerRequestRegistry requests,
                           MusicPlayerInteractionPublisher interactionPublisher) {
        this.allocator = allocator;
        this.requests = requests;
        this.interactionPublisher = interactionPublisher;
    }

    public Optional<MusicPlayer> acquire(String guildId, String voiceChannelId) {
        return allocator
                .findAssignedTo(guildId, voiceChannelId)
                .map(playerId -> player(playerId, guildId, voiceChannelId))
                .or(() -> claim(guildId,voiceChannelId));
    }

    private Optional<MusicPlayer> claim(String guildId, String voiceChannelId) {
        return allocator.claim().flatMap(playerId -> {
            if(allocator.assign(guildId, voiceChannelId, playerId)) {
                return Optional.of(player(playerId, guildId, voiceChannelId));
            } else {
                allocator.release(playerId);
                return acquire(guildId, voiceChannelId);
            }
        });
    }

    private MusicPlayer player(String playerId, String guildId, String voiceChannelId) {
        return new MusicPlayer(playerId, guildId, voiceChannelId, requests, interactionPublisher);
    }
}

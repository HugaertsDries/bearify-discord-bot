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
        Optional<String> existingPlayerId = allocator.findAssignedTo(guildId, voiceChannelId);
        if (existingPlayerId.isPresent()) {
            return existingPlayerId.map(playerId -> player(playerId, guildId, voiceChannelId));
        }

        Optional<String> playerId = allocator.claim();
        if (playerId.isEmpty()) {
            return Optional.empty();
        }

        if (allocator.assign(guildId, voiceChannelId, playerId.orElseThrow())) {
            return playerId.map(value -> player(value, guildId, voiceChannelId));
        }

        allocator.release(playerId.orElseThrow());
        return allocator.findAssignedTo(guildId, voiceChannelId)
                .map(value -> player(value, guildId, voiceChannelId));
    }

    private MusicPlayer player(String playerId, String guildId, String voiceChannelId) {
        return new MusicPlayer(playerId, guildId, voiceChannelId, requests, interactionPublisher);
    }
}

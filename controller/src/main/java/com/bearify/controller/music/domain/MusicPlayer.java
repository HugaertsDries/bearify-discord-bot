package com.bearify.controller.music.domain;

import com.bearify.controller.music.domain.redis.MusicPlayerInteractionPublisher;
import com.bearify.shared.events.PlayerInteraction;

import java.util.UUID;

public class MusicPlayer {

    private final String playerId;
    private final String guildId;
    private final String voiceChannelId;
    private final MusicPlayerRequestRegistry requests;
    private final MusicPlayerInteractionPublisher interactionPublisher;

    public MusicPlayer(String playerId,
                       String guildId,
                       String voiceChannelId,
                       MusicPlayerRequestRegistry requests,
                       MusicPlayerInteractionPublisher interactionPublisher) {
        this.playerId = playerId;
        this.guildId = guildId;
        this.voiceChannelId = voiceChannelId;
        this.requests = requests;
        this.interactionPublisher = interactionPublisher;
    }

    public void join(MusicPlayerEventHandler eventHandler) {
        String requestId = UUID.randomUUID().toString();
        requests.register(requestId, eventHandler);
        interactionPublisher.connect(new PlayerInteraction.Connect(playerId, requestId, voiceChannelId, guildId));
    }
}

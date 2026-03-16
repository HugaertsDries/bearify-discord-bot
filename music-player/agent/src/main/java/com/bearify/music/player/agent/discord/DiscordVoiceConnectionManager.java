package com.bearify.music.player.agent.discord;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.music.player.agent.domain.ConnectionRequest;
import com.bearify.music.player.agent.domain.EventPublisher;
import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
class DiscordVoiceConnectionManager implements VoiceConnectionManager {

    private final DiscordClient client;
    private final EventPublisher eventPublisher;
    private final String playerId;
    private final Set<String> connectedGuilds = ConcurrentHashMap.newKeySet();

    DiscordVoiceConnectionManager(DiscordClient client,
                                  EventPublisher eventPublisher,
                                  @Value("${player.id}") String playerId) {
        this.client = client;
        this.eventPublisher = eventPublisher;
        this.playerId = playerId;
    }

    @Override
    public void connect(ConnectionRequest request) {
        var session = client.guild(request.guildId()).voice();

        var currentChannelId = session.getConnectedChannelId();
        if (currentChannelId.isPresent()) {
            if (currentChannelId.get().equals(request.voiceChannelId())) {
                connectedGuilds.add(request.guildId());
                eventPublisher.publish(new MusicPlayerEvent.Ready(playerId, request.requestId()));
            } else if (session.isAlone()) {
                session.join(request.voiceChannelId(), _ -> eventPublisher.publish(new MusicPlayerEvent.Ready(playerId, request.requestId())));
            } else {
                eventPublisher.publish(new MusicPlayerEvent.ConnectFailed(playerId, request.requestId(), "already connected to a different channel"));
            }
            return;
        }

        session.join(request.voiceChannelId(), _ -> eventPublisher.publish(new MusicPlayerEvent.Ready(playerId, request.requestId())));
        connectedGuilds.add(request.guildId());
    }

    @Override
    public void disconnect(String guildId) {
        if (connectedGuilds.remove(guildId)) {
            client.guild(guildId).voice().leave();
        }
    }
}

package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceConnectionManager {

    private final DiscordClient client;
    private final MusicPlayerEventDispatcher eventDispatcher;
    private final String playerId;
    private final Set<String> connectedGuilds = ConcurrentHashMap.newKeySet();

    public VoiceConnectionManager(DiscordClient client,
                                  MusicPlayerEventDispatcher eventDispatcher,
                                  @Value("${player.id}") String playerId) {
        this.client = client;
        this.eventDispatcher = eventDispatcher;
        this.playerId = playerId;
    }

    public void connect(ConnectionRequest request) {
        var session = client.guild(request.guildId()).voice();
        session.getConnectedChannelId().ifPresentOrElse(connectedChannelId -> {
            if (connectedChannelId.equals(request.voiceChannelId())) {
                connectedGuilds.add(request.guildId());
                eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId()));
            } else if (session.isAlone()) {
                session.join(request.voiceChannelId(), _ -> {
                    connectedGuilds.add(request.guildId());
                    eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId()));
                });
            } else {
                eventDispatcher.dispatch(new MusicPlayerEvent.ConnectFailed(playerId, request.requestId(), "already connected to a different channel"));
            }
        }, () -> session.join(request.voiceChannelId(), _ -> {
            connectedGuilds.add(request.guildId());
            eventDispatcher.dispatch(new MusicPlayerEvent.Ready(playerId, request.requestId()));
        }));
    }

    public void disconnect(String guildId) {
        if (connectedGuilds.remove(guildId)) {
            client.guild(guildId).voice().leave();
        }
    }
}

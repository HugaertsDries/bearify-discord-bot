package com.bearify.music.player.agent.port;

import com.bearify.music.player.agent.domain.ConnectionRequest;
import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MusicPlayerInteractionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MusicPlayerInteractionDispatcher.class);

    private final VoiceConnectionManager manager;

    MusicPlayerInteractionDispatcher(VoiceConnectionManager manager) {
        this.manager = manager;
    }

    public void handle(MusicPlayerInteraction interaction) {
        switch (interaction) {
            case MusicPlayerInteraction.Connect connect ->
                    manager.connect(new ConnectionRequest(connect.requestId(), connect.voiceChannelId(), connect.guildId()));
            case MusicPlayerInteraction.Stop stop ->
                    manager.disconnect(stop.guildId());
            default -> LOG.warn("Unhandled interaction type: {}", interaction.getClass().getSimpleName());
        };
    }
}

package com.bearify.music.player.agent.domain;

import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InteractionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(InteractionHandler.class);

    private final VoiceConnectionManager manager;

    public InteractionHandler(VoiceConnectionManager manager) {
        this.manager = manager;
    }

    public void handle(MusicPlayerInteraction interaction) {
        switch (interaction) {
            case MusicPlayerInteraction.Connect connect ->
                    manager.connect(new ConnectionRequest(connect.requestId(), connect.voiceChannelId(), connect.guildId()));
            case MusicPlayerInteraction.Stop stop ->
                    manager.disconnect(stop.guildId());
            default -> LOG.warn("Unhandled command type: {}", interaction.getClass().getSimpleName());
        };
    }
}

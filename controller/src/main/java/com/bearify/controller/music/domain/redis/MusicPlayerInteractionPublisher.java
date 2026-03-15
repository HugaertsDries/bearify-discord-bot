package com.bearify.controller.music.domain.redis;

import com.bearify.shared.events.PlayerInteraction;

public interface MusicPlayerInteractionPublisher {

    void connect(PlayerInteraction.Connect interaction);
}

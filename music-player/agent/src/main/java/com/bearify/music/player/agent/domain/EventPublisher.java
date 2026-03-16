package com.bearify.music.player.agent.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

public interface EventPublisher {
    void publish(MusicPlayerEvent event);
}

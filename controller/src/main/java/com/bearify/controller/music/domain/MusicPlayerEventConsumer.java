package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

public interface MusicPlayerEventConsumer {
    void accept(MusicPlayerEvent event);
}

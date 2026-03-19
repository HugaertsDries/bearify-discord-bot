package com.bearify.music.player.agent.port;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

public interface MusicPlayerEventDispatcher {
    void dispatch(MusicPlayerEvent event);
}

package com.bearify.controller.music.port;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

public interface MusicPlayerTrackAnnouncer {
    void announce(MusicPlayerEvent event);
}

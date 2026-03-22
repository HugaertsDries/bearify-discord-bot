package com.bearify.controller.music.port;

import com.bearify.controller.music.domain.MusicPlayerInteractions;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;

public class MusicPlayerEventRouter {

    private final MusicPlayerInteractions pendingInteractions;
    private final MusicPlayerTrackAnnouncer trackAnnouncer;

    public MusicPlayerEventRouter(MusicPlayerInteractions pendingInteractions,
                                  MusicPlayerTrackAnnouncer trackAnnouncer) {
        this.pendingInteractions = pendingInteractions;
        this.trackAnnouncer = trackAnnouncer;
    }

    public void route(MusicPlayerEvent event) {
        if (!pendingInteractions.complete(event.requestId(), event)) {
            trackAnnouncer.announce(event);
        }
    }
}

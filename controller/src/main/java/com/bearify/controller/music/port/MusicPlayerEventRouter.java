package com.bearify.controller.music.port;

import com.bearify.controller.music.domain.MusicPlayerQueue;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;

public class MusicPlayerEventRouter {

    private final MusicPlayerQueue pendingInteractions;
    private final MusicPlayerTrackAnnouncer trackAnnouncer;

    public MusicPlayerEventRouter(MusicPlayerQueue pendingInteractions,
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

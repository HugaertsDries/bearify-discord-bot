package com.bearify.controller.music.domain;

import com.bearify.shared.events.MusicPlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicPlayerEventRouter {

    private static final Logger LOG = LoggerFactory.getLogger(MusicPlayerEventRouter.class);

    private final MusicPlayerPendingRequests musicPlayerPendingRequests;

    public MusicPlayerEventRouter(MusicPlayerPendingRequests musicPlayerPendingRequests) {
        this.musicPlayerPendingRequests = musicPlayerPendingRequests;
    }

    public void route(MusicPlayerEvent event) {
        if (!musicPlayerPendingRequests.complete(event.requestId(), event)) {
            LOG.warn("No pending request for requestId '{}'", event.requestId());
        }
    }
}

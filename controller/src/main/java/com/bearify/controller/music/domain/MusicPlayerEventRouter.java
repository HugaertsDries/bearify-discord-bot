package com.bearify.controller.music.domain;

import com.bearify.shared.events.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MusicPlayerEventRouter {

    private static final Logger LOG = LoggerFactory.getLogger(MusicPlayerEventRouter.class);

    private final MusicPlayerRequestRegistry requests;

    public MusicPlayerEventRouter(MusicPlayerRequestRegistry requests) {
        this.requests = requests;
    }

    public void route(PlayerEvent event) {
        toMusicEvent(event)
                .ifPresentOrElse(e -> consume(event.requestId(), e), () -> LOG.debug("Ignoring unmapped player event type: {}", event.getClass().getSimpleName()));
    }

    private void consume(String requestId, MusicPlayerEvent event) {
        requests.consume(requestId)
                .ifPresentOrElse(event::dispatchTo, () -> LOG.warn("No pending music request for requestId '{}'", requestId));
    }

    private Optional<MusicPlayerEvent> toMusicEvent(PlayerEvent event) {
        if (event instanceof PlayerEvent.PlayerReady ready) {
            return Optional.of(new MusicPlayerEvent.Ready(ready.playerId()));
        }
        return Optional.empty();
    }
}

package com.bearify.controller.music.port;

import com.bearify.controller.music.domain.MusicPlayerInteractions;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerEventRouterTest {

    private static final MusicPlayerTrackAnnouncer NO_OP_ANNOUNCER = event -> {};

    // --- HAPPY PATH ---

    @Test
    void routesEventToMatchingPendingRequest() {
        MusicPlayerInteractions interactions = new MusicPlayerInteractions();
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(interactions, NO_OP_ANNOUNCER);
        MusicPlayerInteractions.Request request = interactions.queue();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", request.requestId());

        router.route(event);

        assertThat(request.future().isDone()).isTrue();
        assertThat(request.future().join()).isEqualTo(event);
    }

    // --- EDGE CASES ---

    @Test
    void consumesPendingRequestWhenEventIsRouted() {
        MusicPlayerInteractions interactions = new MusicPlayerInteractions();
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(interactions, NO_OP_ANNOUNCER);
        MusicPlayerInteractions.Request request = interactions.queue();

        router.route(new MusicPlayerEvent.Ready("player-1", request.requestId()));

        assertThat(interactions.complete(request.requestId(), new MusicPlayerEvent.Ready("player-1", request.requestId()))).isFalse();
    }

    @Test
    void routesUnmatchedEventToTrackAnnouncer() {
        MusicPlayerInteractions interactions = new MusicPlayerInteractions();
        MusicPlayerEvent[] announced = {null};
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(interactions, event -> announced[0] = event);

        MusicPlayerEvent event = new MusicPlayerEvent.QueueEmpty("player-1", "unknown-req", "guild-1");
        router.route(event);

        assertThat(announced[0]).isSameAs(event);
    }
}

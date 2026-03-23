package com.bearify.controller.music.port;

import com.bearify.controller.music.domain.MusicPlayerQueue;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerEventRouterTest {

    private static final MusicPlayerTrackAnnouncer NO_OP_ANNOUNCER = event -> {};

    // --- HAPPY PATH ---

    @Test
    void routesEventToMatchingPendingRequest() {
        MusicPlayerQueue interactions = new MusicPlayerQueue();
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(interactions, NO_OP_ANNOUNCER);
        MusicPlayerQueue.Ticket ticket = interactions.enqueue();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", ticket.requestId());

        router.route(event);

        assertThat(ticket.future().isDone()).isTrue();
        assertThat(ticket.future().join()).isEqualTo(event);
    }

    // --- EDGE CASES ---

    @Test
    void consumesPendingRequestWhenEventIsRouted() {
        MusicPlayerQueue interactions = new MusicPlayerQueue();
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(interactions, NO_OP_ANNOUNCER);
        MusicPlayerQueue.Ticket ticket = interactions.enqueue();

        router.route(new MusicPlayerEvent.Ready("player-1", ticket.requestId()));

        assertThat(interactions.complete(ticket.requestId(), new MusicPlayerEvent.Ready("player-1", ticket.requestId()))).isFalse();
    }

    @Test
    void routesUnmatchedEventToTrackAnnouncer() {
        MusicPlayerQueue interactions = new MusicPlayerQueue();
        MusicPlayerEvent[] announced = {null};
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(interactions, event -> announced[0] = event);

        MusicPlayerEvent event = new MusicPlayerEvent.QueueEmpty("player-1", "unknown-req", "guild-1");
        router.route(event);

        assertThat(announced[0]).isSameAs(event);
    }
}

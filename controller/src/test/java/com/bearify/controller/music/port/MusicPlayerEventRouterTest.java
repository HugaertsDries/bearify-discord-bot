package com.bearify.controller.music.port;

import com.bearify.controller.music.domain.MusicPlayerPendingRequests;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerEventRouterTest {

    // --- HAPPY PATH ---

    @Test
    void routesEventToMatchingPendingRequest() {
        MusicPlayerPendingRequests requests = new MusicPlayerPendingRequests();
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(requests);
        MusicPlayerPendingRequests.Pending pending = requests.register();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", pending.requestId());

        router.route(event);

        assertThat(pending.future().isDone()).isTrue();
        assertThat(pending.future().join()).isEqualTo(event);
    }

    // --- EDGE CASES ---

    @Test
    void consumesPendingRequestWhenEventIsRouted() {
        MusicPlayerPendingRequests requests = new MusicPlayerPendingRequests();
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(requests);
        MusicPlayerPendingRequests.Pending pending = requests.register();

        router.route(new MusicPlayerEvent.Ready("player-1", pending.requestId()));

        assertThat(requests.complete(pending.requestId(), new MusicPlayerEvent.Ready("player-1", pending.requestId()))).isFalse();
    }
}

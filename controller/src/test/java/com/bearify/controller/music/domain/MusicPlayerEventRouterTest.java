package com.bearify.controller.music.domain;

import com.bearify.shared.events.PlayerEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerEventRouterTest {

    // --- HAPPY PATH ---

    @Test
    void routesEventToMatchingPendingSummon() {
        MusicPlayerRequestRegistry requests = new MusicPlayerRequestRegistry();
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(requests);
        AtomicReference<MusicPlayerEvent> handled = new AtomicReference<>();
        PlayerEvent.PlayerReady event = new PlayerEvent.PlayerReady("player-1", "req-1");

        requests.register("req-1", new MusicPlayerEventHandler() {
            @Override
            public void onReady(MusicPlayerEvent.Ready event) {
                handled.set(event);
            }
        });

        router.route(event);

        assertThat(handled.get()).isEqualTo(new MusicPlayerEvent.Ready("player-1"));
        assertThat(requests.consume("req-1")).isEmpty();
    }

    // --- EDGE CASES ---

    @Test
    void consumesPendingRequestWhenEventIsRouted() {
        MusicPlayerRequestRegistry requests = new MusicPlayerRequestRegistry();
        MusicPlayerEventRouter router = new MusicPlayerEventRouter(requests);

        requests.register("req-1", new MusicPlayerEventHandler() {});

        router.route(new PlayerEvent.PlayerReady("player-1", "req-1"));

        assertThat(requests.consume("req-1")).isEmpty();
    }
}

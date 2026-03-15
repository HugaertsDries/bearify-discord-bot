package com.bearify.controller.music.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerRequestRegistryTest {

    // --- HAPPY PATH ---

    @Test
    void returnsRegisteredHandlerOnceForMatchingRequestId() {
        MusicPlayerRequestRegistry registry = new MusicPlayerRequestRegistry();
        MusicPlayerEventHandler handler = new MusicPlayerEventHandler() {};

        registry.register("req-1", handler);

        assertThat(registry.consume("req-1")).containsSame(handler);
    }

    // --- EDGE CASES ---

    @Test
    void removesHandlerAfterItIsConsumed() {
        MusicPlayerRequestRegistry registry = new MusicPlayerRequestRegistry();
        registry.register("req-1", new MusicPlayerEventHandler() {});

        registry.consume("req-1");

        assertThat(registry.consume("req-1")).isEmpty();
    }

    @Test
    void returnsEmptyWhenRequestIdWasNeverRegistered() {
        MusicPlayerRequestRegistry registry = new MusicPlayerRequestRegistry();

        assertThat(registry.consume("missing")).isEmpty();
    }
}

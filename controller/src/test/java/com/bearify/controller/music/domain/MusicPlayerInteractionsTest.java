package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerInteractionsTest {

    // --- HAPPY PATH ---

    @Test
    void queueReturnsPendingWithNonBlankRequestIdAndNonNullFuture() {
        MusicPlayerInteractions interactions = new MusicPlayerInteractions();

        MusicPlayerInteractions.Request request = interactions.queue();

        assertThat(request.requestId()).isNotBlank();
        assertThat(request.future()).isNotNull();
    }

    @Test
    void completesFutureWithEventForMatchingRequestId() {
        MusicPlayerInteractions interactions = new MusicPlayerInteractions();
        MusicPlayerInteractions.Request request = interactions.queue();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", request.requestId());

        interactions.complete(request.requestId(), event);

        assertThat(request.future().isDone()).isTrue();
        assertThat(request.future().join()).isEqualTo(event);
    }

    // --- EDGE CASES ---

    @Test
    void returnsFalseWhenCompletingAlreadyConsumedRequestId() {
        MusicPlayerInteractions interactions = new MusicPlayerInteractions();
        MusicPlayerInteractions.Request request = interactions.queue();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", request.requestId());

        interactions.complete(request.requestId(), event);

        assertThat(interactions.complete(request.requestId(), event)).isFalse();
    }

    @Test
    void returnsFalseWhenCompletingUnknownRequestId() {
        MusicPlayerInteractions interactions = new MusicPlayerInteractions();

        assertThat(interactions.complete("unknown", new MusicPlayerEvent.Ready("player-1", "unknown"))).isFalse();
    }
}

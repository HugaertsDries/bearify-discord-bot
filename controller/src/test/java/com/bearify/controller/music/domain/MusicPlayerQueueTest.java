package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerQueueTest {

    // --- HAPPY PATH ---

    @Test
    void enqueueReturnsPendingWithNonBlankRequestIdAndNonNullFuture() {
        MusicPlayerQueue interactions = new MusicPlayerQueue();

        MusicPlayerQueue.Ticket ticket = interactions.enqueue();

        assertThat(ticket.requestId()).isNotBlank();
        assertThat(ticket.future()).isNotNull();
    }

    @Test
    void completesFutureWithEventForMatchingRequestId() {
        MusicPlayerQueue interactions = new MusicPlayerQueue();
        MusicPlayerQueue.Ticket ticket = interactions.enqueue();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", ticket.requestId());

        interactions.complete(ticket.requestId(), event);

        assertThat(ticket.future().isDone()).isTrue();
        assertThat(ticket.future().join()).isEqualTo(event);
    }

    // --- EDGE CASES ---

    @Test
    void returnsFalseWhenCompletingAlreadyConsumedRequestId() {
        MusicPlayerQueue interactions = new MusicPlayerQueue();
        MusicPlayerQueue.Ticket ticket = interactions.enqueue();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", ticket.requestId());

        interactions.complete(ticket.requestId(), event);

        assertThat(interactions.complete(ticket.requestId(), event)).isFalse();
    }

    @Test
    void returnsFalseWhenCompletingUnknownRequestId() {
        MusicPlayerQueue interactions = new MusicPlayerQueue();

        assertThat(interactions.complete("unknown", new MusicPlayerEvent.Ready("player-1", "unknown"))).isFalse();
    }
}

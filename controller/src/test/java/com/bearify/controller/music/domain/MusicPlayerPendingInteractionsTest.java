package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerPendingInteractionsTest {

    @Test
    void registerReturnsPendingInteractionWithRequestIdAndFuture() {
        MusicPlayerPendingInteractions interactions = new MusicPlayerPendingInteractions();

        MusicPlayerPendingInteractions.PendingInteraction pending = interactions.register();

        assertThat(pending.requestId()).isNotBlank();
        assertThat(pending.future()).isNotNull();
    }

    @Test
    void acceptCompletesFutureForMatchingRequestId() {
        MusicPlayerPendingInteractions interactions = new MusicPlayerPendingInteractions();
        MusicPlayerPendingInteractions.PendingInteraction pending = interactions.register();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", pending.requestId());

        interactions.accept(event);

        assertThat(pending.future().isDone()).isTrue();
        assertThat(pending.future().join()).isEqualTo(event);
    }

    @Test
    void acceptIgnoresUnknownRequestId() {
        MusicPlayerPendingInteractions interactions = new MusicPlayerPendingInteractions();
        MusicPlayerPendingInteractions.PendingInteraction pending = interactions.register();

        interactions.accept(new MusicPlayerEvent.Ready("player-1", "unknown"));

        assertThat(pending.future().isDone()).isFalse();
    }
}

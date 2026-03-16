package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerPendingRequestsTest {

    // --- HAPPY PATH ---

    @Test
    void registerReturnsPendingWithNonBlankRequestIdAndNonNullFuture() {
        MusicPlayerPendingRequests requests = new MusicPlayerPendingRequests();

        MusicPlayerPendingRequests.Pending pending = requests.register();

        assertThat(pending.requestId()).isNotBlank();
        assertThat(pending.future()).isNotNull();
    }

    @Test
    void completesFutureWithEventForMatchingRequestId() {
        MusicPlayerPendingRequests requests = new MusicPlayerPendingRequests();
        MusicPlayerPendingRequests.Pending pending = requests.register();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", pending.requestId());

        requests.complete(pending.requestId(), event);

        assertThat(pending.future().isDone()).isTrue();
        assertThat(pending.future().join()).isEqualTo(event);
    }

    // --- EDGE CASES ---

    @Test
    void returnsFalseWhenCompletingAlreadyConsumedRequestId() {
        MusicPlayerPendingRequests requests = new MusicPlayerPendingRequests();
        MusicPlayerPendingRequests.Pending pending = requests.register();
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", pending.requestId());

        requests.complete(pending.requestId(), event);

        assertThat(requests.complete(pending.requestId(), event)).isFalse();
    }

    @Test
    void returnsFalseWhenCompletingUnknownRequestId() {
        MusicPlayerPendingRequests requests = new MusicPlayerPendingRequests();

        assertThat(requests.complete("unknown", new MusicPlayerEvent.Ready("player-1", "unknown"))).isFalse();
    }
}
